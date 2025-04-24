package org.openbase.bco.device.hass.sync.strategy

import org.openbase.bco.device.hass.communication.HassCommunicator
import org.openbase.bco.device.hass.communication.HassCommunicator.Companion.EVENT_WS_SUBSCRIPTION
import org.openbase.bco.device.hass.communication.websocket.command.SubscriptionEvent
import org.openbase.bco.device.hass.manager.HassDeviceManager.Companion.ALIAS_KEY_HASS_FLOOR_ID
import org.openbase.bco.device.hass.manager.dto.HassFloorDto
import org.openbase.bco.device.hass.manager.dto.HassFloorInputDto
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

class ZoneSyncStrategy(
    private val hassCommunicator: HassCommunicator = HassCommunicator.instance,
    private val unitRegistry: UnitRegistry = Registries.getUnitRegistry(),
): UnitSyncStrategy<HassFloorDto, HassFloorInputDto>{
    override val unitType: UnitType = UnitType.LOCATION
    override val unitFilter: (UnitConfig) -> Boolean = { it.locationConfig?.locationType == LocationType.ZONE }


    override fun buildUnitConfig(hassDto: HassFloorDto): UnitConfig =
        UnitConfig
            .newBuilder()
            .setUnitType(UnitType.LOCATION)
            .apply { locationConfigBuilder.locationType = LocationType.ZONE }
            .setLabel(LabelProcessor.generateLabelBuilder(hassDto.name))
            .apply { metaConfigBuilder[ALIAS_KEY_HASS_FLOOR_ID] = hassDto.id }
            .build()

    override fun buildHassInputDto(unitConfig: UnitConfig): HassFloorInputDto {
        TODO("Not yet implemented")
    }

    override fun saveHassDtos(dtos: List<HassFloorInputDto>): List<HassFloorDto> =
        hassCommunicator.saveFloors(dtos)

    override fun queryHassDtos(): List<HassFloorDto> =
        hassCommunicator.getFloors()

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
