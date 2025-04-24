package org.openbase.bco.device.hass.manager.dto

import com.google.gson.annotations.SerializedName
import org.openbase.bco.device.hass.type.InputDtoProvider
import org.openbase.bco.device.hass.util.Mergeable

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
    override val id: String,
    val name: String,
    val icon: String,
    val aliases: List<String>,
): HassDto, Mergeable<HassFloorInputDto, HassFloorDto>, InputDtoProvider<HassFloorInputDto> {

    override fun merge(input: HassFloorInputDto): HassFloorDto = copy(
        id = input.id?: id,
        name = input.name?: name,
        icon = input.icon?: icon,
        aliases = input.aliases?: aliases,
    )

    override fun toInputDto(): HassFloorInputDto = HassFloorInputDto(
        id = id,
        name = name,
        icon = icon,
        aliases = aliases,
    )
}
