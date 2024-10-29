package org.openbase.bco.device.hass.manager.dto

import com.google.gson.annotations.SerializedName

data class HassStateDto(
    @SerializedName("entity_id")
    val entityId: String,
    @SerializedName("last_changed")
    val lastChanged: String,
    val state: String,
    val attributes: Map<String, Any>,
    val context: Map<String, String?>,
) {
    val type get() = entityId.split(".").first()
    val name get() = entityId.split(".").last()
}
