package org.openbase.bco.device.hass.action

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.protobuf.Message
import org.openbase.bco.dal.lib.action.ActionDescriptionProcessor
import org.openbase.bco.device.hass.manager.cache.HassIdToUnitControllerCache
import org.openbase.bco.device.hass.manager.dto.HassStateDto
import org.openbase.bco.device.hass.type.HassDomainType
import org.openbase.bco.device.hass.util.isNotNull
import org.openbase.bco.device.hass.util.toBCOBrightness
import org.openbase.bco.device.hass.util.toBCOHue
import org.openbase.bco.device.hass.util.toBCOSaturation
import org.openbase.jul.exception.CouldNotPerformException
import org.openbase.jul.exception.InvalidStateException
import org.openbase.jul.pattern.Observer
import org.openbase.jul.schedule.Timeout
import org.openbase.type.domotic.action.ActionInitiatorType.ActionInitiator
import org.openbase.type.domotic.action.ActionInitiatorType.ActionInitiator.InitiatorType
import org.openbase.type.domotic.action.ActionPriorityType.ActionPriority
import org.openbase.type.domotic.service.ServiceTemplateType.ServiceTemplate.ServiceType
import org.openbase.type.domotic.state.ColorStateType.ColorState
import org.openbase.type.domotic.state.MotionStateType.MotionState
import org.openbase.type.domotic.state.PowerStateType.PowerState
import org.openbase.type.domotic.state.PresenceStateType.PresenceState
import org.openbase.type.vision.ColorType.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

class ServiceActionExecutor(
    private val hassIdToUnitControllerCache: HassIdToUnitControllerCache,
) : Observer<Any?, JsonObject> {
    override fun update(
        source: Any?,
        payload: JsonObject,
    ) {
        LOGGER.trace("Received update from source[{}] with payload[{}].", source, payload)

        // extract payload
        val hassState = Gson().fromJson(payload[PAYLOAD_KEY], HassStateDto::class.java)

        applyStateUpdate(hassState)
    }

    private fun HassStateDto.toServiceType() : ServiceType =
        // TODO: implement correct mapping
        when (type) {
            HassDomainType.LIGHT -> if (hue.isNotNull() && saturation.isNotNull() && brightness.isNotNull()) {
                ServiceType.COLOR_STATE_SERVICE
            } else {
                ServiceType.POWER_STATE_SERVICE
            }

            HassDomainType.BINARY_SENSOR -> ServiceType.MOTION_STATE_SERVICE

            else -> ServiceType.UNKNOWN
        }

    private fun HassStateDto.toServiceState() : Message.Builder? = this.let { hassState ->
        when (hassState.type) {
            HassDomainType.LIGHT -> if (hassState.hue.isNotNull() && hassState.saturation.isNotNull() && hassState.brightness.isNotNull()) {
                ColorState.newBuilder().apply {
                    setColor(
                        colorBuilder.apply {
                            setHsbColor(
                                hsbColorBuilder.apply {
                                    hue = hassState.hue!!.toBCOHue()
                                    saturation = hassState.saturation!!.toBCOSaturation()
                                    brightness = hassState.brightness!!.toBCOBrightness()
                                }
                            )
                        }
                    )
                }
            } else {
                PowerState.newBuilder().apply {
                    setValue(when (hassState.state) {
                        STATE_ON -> PowerState.State.ON
                        STATE_OFF -> PowerState.State.OFF
                        else -> PowerState.State.UNKNOWN
                    })
                }
            }

            HassDomainType.BINARY_SENSOR -> MotionState.newBuilder().apply {
                if (hassState.attributes["device_class"] == "motion") {
                    setValue(when (hassState.state) {
                        STATE_ON -> MotionState.State.MOTION
                        STATE_OFF -> MotionState.State.NO_MOTION
                        else -> MotionState.State.UNKNOWN
                    })
                } else {
                    null
                }
            }

            else -> null
        }
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
        const val STATE_ON: String = "on"
        const val STATE_OFF: String = "off"
        const val PAYLOAD_KEY: String = "payload"

        private val LOGGER: Logger = LoggerFactory.getLogger(ServiceActionExecutor::class.java)
    }
}
