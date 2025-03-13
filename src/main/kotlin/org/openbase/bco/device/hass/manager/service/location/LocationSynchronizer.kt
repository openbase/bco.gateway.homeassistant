package org.openbase.bco.device.hass.manager.service.location

import org.openbase.bco.device.hass.communication.HassCommunicator
import org.openbase.bco.device.hass.communication.HassCommunicator.Companion.EVENT_WS_SUBSCRIPTION
import org.openbase.bco.device.hass.manager.HassDeviceManager.Companion.ALIAS_KEY_HASS_AREA_ID
import org.openbase.bco.device.hass.manager.HassDeviceManager.Companion.ALIAS_KEY_HASS_FLOOR_ID
import org.openbase.bco.device.hass.manager.dto.HassAreaDto
import org.openbase.bco.device.hass.manager.dto.HassFloorDto
import org.openbase.bco.device.hass.util.await
import org.openbase.bco.device.hass.util.get
import org.openbase.bco.device.hass.util.set
import org.openbase.bco.registry.remote.Registries
import org.openbase.jul.extension.protobuf.ProtoBufBuilderProcessor.mergeFromWithoutRepeatedFields
import org.openbase.jul.extension.type.processing.LabelProcessor
import org.openbase.jul.iface.Activatable
import org.openbase.jul.schedule.GlobalCachedExecutorService
import org.openbase.type.domotic.state.ConnectionStateType
import org.openbase.type.domotic.unit.UnitConfigType.UnitConfig
import org.openbase.type.domotic.unit.UnitTemplateType.UnitTemplate.UnitType
import org.openbase.type.domotic.unit.location.LocationConfigType.LocationConfig.LocationType
import org.slf4j.LoggerFactory
import kotlin.collections.map

class LocationSynchronizer: Activatable {

    var active: Boolean = false

    var areaToTiles: List<Pair<HassAreaDto, UnitConfig>> = listOf()
        set(value) {
            areaIdToMapping = value.associateBy { it.first.id }
            tileIdToMapping = value.associateBy { it.second.id }
        }
    var areaIdToMapping: Map<String, Pair<HassAreaDto, UnitConfig>> = mapOf()
    var tileIdToMapping: Map<String, Pair<HassAreaDto, UnitConfig>> = mapOf()
    var areas: List<HassAreaDto> = areaToTiles.map { (area, _) -> area }
    var tiles: List<UnitConfig> = areaToTiles.map { (_, tile) -> tile }

    fun findTileByAreaId(areaId: String?) = areaId?.let {areaIdToMapping[areaId]?.let { (area, _) -> area } }
    fun findZoneByFloorId(floorId: String?) = floorId?.let { floorIdToMapping[floorId]?.let { (area, _) -> area } }

    var floorToZones: List<Pair<HassFloorDto, UnitConfig>> = listOf()
        set(value) {
            floorIdToMapping = value.associateBy { it.first.id }
            zoneIdToMapping = value.associateBy { it.second.id }
        }
    var floorIdToMapping: Map<String, Pair<HassFloorDto, UnitConfig>> = mapOf()
    var zoneIdToMapping: Map<String, Pair<HassFloorDto, UnitConfig>> = mapOf()
    var floors: List<HassFloorDto> = floorToZones.map { (floor, _) -> floor }
    var zones: List<UnitConfig> = floorToZones.map { (_, zone) -> zone }

    override fun activate() {
        active = true

        GlobalCachedExecutorService.execute {

            if (!HassCommunicator.instance.isConnected) {
                LOGGER.info("Waiting for hass connection...")
                HassCommunicator.instance.waitForConnectionState(ConnectionStateType.ConnectionState.State.CONNECTED)
            }

            HassCommunicator.instance.subscribe(EVENT_WS_SUBSCRIPTION, HassCommunicator.HassEventType.AREA_UPDATE) { event ->
                LOGGER.info("new area update: $event")
//                syncAll()
            }

            HassCommunicator.instance.subscribe(EVENT_WS_SUBSCRIPTION, HassCommunicator.HassEventType.FLOOR_UPDATE) { event ->
                LOGGER.info("new floor update: $event")
//                syncAll()
            }

            syncAll()

            LOGGER.info("activated ${this::class.simpleName}")
        }
    }

