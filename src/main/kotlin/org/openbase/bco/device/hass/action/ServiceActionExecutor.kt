package org.openbase.bco.device.hass.action

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.openbase.bco.dal.lib.action.ActionDescriptionProcessor
import org.openbase.bco.dal.lib.layer.unit.UnitController
import org.openbase.bco.dal.lib.layer.unit.UnitControllerRegistry
import org.openbase.bco.dal.lib.state.States
import org.openbase.bco.device.hass.communication.HassConnection
import org.openbase.bco.device.hass.manager.cache.HassIdToUnitConfigCache
import org.openbase.bco.device.hass.type.HassDomainType
import org.openbase.bco.device.hass.type.toHassDomainType
import org.openbase.bco.device.hass.util.isNull
import org.openbase.jul.exception.CouldNotPerformException
import org.openbase.jul.exception.InvalidStateException
import org.openbase.jul.exception.printer.ExceptionPrinter
import org.openbase.jul.exception.printer.LogLevel
import org.openbase.jul.pattern.Observer
import org.openbase.jul.schedule.Timeout
import org.openbase.type.domotic.action.ActionInitiatorType.ActionInitiator
import org.openbase.type.domotic.action.ActionInitiatorType.ActionInitiator.InitiatorType
import org.openbase.type.domotic.action.ActionPriorityType.ActionPriority
import org.openbase.type.domotic.service.ServiceTemplateType.ServiceTemplate.ServiceType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

class ServiceActionExecutor(
    private val unitControllerRegistry: UnitControllerRegistry<UnitController<*, *>>,
    private val hassIdToUnitConfigCache: HassIdToUnitConfigCache,
) : Observer<Any?, JsonObject> {
    override fun update(
        source: Any?,
        payload: JsonObject,
    ) {
        LOGGER.trace("Received update from source[{}] with payload[{}].", source, payload)

        // extract item name from topic
        val topic = payload[HassConnection.TOPIC_KEY].asString

        // topic structure: hass/items/{entityId}/command
        val entityId: String =
            topic.split(HassConnection.TOPIC_SEPARATOR.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[2]

        // extract payload
        val payloadObject = JsonParser.parseString(payload[PAYLOAD_KEY].asString).asJsonObject
        val state = payloadObject[PAYLOAD_STATE_KEY].asString
        val type = payloadObject[PAYLOAD_STATE_TYPE_KEY].asString.toHassDomainType()

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
     * @param hassDomainType  defines the type class.
     * @param state      a string serializing the state to be set.
     * @param systemSync flag determining if the state update is the result of a system sync.
     * @throws CouldNotPerformException
     */
    @JvmOverloads
    @Throws(CouldNotPerformException::class)
    fun applyStateUpdate(
        entityId: String,
        hassDomainType: HassDomainType,
        state: String,
        systemSync: Boolean = false,
    ) {
        // TODO: implement correct mapping
        val serviceType =
            when (hassDomainType) {
                HassDomainType.LIGHT -> ServiceType.POWER_STATE_SERVICE
                else -> ServiceType.UNKNOWN
            }

        var serviceStateBuilder =
            when (hassDomainType) {
                HassDomainType.LIGHT -> {
                    when (state) {
                        "on" -> States.Power.ON
                        else -> States.Power.OFF
                    }.toBuilder()
                }
                else -> null
            }

        try {
            // load controller
            val unitController =
                hassIdToUnitConfigCache.getEntry(entityId)?.let {
                    unitControllerRegistry.get(it.id)
                }

            // filter all events that are not handled by this instance.
            if (unitController.isNull() || serviceStateBuilder.isNull()) {
                return
            }

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
            unitController!!.applyDataUpdate(serviceStateBuilder, serviceType)
        } catch (ex: InvalidStateException) {
            LOGGER.debug("Ignore state update [{}] for service[{}]", state, serviceType, ex)
        } catch (ex: CouldNotPerformException) {
            LOGGER.warn(
                "Ignore state update [$state] for service[$serviceType]",
                ex,
            )
        }
    }

    companion object {
        const val PAYLOAD_KEY: String = "payload"
        const val PAYLOAD_STATE_KEY: String = "value"
        const val PAYLOAD_STATE_TYPE_KEY: String = "type"

        private val LOGGER: Logger = LoggerFactory.getLogger(ServiceActionExecutor::class.java)
    }
}
