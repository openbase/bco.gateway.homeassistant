package org.openbase.bco.gateway.homeassistant.manager.dto

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
    @SerializedName(HassDto.AREA_ID)
    val id: String?,
    override val name: String?,
    @SerializedName(HassDto.FLOOR_ID)
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
