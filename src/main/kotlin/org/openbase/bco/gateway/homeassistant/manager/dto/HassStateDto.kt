package org.openbase.bco.gateway.homeassistant.manager.dto

import com.google.gson.annotations.SerializedName
import org.openbase.bco.gateway.homeassistant.type.HassDomainType
import org.openbase.bco.gateway.homeassistant.type.toHassDomainType

data class HassStateDto(
    @SerializedName("entity_id")
    val entityId: String,
    @SerializedName("last_changed")
    val lastChanged: String,
    val state: String,
    val attributes: Map<String, Any>,
    val context: Map<String, String?>,
) {
    val type
        get() : HassDomainType =
            entityId.split(".").first().toHassDomainType()
    val name get() = entityId.split(".").last()

    val deviceClass: String? get() = attributes[HassStateAttributes.DEVICE_CLASS.id] as? String

    private val hsColor: Pair<Double, Double>?
        get() =
            attributes["hs_color"]
                ?.toString()
                ?.replace("[", "")
                ?.replace("]", "")
                ?.split(",")
                ?.let { it[0].toDouble() to it[1].toDouble() }

    val hue: Double?
        get() =
            hsColor?.first

    val saturation: Double?
        get() =
            hsColor?.second

    val brightness: Double?
        get() =
            (attributes["brightness"] as? Double)

    val position: Double?
        get() =
            (attributes["current_position"] as? Double)

    companion object {
        const val STATE_ON: String = "on"
        const val STATE_OFF: String = "off"

        const val STATE_OPEN = "open"
        const val STATE_CLOSED = "closed"
        const val STATE_OPENING = "opening"
        const val STATE_CLOSING = "closing"
        const val STATE_STOPPED = "stopped"

        const val STATE_BUTTON_INITIAL_PRESS = "initial_press"
        const val STATE_BUTTON_REPEAT = "repeat"
        const val STATE_BUTTON_SHORT_RELEASE = "short_release"
        const val STATE_BUTTON_LONG_PRESS = "long_press"
        const val STATE_BUTTON_LONG_RELEASE = "long_release"
    }
}
