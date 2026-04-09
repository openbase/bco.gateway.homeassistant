package org.openbase.bco.gateway.homeassistant.manager.dto

import com.google.gson.annotations.SerializedName

data class HassFloorInputDto(
    @SerializedName(HassDto.FLOOR_ID)
    val id: String? = null,
    override val name: String? = null,
    val icon: String? = null,
    val aliases: List<String>? = null,
    val level: Int? = null,
): HassInputDto