    override fun deactivate() {
        active = false
    }

    override fun isActive(): Boolean = active

    fun syncAll() {
        Registries
            .getUnitRegistry()
            .getUnitConfigsByUnitType(UnitType.LOCATION)
            .also { locationConfigs -> floorToZones = syncFloors(locationConfigs) }
            .also { locationConfigs -> areaToTiles = syncAreas(locationConfigs) }
    }

    fun syncFloors(locationConfigs: List<UnitConfig>): List<Pair<HassFloorDto, UnitConfig>> {
        // ======= SYNC FlOORS ========
        val floorIdToZones =
            locationConfigs
                .filter { it.locationConfig.locationType == LocationType.ZONE }
                .filter { it.metaConfig.entryList.any { it.key == ALIAS_KEY_HASS_FLOOR_ID } }
                .associateBy { it.metaConfig[ALIAS_KEY_HASS_FLOOR_ID] }

        return HassCommunicator.instance
            .getFloors()
            .map { floor ->
                floor to UnitConfig
                    .newBuilder()
                    .setUnitType(UnitType.LOCATION)
                    .apply {
                        locationConfigBuilder.locationType = LocationType.ZONE
                    }
                    .setLabel(LabelProcessor.generateLabelBuilder(floor.name))
                    .apply { metaConfigBuilder[ALIAS_KEY_HASS_FLOOR_ID] = floor.id }
                    .build()
            }.map { (floor, zoneConfig) ->
                floor to (floorIdToZones[zoneConfig.metaConfig[ALIAS_KEY_HASS_FLOOR_ID]]?.let { existingZoneConfig ->
                    existingZoneConfig.toBuilder().mergeFromWithoutRepeatedFields(zoneConfig).build().let {
                        Registries.getUnitRegistry().updateUnitConfig(it).await()
                    }
                } ?: Registries.getUnitRegistry().registerUnitConfig(zoneConfig).await())
            }
    }

    fun syncAreas(locationConfigs: List<UnitConfig>): List<Pair<HassAreaDto, UnitConfig>> {

        // ======= SYNC AREAS ========
        val areaIdToTiles =
            locationConfigs
                .filter { it.locationConfig.locationType == LocationType.TILE }
                .filter { it.metaConfig.entryList.any { it.key == ALIAS_KEY_HASS_AREA_ID } }
                .associateBy { it.metaConfig[ALIAS_KEY_HASS_AREA_ID] }


           return HassCommunicator.instance
                .getAreas()
                .map { area ->
                    area to UnitConfig
                        .newBuilder()
                        .setUnitType(UnitType.LOCATION)
                        .apply {
                            locationConfigBuilder.locationType = LocationType.TILE
                        }.setLabel(LabelProcessor.generateLabelBuilder(area.name))
                        .apply { metaConfigBuilder[ALIAS_KEY_HASS_AREA_ID] = area.id }
                        .build()
                }.map { (area, tileConfig) ->
                    area to (areaIdToTiles[tileConfig.metaConfig[ALIAS_KEY_HASS_AREA_ID]]?.let { existingTileConfig ->
                        existingTileConfig.toBuilder().mergeFromWithoutRepeatedFields(tileConfig).build().let {
                            Registries.getUnitRegistry().updateUnitConfig(it).await()
                        }
                    } ?: Registries.getUnitRegistry().registerUnitConfig(tileConfig).await())
                }
    }

    companion object {
        val LOGGER = LoggerFactory.getLogger(LocationSynchronizer::class.java)
    }
}
