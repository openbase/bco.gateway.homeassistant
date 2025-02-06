package org.openbase.bco.device.hass.manager.service.location

import org.openbase.bco.device.hass.communication.HassCommunicator
import org.openbase.bco.device.hass.manager.HassDeviceManager.Companion.ALIAS_KEY_HASS_AREA_ID
import org.openbase.bco.device.hass.manager.HassDeviceManager.Companion.ALIAS_KEY_HASS_FLOOR_ID
import org.openbase.bco.device.hass.util.await
import org.openbase.bco.device.hass.util.get
import org.openbase.bco.device.hass.util.set
import org.openbase.bco.registry.remote.Registries
import org.openbase.jul.extension.protobuf.ProtoBufBuilderProcessor.mergeFromWithoutRepeatedFields
import org.openbase.jul.extension.type.processing.LabelProcessor
import org.openbase.jul.iface.Activatable
import org.openbase.type.domotic.unit.UnitConfigType.UnitConfig
import org.openbase.type.domotic.unit.UnitTemplateType.UnitTemplate.UnitType
import org.openbase.type.domotic.unit.location.LocationConfigType.LocationConfig.LocationType

class LocationSynchronizer: Activatable {

    var active: Boolean = false

    var tileConfigs: List<UnitConfig> = listOf()

    override fun activate() {
        active = true

        initialSync()

    }

    override fun deactivate() {
        active = false
    }

    override fun isActive(): Boolean = active

    fun initialSync() {
        syncFloors()
        tileConfigs = syncAreas()
    }

    fun syncFloors() {
        // ======= SYNC FlOORS ========
        val floorIdToZones =
            Registries
                .getUnitRegistry()
                .getUnitConfigsByUnitType(UnitType.LOCATION)
                .filter { it.metaConfig.entryList.any { it.key == ALIAS_KEY_HASS_FLOOR_ID } }
                .associateBy { it.metaConfig[ALIAS_KEY_HASS_FLOOR_ID] }

        HassCommunicator.instance
            .getFloors()
            .map { floor ->
                UnitConfig
                    .newBuilder()
                    .setUnitType(UnitType.LOCATION)
                    .apply {
                        locationConfigBuilder.locationType = LocationType.ZONE
                    }.setLabel(LabelProcessor.generateLabelBuilder(floor.name))
                    .apply { metaConfigBuilder[ALIAS_KEY_HASS_FLOOR_ID] = floor.id }
                    .build()
            }.map { zoneConfig ->
                floorIdToZones[zoneConfig.metaConfig[ALIAS_KEY_HASS_FLOOR_ID]]?.let { existingZoneConfig ->
                    existingZoneConfig.toBuilder().mergeFrom(zoneConfig).build().let {
                        Registries.getUnitRegistry().updateUnitConfig(it).await()
                    }
                } ?: Registries.getUnitRegistry().registerUnitConfig(zoneConfig).await()
            }
    }

    fun syncAreas(): List<UnitConfig> {

        // ======= SYNC AREAS ========
        val areaIdToTiles =
            Registries
                .getUnitRegistry()
                .getUnitConfigsByUnitType(UnitType.LOCATION)
                .filter { it.metaConfig.entryList.any { it.key == ALIAS_KEY_HASS_AREA_ID } }
                .associateBy { it.metaConfig[ALIAS_KEY_HASS_AREA_ID] }


           return HassCommunicator.instance
                .getAreas()
                .map { area ->
                    UnitConfig
                        .newBuilder()
                        .setUnitType(UnitType.LOCATION)
                        .apply {
                            locationConfigBuilder.locationType = LocationType.TILE
                        }.setLabel(LabelProcessor.generateLabelBuilder(area.name))
                        .apply { metaConfigBuilder[ALIAS_KEY_HASS_AREA_ID] = area.id }
                        .build()
                }.map { tileConfig ->
                    areaIdToTiles[tileConfig.metaConfig[ALIAS_KEY_HASS_AREA_ID]]?.let { existingTileConfig ->
                        existingTileConfig.toBuilder().mergeFromWithoutRepeatedFields(tileConfig).build().let {
                            Registries.getUnitRegistry().updateUnitConfig(it).await()
                        }
                    } ?: Registries.getUnitRegistry().registerUnitConfig(tileConfig).await()
                }
    }
}
