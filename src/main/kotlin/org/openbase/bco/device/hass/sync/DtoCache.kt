package org.openbase.bco.device.hass.sync

import org.openbase.bco.device.hass.manager.dto.HassDto
import org.openbase.type.domotic.unit.UnitConfigType.UnitConfig
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class DtoCache<HASS_DTO: HassDto> {

    private val lock = ReentrantReadWriteLock()
    private val writeCondition = lock.writeLock().newCondition()
    private val dtoCache = mutableMapOf<String, HASS_DTO>()
    private val unitConfigCache = mutableMapOf<String, UnitConfig>()
    private var unitIdToDtoCache = mutableMapOf<String, HASS_DTO>()
    private var dtoIdToUnitIdCache = mutableMapOf<String, String>()

    private var initialized = false

    fun putAll(dtos: List<Pair<UnitConfig, HASS_DTO>>) = lock.write {
        dtos.forEach { (unitConfig, dto) ->
            dtoCache[dto.id] = dto
            unitIdToDtoCache[unitConfig.id] = dto
            dtoIdToUnitIdCache[dto.id] = unitConfig.id
        }
    }

    fun confirmInit() {
        lock.write {
            initialized = true
            writeCondition.signalAll()
        }
    }

    fun waitUntilReady() =
        lock.write {
            if (!initialized) {
                writeCondition.await()
            }
        }

    fun getDtoByUnitId(unitId: String) =
        lock.read { unitIdToDtoCache[unitId] }

    fun getDtoById(dtoId: String) =
        lock.read { dtoCache[dtoId] }

    fun getUnitIdByDtoId(dtoId: String) =
        lock.read { dtoIdToUnitIdCache[dtoId] }

    fun getUnitConfigById(unitId: String): UnitConfig? =
        lock.read { unitConfigCache[unitId] }

    fun getUnitConfigByDtoId(dtoId: String?): UnitConfig? =
        lock.read {
            dtoIdToUnitIdCache[dtoId]
            ?.let { unitId -> unitConfigCache[unitId] }
    }
}
