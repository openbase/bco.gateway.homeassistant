package org.openbase.bco.gateway.homeassistant.manager.dto

import com.google.gson.annotations.SerializedName

data class HassDeviceInputDto(
    val id: String?,
    override val name: String?,
    @SerializedName(HassDto.NAME_BY_USER)
    val nameByUser: String? = null,
    @SerializedName(HassDto.AREA_ID)
    val areaId: String? = null,
    @SerializedName(HassDto.MODEL_ID)
    val modelId: String? = null,
    val model: String? = null,
    val icon: String? = null,
    val manufacturer: String? = null,
    val labels: List<String>?,
    val entryType: String? = null,
): HassInputDto
