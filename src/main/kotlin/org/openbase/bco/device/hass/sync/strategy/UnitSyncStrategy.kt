package org.openbase.bco.device.hass.sync.strategy

import org.openbase.bco.device.hass.communication.websocket.command.SubscriptionEvent
import org.openbase.bco.device.hass.manager.dto.HassDto
import org.openbase.bco.device.hass.manager.dto.HassInputDto
import org.openbase.type.domotic.unit.UnitConfigType.UnitConfig
import org.openbase.type.domotic.unit.UnitTemplateType

interface UnitSyncStrategy<HASS_DTO: HassDto, HASS_INPUT_DTO: HassInputDto> {
    val name: String get() = this::class.simpleName ?: "Unknown"
    val unitType: UnitTemplateType.UnitTemplate.UnitType
    val unitFilter: (UnitConfig) -> Boolean

    fun buildUnitConfig(hassDto: HASS_DTO): UnitConfig
    fun buildHassInputDto(unitConfig: UnitConfig): HASS_INPUT_DTO

    fun saveHassDtos(dtos: List<HASS_INPUT_DTO>): List<HASS_DTO>

    fun queryHassDtos(): List<HASS_DTO>

    fun onDtoChanges(eventProcessor: (event: SubscriptionEvent.Event) -> Any): AutoCloseable
    fun onUnitChanges(eventProcessor: () -> Any): AutoCloseable
}
