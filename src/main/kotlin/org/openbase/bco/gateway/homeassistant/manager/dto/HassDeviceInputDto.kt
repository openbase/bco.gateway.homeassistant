package org.openbase.bco.gateway.homeassistant.manager.dto

import com.google.gson.annotations.SerializedName

data class HassDeviceInputDto(
    val id: String?,
    override val name: String?,
    @SerializedName("name_by_user")
    val nameByUser: String? = null,
    @SerializedName("area_id")
    val areaId: String? = null,
    @SerializedName("model_id")
    val modelId: String? = null,
    val model: String? = null,
    val icon: String? = null,
    val manufacturer: String? = null,
    val labels: List<String>?,
    val entryType: String? = null,
): HassInputDto
