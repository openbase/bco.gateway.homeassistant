package org.openbase.bco.device.hass.sync.strategy

import org.openbase.bco.device.hass.communication.HassCommunicator
import org.openbase.bco.device.hass.communication.HassCommunicator.Companion.EVENT_WS_SUBSCRIPTION
import org.openbase.bco.device.hass.communication.websocket.command.SubscriptionEvent
import org.openbase.bco.device.hass.manager.HassDeviceManager.Companion.ALIAS_KEY_BCO_ICON
import org.openbase.bco.device.hass.manager.HassDeviceManager.Companion.ALIAS_KEY_HASS_TYPE
import org.openbase.bco.device.hass.manager.dto.HassAreaDto
import org.openbase.bco.device.hass.manager.dto.HassAreaInputDto
import org.openbase.bco.device.hass.manager.dto.HassDeviceDto
import org.openbase.bco.device.hass.manager.dto.HassDeviceInputDto
import org.openbase.bco.device.hass.manager.dto.HassFloorDto
import org.openbase.bco.device.hass.sync.DtoCache
import org.openbase.bco.device.hass.type.HassType
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

class DeviceSyncStrategy(
    private val areaCache: DtoCache<HassAreaDto>,
    private val hassCommunicator: HassCommunicator = HassCommunicator.instance,
    private val unitRegistry: UnitRegistry = Registries.getUnitRegistry(),
): UnitSyncStrategy<HassDeviceDto, HassDeviceInputDto> {
    override val unitType: UnitType = UnitType.LOCATION
    override val hassType: HassType = HassType.DEVICE
    override val unitFilter: (UnitConfig) -> Boolean = { it.locationConfig?.locationType == LocationType.TILE }
    override fun buildUnitConfig(hassDto: HassDeviceDto): UnitConfig =
        UnitConfig
            .newBuilder()
            .setUnitType(UnitType.LOCATION)
            .apply { locationConfigBuilder.locationType = LocationType.TILE }
            .setLabel(LabelProcessor.generateLabelBuilder(hassDto.name))
            .link(hassDto)
            .apply { metaConfigBuilder[ALIAS_KEY_HASS_TYPE] = hassType.name }
            .build()

    override fun buildHassInputDto(unitConfig: UnitConfig): HassDeviceInputDto = HassDeviceInputDto(
        id = unitConfig.toHassId(),
        name = LabelProcessor.getBestMatch(unitConfig.label),
        areaId = areaCache.getDtoByUnitId(unitConfig.placementConfig.locationId)?.id,
        icon =  unitConfig.metaConfig[ALIAS_KEY_BCO_ICON],
        labels = null,
    )

    override fun saveHassDto(dto: HassDeviceInputDto): HassDeviceDto =
        hassCommunicator.saveDevice(dto)

    override fun queryHassDtos(): List<HassDeviceDto> =
        HassCommunicator.instance
            .getDevices()

    override fun deleteHassDto(dto: HassDeviceDto): HassDeviceDto =
        hassCommunicator.deleteDevice(dto)

    override fun onDtoChanges(eventProcessor: (event: SubscriptionEvent.Event) -> Any): AutoCloseable =
        hassCommunicator.subscribe(
            commandType = EVENT_WS_SUBSCRIPTION,
            eventType = HassCommunicator.HassEventType.DEVICE_UPDATE,
            eventProcessor
        ).let { AutoCloseable { hassCommunicator.unsubscribe(it) } }

    override fun onUnitChanges(eventProcessor: () -> Any): AutoCloseable =
        Observer<DataProvider<UnitRegistryData?>, UnitRegistryData> { _, _ -> eventProcessor.invoke() }
            .also { unitRegistry.addDataObserver(it) }
            .let { AutoCloseable { unitRegistry.removeDataObserver(it) } }
}
