package org.openbase.bco.gateway.homeassistant.option

data class AddonOptions(
    val host: String?,
    val port: Int?,
    val admin: String?,
    val adminPassword: String?,
    val logLevel: String?,
    val debugMode: Boolean?,
    val homeAssistantHost: String?,
    val homeAssistantPort: Int?,
    val homeAssistantWebsocketEndpoint: String?,
    val homeAssistantRestEndpoint: String?,
)
