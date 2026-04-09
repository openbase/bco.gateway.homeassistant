package org.openbase.bco.gateway.homeassistant.manager.dto

import com.google.gson.annotations.SerializedName

data class HassEntityInputDto(
    @SerializedName(HassDto.ENTITY_ID)
    val entityId: String? = null,
    override val name: String? = null,
    @SerializedName(HassDto.AREA_ID)
    val areaId: String? = null,
    @SerializedName(HassDto.DEVICE_ID)
    val deviceId: String? = null,
    val icon: String? = null,
): HassInputDto

