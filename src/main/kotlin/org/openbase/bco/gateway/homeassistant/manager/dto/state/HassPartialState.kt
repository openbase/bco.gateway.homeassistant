package org.openbase.bco.gateway.homeassistant.manager.dto.state

data class HassPartialState(

    val state: String,

    val attributes: Map<String, Any> = emptyMap(),
)
