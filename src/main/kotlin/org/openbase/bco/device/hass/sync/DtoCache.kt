package org.openbase.bco.device.hass.sync

import org.openbase.bco.device.hass.manager.dto.HassDto
import org.openbase.type.domotic.unit.UnitConfigType.UnitConfig

class DtoCache<HASS_DTO: HassDto> {
    private val dtoCache = mutableMapOf<String, HASS_DTO>()
    private val unitConfigCache = mutableMapOf<String, UnitConfig>()
    private var unitIdToDtoCache = mutableMapOf<String, HASS_DTO>()
    private var dtoIdToUnitIdCache = mutableMapOf<String, String>()

    fun getDtoByUnitId(unitId: String) = unitIdToDtoCache[unitId]
    fun getDtoById(dtoId: String) = dtoCache[dtoId]
    fun getUnitIdByDtoId(dtoId: String) = dtoIdToUnitIdCache[dtoId]

    fun putAll(dtos: List<Pair<UnitConfig, HASS_DTO>>) = dtos.forEach { (unitConfig, dto) ->
        dtoCache[dto.id] = dto
        unitIdToDtoCache[unitConfig.id] = dto
        dtoIdToUnitIdCache[dto.id] = unitConfig.id
    }

    fun getUnitConfigByDtoId(dtoId: String?): UnitConfig? =
        dtoIdToUnitIdCache[dtoId]
            ?.let { unitId -> unitConfigCache[unitId] }
}
