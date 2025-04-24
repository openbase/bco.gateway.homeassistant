package org.openbase.bco.device.hass.manager.transformer

import org.openbase.bco.device.hass.manager.dto.HassStateDto
import org.openbase.bco.device.hass.manager.service.isBatterySensor
import org.openbase.bco.device.hass.manager.service.isBrightnessState
import org.openbase.bco.device.hass.manager.service.isButtonState
import org.openbase.bco.device.hass.manager.service.isColorableLight
import org.openbase.bco.device.hass.manager.service.isMotionSensor
import org.openbase.bco.device.hass.type.HassDomainType
import org.openbase.type.domotic.service.ServiceTemplateType.ServiceTemplate.ServiceType

fun HassStateDto.toServiceType() : ServiceType = when (type) {
    HassDomainType.LIGHT -> {
        if (isColorableLight()) {
            ServiceType.COLOR_STATE_SERVICE
        } else if (isBrightnessState()) {
            ServiceType.BRIGHTNESS_STATE_SERVICE
        } else {
            ServiceType.POWER_STATE_SERVICE
        }
    }

    HassDomainType.SENSOR -> if (isBatterySensor()) {
        ServiceType.BATTERY_STATE_SERVICE
    } else {
        ServiceType.UNKNOWN
    }

    HassDomainType.BINARY_SENSOR -> if (isMotionSensor()) {
        ServiceType.MOTION_STATE_SERVICE
    } else {
        ServiceType.UNKNOWN
    }

    HassDomainType.BUTTON -> ServiceType.BUTTON_STATE_SERVICE

    HassDomainType.EVENT -> if (isButtonState()) {
        ServiceType.BUTTON_STATE_SERVICE
    } else {
        ServiceType.UNKNOWN
    }

    HassDomainType.CLIMATE -> ServiceType.TARGET_TEMPERATURE_STATE_SERVICE

    else -> ServiceType.UNKNOWN
}
