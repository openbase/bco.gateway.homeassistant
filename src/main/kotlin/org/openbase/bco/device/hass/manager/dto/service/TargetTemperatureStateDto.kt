package org.openbase.bco.device.hass.manager.dto.service

import com.google.gson.annotations.SerializedName

data class TargetTemperatureStateDto(
    val temperature: Double,
) : ServiceDto
