package org.openbase.bco.device.hass.manager.dto.service

import com.google.gson.annotations.SerializedName

data class ColorServiceDto(
    @SerializedName("entity_id")
    val entityId: String,
    val brightness: Int,
    @SerializedName("hs_color")
    val hsColor: Pair<Int, Int>,
): ServiceDto
