package org.openbase.bco.gateway.homeassistant.manager.dto

import org.openbase.bco.gateway.homeassistant.util.IdProvider
import org.openbase.bco.gateway.homeassistant.util.NameProvider

interface HassDto: IdProvider<String>, NameProvider<String> {
    companion object {
        const val UNIQUE_ID = "unique_id"
        const val ENTITY_ID = "entity_id"
        const val AREA_ID = "area_id"
        const val DEVICE_ID = "device_id"
        const val FLOOR_ID = "floor_id"
        const val NAME_BY_USER = "name_by_user"
        const val MODEL_ID = "model_id"
        const val SERVICE_DATA = "service_data"
        const val RETURN_RESPONSE = "return_response"
        const val EVENT_TYPE = "event_type"
        const val TIME_FIRED = "time_fired"
        const val NEW_STATE = "new_state"
        const val LAST_CHANGED = "last_changed"
        const val HS_COLOR = "hs_color"
        const val LABELS = "labels"
        const val ICON = "icon"
    }
}
