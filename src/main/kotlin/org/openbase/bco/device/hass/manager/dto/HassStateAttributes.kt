package org.openbase.bco.device.hass.manager.dto

enum class HassStateAttributes(val id: String) {
    DEVICE_CLASS("device_class"),
    EVENT_TYPE("event_type"),
    TEMPERATURE("temperature")
}

fun Map<String, Any>.deviceClass(): String = this[HassStateAttributes.DEVICE_CLASS.id] as String
fun Map<String, Any>.temperature(): Double = this[HassStateAttributes.TEMPERATURE.id] as Double
