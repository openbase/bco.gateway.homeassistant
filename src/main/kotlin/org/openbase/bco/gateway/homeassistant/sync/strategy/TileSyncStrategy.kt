package org.openbase.bco.gateway.homeassistant.sync.strategy

import org.openbase.bco.gateway.homeassistant.communication.HassCommunicator
import org.openbase.bco.gateway.homeassistant.communication.HassCommunicator.Companion.EVENT_WS_SUBSCRIPTION
import org.openbase.bco.gateway.homeassistant.communication.websocket.command.SubscriptionEvent
import org.openbase.bco.gateway.homeassistant.manager.HassDeviceManager.Companion.ALIAS_KEY_BCO_ICON
import org.openbase.bco.gateway.homeassistant.manager.HassDeviceManager.Companion.ALIAS_KEY_HASS_TYPE
import org.openbase.bco.gateway.homeassistant.manager.dto.HassAreaDto
import org.openbase.bco.gateway.homeassistant.manager.dto.HassAreaInputDto
import org.openbase.bco.gateway.homeassistant.manager.dto.HassFloorDto
import org.openbase.bco.gateway.homeassistant.sync.DtoCache
import org.openbase.bco.gateway.homeassistant.type.HassType
import org.openbase.bco.gateway.homeassistant.util.get
import org.openbase.bco.gateway.homeassistant.util.set
import org.openbase.bco.registry.remote.Registries
import org.openbase.bco.registry.unit.lib.UnitRegistry
import org.openbase.jul.extension.type.processing.LabelProcessor
import org.openbase.jul.pattern.Observer
import org.openbase.jul.pattern.provider.DataProvider
import org.openbase.type.domotic.registry.UnitRegistryDataType.UnitRegistryData
import org.openbase.type.domotic.unit.UnitConfigType.UnitConfig
import org.openbase.type.domotic.unit.UnitTemplateType.UnitTemplate.UnitType
import org.openbase.type.domotic.unit.location.LocationConfigType.LocationConfig.LocationType

class TileSyncStrategy(
    private val floorCache: DtoCache<HassFloorDto>,
    private val hassCommunicator: HassCommunicator = HassCommunicator.instance,
    override val unitRegistry: UnitRegistry = Registries.getUnitRegistry(),
): UnitSyncStrategy<HassAreaDto, HassAreaInputDto> {
    override val dependencies = listOf(floorCache)
    override val unitType: UnitType = UnitType.LOCATION
    override val hassType: HassType = HassType.AREA
    override val unitFilter: (UnitConfig) -> Boolean = { it.locationConfig?.locationType == LocationType.TILE }
    override fun buildUnitConfig(hassDto: HassAreaDto): UnitConfig =
        UnitConfig
            .newBuilder()
            .setUnitType(unitType)
            .apply { locationConfigBuilder.locationType = LocationType.TILE }
            .setLabel(LabelProcessor.generateLabelBuilder(hassDto.name))
            .link(hassDto)
            .apply { metaConfigBuilder[ALIAS_KEY_HASS_TYPE] = hassType.name }
            .build()

    override fun buildHassInputDto(unitConfig: UnitConfig): HassAreaInputDto = HassAreaInputDto(
        id = unitConfig.toHassId(),
        name = LabelProcessor.getBestMatch(unitConfig.label),
        floorId = floorCache.getDtoByUnitId(unitConfig.placementConfig.locationId)?.id,
        icon =  unitConfig.metaConfig[ALIAS_KEY_BCO_ICON],
        picture = null,
        labels = null,
        aliases = unitConfig.label.entryList.flatMap { it.valueList },
    )

    override fun saveHassDto(dto: HassAreaInputDto): HassAreaDto =
        hassCommunicator.saveArea(dto)

    override fun queryHassDtos(): List<HassAreaDto> =
        hassCommunicator.getAreas()

    override fun deleteHassDto(dto: HassAreaDto): HassAreaDto =
        hassCommunicator.deleteArea(dto)

    override fun onDtoChanges(eventProcessor: (event: SubscriptionEvent.Event) -> Any): AutoCloseable =
        hassCommunicator.subscribe(
            commandType = EVENT_WS_SUBSCRIPTION,
            eventType = HassCommunicator.HassEventType.AREA_UPDATE,
            eventProcessor
        ).let { AutoCloseable { hassCommunicator.unsubscribe(it) } }

    override fun onUnitChanges(eventProcessor: () -> Any): AutoCloseable =
        Observer<DataProvider<UnitRegistryData?>, UnitRegistryData> { _, _ -> eventProcessor.invoke() }
            .also { unitRegistry.addDataObserver(it) }
            .let { AutoCloseable { unitRegistry.removeDataObserver(it) } }
}
