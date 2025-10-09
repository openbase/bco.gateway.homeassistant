package org.openbase.bco.gateway.homeassistant.manager.dto.service

import com.google.gson.annotations.SerializedName

data class ColorServiceDto(
    @SerializedName("entity_id")
    val entityId: String,
    val brightness: Double,
    @SerializedName("hs_color")
    val hsColor: List<Double>,
): ServiceDto
