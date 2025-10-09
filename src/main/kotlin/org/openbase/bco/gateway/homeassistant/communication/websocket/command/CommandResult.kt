package org.openbase.bco.gateway.homeassistant.communication.websocket.command

import com.google.gson.JsonElement

data class CommandResult(
    val id: Long,
    val type: String,
    val success: Boolean?,
    val result: JsonElement?,
    val error: JsonElement?,
)
