package org.openbase.bco.device.hass.communication.websocket.command

import com.google.gson.JsonElement

data class ResultCommand(
    val id: Long,
    val type: String,
    val success: Boolean?,
    val result: JsonElement,
    val error: String?,
)
