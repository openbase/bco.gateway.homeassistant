package org.example.org.openbase.bco.device.hass.manager.dto

data class HassEntityDto(val entityId: String, val name: String, val type: String,    val state: String, val attributes: Map<String, Any>)
