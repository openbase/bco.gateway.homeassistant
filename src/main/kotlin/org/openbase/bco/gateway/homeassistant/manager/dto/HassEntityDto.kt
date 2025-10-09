package org.openbase.bco.gateway.homeassistant.manager.dto

import com.google.gson.annotations.SerializedName

data class HassEntityDto(
    @SerializedName("unique_id")
    val id: String,
    @SerializedName("entity_id")
    val entityId: String,
    @SerializedName("area_id")
    val areaId: String,
    val platform: String,
    @SerializedName("device_id")
    val deviceId: String,
) {
    val type get() = entityId.split(".").first()
    val name get() = entityId.split(".").last()
}
