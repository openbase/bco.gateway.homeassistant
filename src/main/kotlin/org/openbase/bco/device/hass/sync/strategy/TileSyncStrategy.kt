package org.openbase.bco.device.hass.sync.strategy

import org.openbase.bco.device.hass.communication.HassCommunicator
import org.openbase.bco.device.hass.communication.HassCommunicator.Companion.EVENT_WS_SUBSCRIPTION
import org.openbase.bco.device.hass.communication.websocket.command.SubscriptionEvent
import org.openbase.bco.device.hass.manager.HassDeviceManager.Companion.ALIAS_KEY_BCO_ICON
import org.openbase.bco.device.hass.manager.HassDeviceManager.Companion.ALIAS_KEY_HASS_AREA_ID
import org.openbase.bco.device.hass.manager.dto.HassAreaDto
import org.openbase.bco.device.hass.manager.dto.HassAreaInputDto
import org.openbase.bco.device.hass.util.get
import org.openbase.bco.device.hass.util.set
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
    private val hassCommunicator: HassCommunicator = HassCommunicator.instance,
    private val unitRegistry: UnitRegistry = Registries.getUnitRegistry(),
): UnitSyncStrategy<HassAreaDto, HassAreaInputDto> {
    override val unitType: UnitType = UnitType.LOCATION
    override val unitFilter: (UnitConfig) -> Boolean = { it.locationConfig?.locationType == LocationType.TILE }
    override fun buildUnitConfig(hassDto: HassAreaDto): UnitConfig =
        UnitConfig
            .newBuilder()
            .setUnitType(UnitType.LOCATION)
            .apply { locationConfigBuilder.locationType = LocationType.TILE }
            .setLabel(LabelProcessor.generateLabelBuilder(hassDto.name))
            .apply { metaConfigBuilder[ALIAS_KEY_HASS_AREA_ID] = hassDto.id }
            .build()

    override fun buildHassInputDto(unitConfig: UnitConfig): HassAreaInputDto = HassAreaInputDto(
        id = unitConfig.toHassId(),
        name = LabelProcessor.getBestMatch(unitConfig.label),
        floorId = unitConfig.locationConfig?.locationType?.name,
        icon =  unitConfig.metaConfig[ALIAS_KEY_BCO_ICON],
        picture = null,
        labels = null,
        aliases = unitConfig.label.entryList.flatMap { it.valueList },
    )

    override fun UnitConfig.toHassId(): String? = metaConfig[ALIAS_KEY_HASS_AREA_ID]

    override fun saveHassDto(dtos: HassAreaInputDto): HassAreaDto =
        hassCommunicator.saveArea(dtos)

    override fun queryHassDtos(): List<HassAreaDto> =
        HassCommunicator.instance
            .getAreas()

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
