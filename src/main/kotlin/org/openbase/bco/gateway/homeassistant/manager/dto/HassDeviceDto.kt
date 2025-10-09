package org.openbase.bco.gateway.homeassistant.manager.dto

import com.google.gson.annotations.SerializedName

data class HassDeviceDto(
    val id: String,
    val name: String,
    @SerializedName("area_id")
    val areaId: String? = null,
    val model: String? = null,
    val manufacturer: String? = null,
    val labels: List<String>,
    val entryType: String? = null,
)
