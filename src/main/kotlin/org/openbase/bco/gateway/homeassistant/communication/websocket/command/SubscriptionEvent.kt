package org.openbase.bco.gateway.homeassistant.communication.websocket.command

import com.google.gson.annotations.SerializedName
import org.openbase.bco.gateway.homeassistant.manager.dto.HassStateDto

data class SubscriptionEvent(
    val id: String,
    val event: Event,
) {
    data class Event(
        @SerializedName("event_type")
        val eventType: String,
        val data: EventData,
        @SerializedName("time_fired")
        val timestamp: String,
        val origin: String,
    ) {
        data class EventData(
            @SerializedName("entity_id")
            val entityId: String,
            @SerializedName("new_state")
            val newState: HassStateDto,
        )
    }
}
