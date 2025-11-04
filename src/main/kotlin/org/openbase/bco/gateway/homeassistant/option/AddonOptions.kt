package org.openbase.bco.gateway.homeassistant.option

data class AddonOptions(
    val host: String?,
    val port: Int?,
    val admin: String?,
    val adminPassword: String?
)
