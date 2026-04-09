package org.openbase.bco.gateway.homeassistant.sync.strategy

import org.openbase.bco.gateway.homeassistant.communication.websocket.command.SubscriptionEvent
import org.openbase.bco.gateway.homeassistant.manager.HassDeviceManager.Companion.ALIAS_KEY_HASS_ID
import org.openbase.bco.gateway.homeassistant.manager.HassDeviceManager.Companion.ALIAS_KEY_HASS_TYPE
import org.openbase.bco.gateway.homeassistant.manager.dto.HassDto
import org.openbase.bco.gateway.homeassistant.manager.dto.HassInputDto
import org.openbase.bco.gateway.homeassistant.sync.DtoCache
import org.openbase.bco.gateway.homeassistant.type.HassType
import org.openbase.bco.gateway.homeassistant.util.get
import org.openbase.bco.gateway.homeassistant.util.set
import org.openbase.bco.registry.unit.lib.UnitRegistry
import org.openbase.type.domotic.unit.UnitConfigType.UnitConfig
import org.openbase.type.domotic.unit.UnitTemplateType

interface UnitSyncStrategy<HASS_DTO: HassDto, HASS_INPUT_DTO: HassInputDto> {
    val name: String get() = this::class.simpleName ?: "Unknown"
    val unitType: UnitTemplateType.UnitTemplate.UnitType
    val hassType: HassType
    val unitFilter: (UnitConfig) -> Boolean
    val dependencies: List<DtoCache<*>>
        get() = emptyList()

    fun buildUnitConfig(hassDto: HASS_DTO): UnitConfig
    fun buildHassInputDto(unitConfig: UnitConfig): HASS_INPUT_DTO

    fun UnitConfig.toHassId(): String? = metaConfig[ALIAS_KEY_HASS_ID]

    fun saveHassDto(dto: HASS_INPUT_DTO): HASS_DTO
    fun queryHassDtos(): List<HASS_DTO>
    fun deleteHassDto(dto: HASS_DTO): HASS_DTO = dto

    fun onDtoChanges(eventProcessor: (event: SubscriptionEvent.Event) -> Any): AutoCloseable
    fun UnitConfig.link(hassDto: HASS_DTO): UnitConfig.Builder = toBuilder().link(hassDto)
    fun UnitConfig.Builder.link(hassDto: HASS_DTO): UnitConfig.Builder = apply {
        metaConfigBuilder[ALIAS_KEY_HASS_ID] = hassDto.id
        metaConfigBuilder[ALIAS_KEY_HASS_TYPE] = hassType.name
    }
    fun onUnitChanges(eventProcessor: () -> Any): AutoCloseable

    /**
     * Query unit configs that are relevant for this strategy.
     * Override to provide custom querying logic (e.g. when units span multiple types).
     */
    fun queryUnitConfigs(unitRegistry: UnitRegistry): List<UnitConfig> =
        unitRegistry.getUnitConfigsByUnitType(unitType).filter(unitFilter)
}
