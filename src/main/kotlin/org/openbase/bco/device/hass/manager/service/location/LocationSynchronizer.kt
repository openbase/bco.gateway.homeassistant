package org.openbase.bco.device.hass.manager.service.location

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import org.openbase.type.domotic.state.ConnectionStateType
import org.openbase.type.domotic.unit.UnitConfigType.UnitConfig
import org.openbase.type.domotic.unit.UnitTemplateType.UnitTemplate.UnitType
import org.openbase.type.domotic.unit.location.LocationConfigType.LocationConfig.LocationType
import org.slf4j.LoggerFactory

@Deprecated(message = "remove me before PR merge.")
class LocationSynchronizer : Activatable {

    // todo: remove me before PR merge.

    private var coroutineScope: CoroutineScope? = null
    private val debounceDuration = 500L
    private val activationMutex = Mutex()

    private val syncTrigger = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private fun startDebouncedSyncCollector(scope: CoroutineScope) {
        scope.launch {
            syncTrigger
                .debounce(debounceDuration)
                .collect {
                    LOGGER.info("Debounced syncAll triggered")
                    syncAll()
                }
        }
    }

    private fun triggerSync() =  syncTrigger.tryEmit(Unit)

    @OptIn(DelicateCoroutinesApi::class)
    override fun activate() {
        GlobalScope.launch {
            activationMutex.withLock {
                if (isActive) return@withLock

                val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                coroutineScope = scope

                startDebouncedSyncCollector(scope)

                scope.launch {
                    if (!HassCommunicator.instance.isConnected) {
                        LOGGER.info("Waiting for hass connection...")
                        HassCommunicator.instance.waitForConnectionState(ConnectionStateType.ConnectionState.State.CONNECTED)
                    }

                    HassCommunicator.instance.subscribe(EVENT_WS_SUBSCRIPTION, HassCommunicator.HassEventType.AREA_UPDATE) {
                        LOGGER.info("Received AREA_UPDATE event")
                        triggerSync()
                    }

                    HassCommunicator.instance.subscribe(EVENT_WS_SUBSCRIPTION, HassCommunicator.HassEventType.FLOOR_UPDATE) {
                        LOGGER.info("Received FLOOR_UPDATE event")
                        triggerSync()
                    }

                    triggerSync()
                    LOGGER.info("Activated ${this@LocationSynchronizer::class.simpleName}")
                }
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun deactivate() {
        GlobalScope.launch {
            activationMutex.withLock {
                coroutineScope?.cancel()
                coroutineScope = null
            }
        }
    }

    override fun isActive(): Boolean = coroutineScope?.isActive == true

    var areaToTiles: List<Pair<HassAreaDto, UnitConfig>> = listOf()
        set(value) {
            areaIdToMapping = value.associateBy { it.first.id }
            tileIdToMapping = value.associateBy { it.second.id }
        }
    var areaIdToMapping: Map<String, Pair<HassAreaDto, UnitConfig>> = mapOf()
    var tileIdToMapping: Map<String, Pair<HassAreaDto, UnitConfig>> = mapOf()
    val areas: List<HassAreaDto> get() = areaToTiles.map { (area, _) -> area }
    val tiles: List<UnitConfig> get() = areaToTiles.map { (_, tile) -> tile }

    fun findTileByAreaId(areaId: String?) = areaId?.let { areaIdToMapping[areaId]?.first }
    fun findZoneByFloorId(floorId: String?) = floorId?.let { floorIdToMapping[floorId]?.first }

    var floorToZones: List<Pair<HassFloorDto, UnitConfig>> = listOf()
        set(value) {
            floorIdToMapping = value.associateBy { it.first.id }
            zoneIdToMapping = value.associateBy { it.second.id }
        }
    var floorIdToMapping: Map<String, Pair<HassFloorDto, UnitConfig>> = mapOf()
    var zoneIdToMapping: Map<String, Pair<HassFloorDto, UnitConfig>> = mapOf()
    val floors: List<HassFloorDto> get() = floorToZones.map { (floor, _) -> floor }
    val zones: List<UnitConfig> get() = floorToZones.map { (_, zone) -> zone }

    private fun syncAll() = Registries
            .getUnitRegistry()
            .getUnitConfigsByUnitType(UnitType.LOCATION)
            .also { locationConfigs -> floorToZones = syncFloors(locationConfigs) }
            .also { locationConfigs -> areaToTiles = syncAreas(locationConfigs) }

    private fun syncFloors(locationConfigs: List<UnitConfig>): List<Pair<HassFloorDto, UnitConfig>> {
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
                    .apply { locationConfigBuilder.locationType = LocationType.ZONE }
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

    private fun syncAreas(locationConfigs: List<UnitConfig>): List<Pair<HassAreaDto, UnitConfig>> {
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
                    .apply { locationConfigBuilder.locationType = LocationType.TILE }
                    .setLabel(LabelProcessor.generateLabelBuilder(area.name))
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
        private val LOGGER = LoggerFactory.getLogger(LocationSynchronizer::class.java)
    }
}
