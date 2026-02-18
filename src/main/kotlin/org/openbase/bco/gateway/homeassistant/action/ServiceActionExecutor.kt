package org.openbase.bco.gateway.homeassistant.action

import com.google.gson.JsonObject
import org.openbase.bco.dal.control.layer.unit.AbstractUnitController
import org.openbase.bco.dal.lib.action.ActionDescriptionProcessor
import org.openbase.bco.gateway.homeassistant.manager.cache.HassIdToUnitControllerCache
import org.openbase.bco.gateway.homeassistant.manager.dto.HassStateDto
import org.openbase.bco.gateway.homeassistant.manager.service.*
import org.openbase.bco.gateway.homeassistant.manager.transformer.toServiceState
import org.openbase.bco.gateway.homeassistant.manager.transformer.toServiceType
import org.openbase.bco.gateway.homeassistant.util.*
import org.openbase.jul.exception.CouldNotPerformException
import org.openbase.jul.exception.InvalidStateException
import org.openbase.jul.pattern.Observer
import org.openbase.jul.schedule.Timeout
import org.openbase.type.domotic.action.ActionInitiatorType.ActionInitiator
import org.openbase.type.domotic.action.ActionInitiatorType.ActionInitiator.InitiatorType
import org.openbase.type.domotic.action.ActionPriorityType.ActionPriority
import org.openbase.type.domotic.service.ServiceTemplateType.ServiceTemplate.ServiceType
import org.openbase.type.vision.ColorType.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

class ServiceActionExecutor(
    private val hassIdToUnitControllerCache: HassIdToUnitControllerCache,
) : Observer<Any, JsonObject> {

    override fun update(
        source: Any,
        message: JsonObject,
    ) {
        LOGGER.trace("Received update from source[{}] with payload[{}].", source, message)
        applyStateUpdate(message[KEY_PAYLOAD] parse HassStateDto::class)
    }

    /**
     * Retrieve a unit controller by an item name and apply an update according to the serialized state.
     * If the update is the result of a system sync it will be scheduled shortly so that the state is only applied.
     * Else the update is handled as a human action.
     *
     * @param hassState an object serializing the state and entity to be set.
     * @param systemSync flag determining if the state update is the result of a system sync.
     * @throws CouldNotPerformException
     */
    @JvmOverloads
    @Throws(CouldNotPerformException::class)
    fun applyStateUpdate(
        hassState: HassStateDto,
        systemSync: Boolean = false,
    ) = hassState.toServiceType().let { serviceType ->
        if (serviceType == ServiceType.UNKNOWN) {
            LOGGER.debug("Skip state update because HassServiceType [{}] is not supported.", hassState.type)
            return null
        }

        try {
            hassState.toServiceState()?.let { serviceState ->
                hassIdToUnitControllerCache.getEntry(hassState.entityId)?.let { unitController ->
                    // update the responsible action to show that it was triggered by hass and add other parameters
                    // note that the responsible action is overwritten if it matches a requested state in the unit controller and thus was triggered by a different user through BCO
                    val serviceStateBuilder = if (systemSync) {

                        ActionDescriptionProcessor.generateAndSetResponsibleAction(
                            serviceState,
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
                            serviceState,
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

                    LOGGER.info("Apply ItemUpdate[${hassState.entityId}=${hassState.state}].")
                    unitController.applyDataUpdate(serviceStateBuilder, serviceType)
                }
            }
        } catch (ex: InvalidStateException) {
            LOGGER.debug("Ignore state update [{}] for service[{}]", hassState.state, serviceType, ex)
        } catch (ex: CouldNotPerformException) {
            LOGGER.warn(
                "Ignore state update [${hassState.state}] for service[$serviceType]",
                ex,
            )
        }
    }

    companion object {
        const val KEY_PAYLOAD: String = "payload"

        private val LOGGER: Logger = LoggerFactory.getLogger(ServiceActionExecutor::class.java)
    }
}
