package org.openbase.bco.device.hass.manager.dto

import com.google.gson.annotations.SerializedName

data class HassFloorInputDto(
    @SerializedName("floor_id")
    val id: String? = null,
    val name: String? = null,
    val icon: String? = null,
    val aliases: List<String>? = null,
    val level: String? = null,
): HassInputDto
