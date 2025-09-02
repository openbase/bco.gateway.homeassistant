package org.openbase.bco.device.hass.manager.dto

import com.google.gson.annotations.SerializedName

data class HassDeviceInputDto(
    val id: String?,
    override val name: String?,
    @SerializedName("area_id")
    val areaId: String? = null,
    val model: String? = null,
    val icon: String? = null,
    val manufacturer: String? = null,
    val labels: List<String>?,
    val entryType: String? = null,
): HassInputDto
