package org.openbase.bco.gateway.homeassistant.manager.dto

import com.google.gson.annotations.SerializedName
import org.openbase.bco.gateway.homeassistant.type.InputDtoProvider
import org.openbase.bco.gateway.homeassistant.type.Mergeable

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
data class HassAreaDto(
    @SerializedName(HassDto.AREA_ID)
    override val id: String,
    override val name: String,
    @SerializedName(HassDto.FLOOR_ID)
    val floorId: String?,

    val icon: String?,

    val picture: String?,

    /**
     * Labels are mainly tags where one can cluster different areas together.
     */
    val labels: List<String>,
    /**
     * Mainly other labels that are associated with the area.
     */
    val aliases: List<String>,
): HassDto, Mergeable<HassAreaInputDto, HassAreaDto>, InputDtoProvider<HassAreaInputDto> {

    override fun merge(input: HassAreaInputDto): HassAreaDto = copy(
        id = input.id ?: id,
        name = input.name ?: name,
        floorId = input.floorId ?: floorId,
        icon = input.icon ?: icon,
        labels = input.labels ?: labels,
        aliases = input.aliases ?: aliases,
    )

    override fun toInputDto(): HassAreaInputDto = HassAreaInputDto(
        id = id,
        name = name,
        floorId = floorId,
        icon = icon,
        labels = labels,
        aliases = aliases,
        picture = picture
    )
}
