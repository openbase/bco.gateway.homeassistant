package org.openbase.bco.device.hass.action

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.protobuf.Message
import org.example.org.openbase.bco.device.hass.manager.dto.ServiceAction
import org.openbase.bco.dal.lib.action.ActionDescriptionProcessor
import org.openbase.bco.dal.lib.layer.unit.UnitController
import org.openbase.bco.dal.lib.layer.unit.UnitControllerRegistry
import org.openbase.bco.device.hass.communication.HassConnection
import org.openbase.bco.device.hass.manager.service.ServiceTypeServiceActionMapping
import org.openbase.bco.device.hass.manager.transformer.ServiceStateServiceActionTransformerPool
import org.openbase.bco.registry.remote.Registries
import org.openbase.jul.exception.CouldNotPerformException
import org.openbase.jul.exception.InvalidStateException
import org.openbase.jul.exception.MultiException
import org.openbase.jul.exception.MultiException.ExceptionStack
import org.openbase.jul.exception.NotAvailableException
import org.openbase.jul.exception.printer.ExceptionPrinter
import org.openbase.jul.exception.printer.LogLevel
import org.openbase.jul.extension.type.processing.TimestampProcessor.updateTimestamp
import org.openbase.jul.pattern.Observer
import org.openbase.jul.schedule.Timeout
import org.openbase.type.domotic.action.ActionInitiatorType.ActionInitiator
import org.openbase.type.domotic.action.ActionInitiatorType.ActionInitiator.InitiatorType
import org.openbase.type.domotic.action.ActionPriorityType.ActionPriority
import org.openbase.type.domotic.service.ServiceTemplateType.ServiceTemplate
import org.openbase.type.domotic.service.ServiceTemplateType.ServiceTemplate.ServiceType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.TimeUnit

