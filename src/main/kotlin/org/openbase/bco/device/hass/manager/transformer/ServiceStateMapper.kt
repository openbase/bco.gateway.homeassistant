package org.openbase.bco.device.hass.manager.transformer

import com.google.protobuf.Message
import org.openbase.bco.device.hass.manager.dto.HassStateDto
import org.openbase.bco.device.hass.manager.service.*
import org.openbase.type.domotic.service.ServiceTemplateType.ServiceTemplate.ServiceType.*

fun HassStateDto.toServiceState(): Message.Builder? = this
    .toServiceType()
    .let { serviceType ->
        when (serviceType) {
            POWER_STATE_SERVICE -> toPowerState()
            BRIGHTNESS_STATE_SERVICE -> toBrightnessState()
            COLOR_STATE_SERVICE -> toColorState()
            MOTION_STATE_SERVICE -> toMotionState()
            TARGET_TEMPERATURE_STATE_SERVICE -> toTargetTemperatureState()
            else -> null
        }
    }