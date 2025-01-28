package org.openbase.bco.device.hass.manager.dto.service

import com.google.gson.annotations.SerializedName

data class BrightnessServiceDto(
    @SerializedName("entity_id")
    val entityId: String,
    val brightness: Double,
) : ServiceDto