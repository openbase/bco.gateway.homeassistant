package org.openbase.bco.gateway.homeassistant.manager.dto

import com.google.gson.annotations.SerializedName
import org.openbase.bco.gateway.homeassistant.type.InputDtoProvider
import org.openbase.bco.gateway.homeassistant.type.Mergeable

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
    override val name: String,
    val icon: String? = null,
    val aliases: List<String>,
    val level: Int,
): HassDto, Mergeable<HassFloorInputDto, HassFloorDto>, InputDtoProvider<HassFloorInputDto> {

    override fun merge(input: HassFloorInputDto): HassFloorDto = copy(
        id = input.id ?: id,
        name = input.name ?: name,
        icon = input.icon ?: icon,
        aliases = input.aliases ?: aliases,
        level = input.level ?: level,
    )

    override fun toInputDto(): HassFloorInputDto = HassFloorInputDto(
        id = id,
        name = name,
        icon = icon,
        aliases = aliases,
        level = level,
    )
}
