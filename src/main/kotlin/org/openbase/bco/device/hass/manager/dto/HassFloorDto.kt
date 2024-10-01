package org.openbase.bco.device.hass.manager.dto

import com.google.gson.annotations.SerializedName

/**
 * Original Type:
 * {
 *   "aliases": [
 *     "Wohnung Jaci und Marian"
 *   ],
 *   "created_at": 1727806180.714932,
 *   "floor_id": "erdgeschoss",
 *   "icon": "mdi:awning",
 *   "level": 0,
 *   "name": "Erdgeschoss",
 *   "modified_at": 1727806180.714944
 * }
 */
data class HassFloorDto(
    @SerializedName("floor_id")
    val id: String,
    val name: String,
    val icon: String,
    val aliases: List<String>,
)
