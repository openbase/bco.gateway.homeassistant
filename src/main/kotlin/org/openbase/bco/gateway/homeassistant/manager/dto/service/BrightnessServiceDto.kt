package org.openbase.bco.gateway.homeassistant.manager.dto.service

import com.google.gson.annotations.SerializedName
import org.openbase.bco.gateway.homeassistant.manager.dto.HassDto

data class BrightnessServiceDto(
    @SerializedName(HassDto.ENTITY_ID)
    val entityId: String,
    val brightness: Double,
) : ServiceDto
