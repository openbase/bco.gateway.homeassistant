package org.openbase.bco.device.hass.manager.dto

data class HassEntityDto(
    val entityId: String,
    val lastChanged: String,
    val state: String,
    val attributes: Map<String, Any>,
    val deviceId: String,
) {
    val type = entityId.split(".").first()
    val name = entityId.split(".").last()
}
