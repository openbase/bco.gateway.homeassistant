package org.openbase.bco.device.hass.manager.service

import org.openbase.bco.device.hass.manager.dto.HassDeviceClass
import org.openbase.bco.device.hass.manager.dto.HassStateAttributes
import org.openbase.bco.device.hass.manager.dto.HassStateDto
import org.openbase.type.domotic.state.TemperatureStateType.TemperatureState

fun HassStateDto.isTemperatureSensor() : Boolean =
    attributes[HassStateAttributes.DEVICE_CLASS.id] == HassDeviceClass.TEMPERATURE.id


fun HassStateDto.toTemperatureState(): TemperatureState.Builder {
    return TemperatureState.newBuilder().apply {
        temperature = state.toDouble()
    }
}
