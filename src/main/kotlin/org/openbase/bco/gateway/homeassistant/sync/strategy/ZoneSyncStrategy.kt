package org.openbase.bco.gateway.homeassistant.sync.strategy

import org.openbase.bco.gateway.homeassistant.communication.HassCommunicator
import org.openbase.bco.gateway.homeassistant.communication.HassCommunicator.Companion.EVENT_WS_SUBSCRIPTION
import org.openbase.bco.gateway.homeassistant.communication.websocket.command.SubscriptionEvent
import org.openbase.bco.gateway.homeassistant.manager.HassDeviceManager.Companion.ALIAS_KEY_BCO_ICON
import org.openbase.bco.gateway.homeassistant.manager.dto.HassFloorDto
import org.openbase.bco.gateway.homeassistant.manager.dto.HassFloorInputDto
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

class ZoneSyncStrategy(
    private val hassCommunicator: HassCommunicator = HassCommunicator.instance,
    private val unitRegistry: UnitRegistry = Registries.getUnitRegistry(),
): UnitSyncStrategy<HassFloorDto, HassFloorInputDto>{
    override val unitType: UnitType = UnitType.LOCATION
    override val hassType: HassType = HassType.FLOOR
    override val unitFilter: (UnitConfig) -> Boolean = { it.locationConfig?.locationType == LocationType.ZONE }

    override fun buildUnitConfig(hassDto: HassFloorDto): UnitConfig =
        UnitConfig
            .newBuilder()
            .setUnitType(unitType)
            .apply {
                locationConfigBuilder.apply {
                    locationType = LocationType.ZONE

                    // map hass root location onto bco root location
                    if (hassDto.id == DEFAULT_HASS_ROOT_LOCATION_ID) {
                        root = true
                        id = unitRegistry.rootLocationConfig.id
                    }
                }
            }
            .setLabel(LabelProcessor.generateLabelBuilder(hassDto.name))
            .link(hassDto)
            .apply { hassDto.icon?.let { metaConfigBuilder[ALIAS_KEY_BCO_ICON] = it } }
            .build()

    override fun buildHassInputDto(unitConfig: UnitConfig): HassFloorInputDto = HassFloorInputDto(
        id = unitConfig.toHassId(),
        name = LabelProcessor.getBestMatch(unitConfig.label),
        icon = unitConfig.metaConfig[ALIAS_KEY_BCO_ICON],
        aliases = unitConfig.label.entryList.flatMap { it.valueList },
        level = 0,
    )

    override fun saveHassDto(dto: HassFloorInputDto): HassFloorDto =
        hassCommunicator.saveFloor(dto)

    override fun queryHassDtos(): List<HassFloorDto> =
        hassCommunicator.getFloors()

    override fun deleteHassDto(dto: HassFloorDto): HassFloorDto =
        hassCommunicator.deleteFloor(dto)

    override fun onDtoChanges(eventProcessor: (event: SubscriptionEvent.Event) -> Any): AutoCloseable =
        hassCommunicator.subscribe(
            commandType = EVENT_WS_SUBSCRIPTION,
            eventType = HassCommunicator.HassEventType.FLOOR_UPDATE,
            eventProcessor
        ).let { AutoCloseable { hassCommunicator.unsubscribe(it) } }

    override fun onUnitChanges(eventProcessor: () -> Any): AutoCloseable =
        Observer<DataProvider<UnitRegistryData?>, UnitRegistryData> { _, _ -> eventProcessor.invoke() }
            .also { unitRegistry.addDataObserver(it) }
            .let { AutoCloseable { unitRegistry.removeDataObserver(it) } }
    companion object {
        const val DEFAULT_HASS_ROOT_LOCATION_ID = "home"
    }
}
