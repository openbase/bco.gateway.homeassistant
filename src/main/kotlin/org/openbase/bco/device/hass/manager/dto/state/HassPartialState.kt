package org.openbase.bco.device.hass.manager.dto.state

data class HassPartialState(

    val state: String,

    val attributes: Map<String, Any> = emptyMap(),
)