class ServiceActionExecutor(
    private val unitControllerRegistry: UnitControllerRegistry<UnitController<*, *>>,
) : Observer<Any?, JsonObject> {
    private val jsonParser: JsonParser = JsonParser()

    override fun update(
        source: Any?,
        payload: JsonObject,
    ) {
        // System.out.println("payload: " + payload.toString());

        // extract item name from topic

        val topic = payload[HassConnection.TOPIC_KEY].asString

        // topic structure: hass/items/{entityId}/command
        val entityId: String =
            topic.split(HassConnection.TOPIC_SEPARATOR.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[2]

        // extract payload
        val payloadObject = jsonParser.parse(payload[PAYLOAD_KEY].asString).asJsonObject
        val state = payloadObject[PAYLOAD_STATE_KEY].asString
        val type = payloadObject[PAYLOAD_STATE_TYPE_KEY].asString

        try {
            applyStateUpdate(entityId, type, state)
        } catch (ex: CouldNotPerformException) {
            ExceptionPrinter.printHistory(
                "Could not apply state update[$state] for item[$entityId]",
                ex,
                LOGGER,
                LogLevel.WARN,
            )
        }
    }

    /**
     * Retrieve a unit controller by an item name and apply an update according to the serialized state.
     * If the update is the result of a system sync it will be scheduled shortly so that the state is only applied.
     * Else the update is handled as a human action.
     *
     * @param entityId   the item identifying the unit who's the state should be updated.
     * @param stateType  defines the type class.
     * @param state      a string serializing the state to be set.
     * @param systemSync flag determining if the state update is the result of a system sync.
     * @throws CouldNotPerformException
     */
    @JvmOverloads
    @Throws(CouldNotPerformException::class)
    fun applyStateUpdate(
        entityId: String?,
        stateType: String?,
        state: String?,
        systemSync: Boolean = false,
    ) {
        // filter empty events

        if (entityId.isNullOrEmpty()) {
            throw NotAvailableException("entityId")
        }

        if (stateType.isNullOrEmpty()) {
            throw NotAvailableException("stateType")
        }

        if (state == null || state.equals(EMPTY_COMMAND_STRING, ignoreCase = true)) {
            return
        }

        // todo: implement correct mapping
        val unitAlias = entityId
        val serviceType =
            when (stateType) {
                "light" -> ServiceType.COLOR_STATE_SERVICE
                else -> ServiceType.UNKNOWN
            }

        try {
            // load controller
            val unitId = Registries.getUnitRegistry().getUnitConfigByAlias(unitAlias).id

            // filter all events that are not handled by this instance.
            if (!unitControllerRegistry.contains(unitId)) {
                return
            }

            val unitController = unitControllerRegistry[unitId]

            var serviceStateBuilder = getServiceData(stateType, state, serviceType).toBuilder()

            // update the responsible action to show that it was triggered by hass and add other parameters
            // note that the responsible action is overwritten if it matches a requested state in the unit controller and thus was triggered by a different user through BCO
            serviceStateBuilder =
                if (systemSync) {
                    ActionDescriptionProcessor.generateAndSetResponsibleAction(
                        serviceStateBuilder,
                        serviceType,
                        unitController,
                        Timeout.INFINITY_TIMEOUT,
                        TimeUnit.MINUTES,
                        false,
                        false,
                        false,
                        ActionPriority.Priority.NO,
                        ActionInitiator.newBuilder().setInitiatorType(InitiatorType.SYSTEM).build(),
                    )
                } else {
                    ActionDescriptionProcessor.generateAndSetResponsibleAction(
                        serviceStateBuilder,
                        serviceType,
                        unitController,
                        30,
                        TimeUnit.MINUTES,
                        false,
                        false,
                        true,
                        ActionPriority.Priority.HIGH,
                        ActionInitiator.newBuilder().setInitiatorType(InitiatorType.HUMAN).build(),
                    )
                }

            LOGGER.info("Apply ItemUpdate[$entityId=$state].")
            unitController.applyDataUpdate(serviceStateBuilder, serviceType)
        } catch (ex: InvalidStateException) {
            LOGGER.debug(
                ("Ignore state update [" + state + "] for service[" + serviceType).toString() + "]",
                ex,
            )
        } catch (ex: CouldNotPerformException) {
            LOGGER.warn(
                ("Ignore state update [" + state + "] for service[" + serviceType).toString() + "]",
                ex,
            )
        }
    }

    companion object {
        const val PAYLOAD_KEY: String = "payload"
        const val PAYLOAD_STATE_KEY: String = "value"
        const val PAYLOAD_STATE_TYPE_KEY: String = "type"

        private val LOGGER: Logger = LoggerFactory.getLogger(ServiceActionExecutor::class.java)

        private const val EMPTY_COMMAND_STRING = "null"

        @Throws(CouldNotPerformException::class)
        fun getServiceData(
            stateType: String,
            commandString: String,
            serviceType: ServiceTemplate.ServiceType,
        ): Message {
            try {
                var command: ServiceAction? = null
                var exceptionStack: ExceptionStack? = null

                val commandClass: Class<out ServiceAction?> = ServiceTypeServiceActionMapping.lookupServiceActionClass(stateType)
                try {
                    command =
                        commandClass
                            .getMethod("valueOf", commandString.javaClass)
                            .invoke(null, commandString) as ServiceAction
                } catch (ex: IllegalAccessException) {
                    exceptionStack =
                        MultiException.push(
                            ServiceActionExecutor::class.java,
                            InvalidStateException(
                                "ServiceAction class[" + commandClass.simpleName + "] does not posses a valueOf(String) method",
                                ex,
                            ),
                            exceptionStack,
                        )
                } catch (ex: NoSuchMethodException) {
                    exceptionStack =
                        MultiException.push(
                            ServiceActionExecutor::class.java,
                            InvalidStateException(
                                "ServiceAction class[" + commandClass.simpleName + "] does not posses a valueOf(String) method",
                                ex,
                            ),
                            exceptionStack,
                        )
                } catch (ex: IllegalArgumentException) {
                    // continue with the next command class, exception will be thrown if none is found
                    exceptionStack = MultiException.push(ServiceActionExecutor::class.java, ex, exceptionStack)
                } catch (ex: InvocationTargetException) {
                    // ignore because the value of method threw an exception, this can happen if e.g. 0 is returned for
                    // a roller shutter as the opening ratio and the stopMoveType is tested

                    exceptionStack = MultiException.push(ServiceActionExecutor::class.java, ex, exceptionStack)

                    // apply workaround for temperature values
                    // todo: implement valueOf() method in all transformer and individually parse the string and setup the physical unit as well
                    try {
                        command =
                            commandClass.getMethod("valueOf", commandString.javaClass).invoke(
                                null,
                                commandString
                                    .replace(" °C", "")
                                    .replace(" %", ""),
                            ) as ServiceAction
                    } catch (exx: Exception) {
                        exceptionStack = MultiException.push(ServiceActionExecutor::class.java, exx, exceptionStack)
                    }
                }

                if (command == null) {
                    if (exceptionStack == null) {
                        exceptionStack =
                            MultiException.push(
                                ServiceActionExecutor::class.java,
                                @Suppress("ktlint:standard:max-line-length")
                                InvalidStateException(
                                    "ServiceAction class not available! Please configure the eclipse smart home command class within the meta config of service template of type " +
                                        serviceType.name,
                                ),
                                exceptionStack,
                            )
                    }

                    MultiException.checkAndThrow(
                        { "Could not transform [" + commandString + "] into a state for service type[" + serviceType.name + "]" },
                        exceptionStack,
                    )
                }

                val serviceData: Message =
                    ServiceStateServiceActionTransformerPool.instance
                        .getTransformer(serviceType, command!!.javaClass)
                        .transform(command)
                return updateTimestamp(System.currentTimeMillis(), serviceData, TimeUnit.MILLISECONDS)
            } catch (ex: NotAvailableException) {
                throw CouldNotPerformException(
                    "Could not transform [" + commandString + "] of class [" + commandString.javaClass.simpleName +
                        "] into a state for service type[" +
                        serviceType.name +
                        "]",
                    ex,
                )
            }
        }
    }
}
