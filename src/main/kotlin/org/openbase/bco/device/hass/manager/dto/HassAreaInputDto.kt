package org.openbase.bco.device.hass.manager.dto

import com.google.gson.annotations.SerializedName

/**
 * The original area entity looks like:
 * "aliases": [],
 * "area_id": "wandschrank",
 * "floor_id": null,
 * "icon": null,
 * "labels": [],
 * "name": "Wandschrank",
 * "picture": null,
 * "created_at": 1723284728.415599,
 * "modified_at": 1723284728.415601
 */
data class HassAreaInputDto(
    @SerializedName("area_id")
    val id: String?,
    val name: String?,
    @SerializedName("floor_id")
    val floorId: String?,
    val icon: String?,
    val picture: String?,

    /**
     * Labels are mainly tags where one can cluster different areas together.
     */
    val labels: List<String>?,
    /**
     * Mainly other labels that are associated with the area.
     */
    val aliases: List<String>?,
): HassInputDto
