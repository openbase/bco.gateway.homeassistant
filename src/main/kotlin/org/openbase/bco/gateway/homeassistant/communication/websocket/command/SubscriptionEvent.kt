package org.openbase.bco.gateway.homeassistant.communication.websocket.command

import com.google.gson.annotations.SerializedName
import org.openbase.bco.gateway.homeassistant.manager.dto.HassDto
import org.openbase.bco.gateway.homeassistant.manager.dto.HassStateDto

data class SubscriptionEvent(
    val id: String,
    val event: Event,
) {
    data class Event(
        @SerializedName(HassDto.EVENT_TYPE)
        val eventType: String,
        val data: EventData,
        @SerializedName(HassDto.TIME_FIRED)
        val timestamp: String,
        val origin: String,
    ) {
        data class EventData(
            @SerializedName(HassDto.ENTITY_ID)
            val entityId: String,
            @SerializedName(HassDto.NEW_STATE)
            val newState: HassStateDto,
        )
    }
}
