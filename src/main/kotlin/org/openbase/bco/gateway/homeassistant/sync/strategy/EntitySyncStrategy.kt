package org.openbase.bco.gateway.homeassistant.sync.strategy

import org.openbase.bco.gateway.homeassistant.communication.HassCommunicator
import org.openbase.bco.gateway.homeassistant.communication.HassCommunicator.Companion.EVENT_WS_SUBSCRIPTION
import org.openbase.bco.gateway.homeassistant.communication.websocket.command.SubscriptionEvent
import org.openbase.bco.gateway.homeassistant.manager.HassDeviceManager.Companion.ALIAS_KEY_HASS_DEVICE_ID
import org.openbase.bco.gateway.homeassistant.manager.HassDeviceManager.Companion.ALIAS_KEY_HASS_ENTITY_ID
import org.openbase.bco.gateway.homeassistant.manager.HassDeviceManager.Companion.ALIAS_KEY_HASS_ID
import org.openbase.bco.gateway.homeassistant.manager.HassDeviceManager.Companion.ALIAS_KEY_HASS_TYPE
import org.openbase.bco.gateway.homeassistant.manager.HassDeviceManager.Companion.HASS_ENTITY_DEVICE_CLASS
import org.openbase.bco.gateway.homeassistant.manager.HassDeviceManager.Companion.HASS_ENTITY_TYPE
import org.openbase.bco.gateway.homeassistant.manager.dto.HassAreaDto
import org.openbase.bco.gateway.homeassistant.manager.dto.HassDeviceDto
import org.openbase.bco.gateway.homeassistant.manager.dto.HassEntityDto
import org.openbase.bco.gateway.homeassistant.manager.dto.HassEntityInputDto
import org.openbase.bco.gateway.homeassistant.sync.DtoCache
import org.openbase.bco.gateway.homeassistant.type.HassType
import org.openbase.bco.gateway.homeassistant.util.get
import org.openbase.bco.gateway.homeassistant.util.set
import org.openbase.bco.registry.remote.Registries
import org.openbase.bco.registry.unit.lib.UnitRegistry
import org.openbase.jul.pattern.Observer
import org.openbase.jul.pattern.provider.DataProvider
import org.openbase.type.domotic.registry.UnitRegistryDataType.UnitRegistryData
import org.openbase.type.domotic.unit.UnitConfigType.UnitConfig
import org.openbase.type.domotic.unit.UnitTemplateType.UnitTemplate.UnitType
import org.slf4j.LoggerFactory

/**
 * Strategy for syncing Home Assistant entities to their corresponding BCO DAL units.
 *
 * DAL units are auto-created when a device with a device class is registered in BCO.
 * This strategy links those DAL units to the corresponding Hass entities by matching
 * entity type and device class from the unit template meta config.
 *
 * Entities are managed by Home Assistant and cannot be created or deleted from BCO.
 * The BCO→Hass sync only produces identity mappings so that no changes are pushed.
 */
class EntitySyncStrategy(
    private val deviceCache: DtoCache<HassDeviceDto>,
    private val areaCache: DtoCache<HassAreaDto>,
    private val hassCommunicator: HassCommunicator = HassCommunicator.instance,
    private val unitRegistry: UnitRegistry = Registries.getUnitRegistry(),
) : UnitSyncStrategy<HassEntityDto, HassEntityInputDto> {

    override val dependencies = listOf(deviceCache, areaCache)
    override val unitType: UnitType = UnitType.UNKNOWN
    override val hassType: HassType = HassType.ENTITY
    override val unitFilter: (UnitConfig) -> Boolean = {
        it.metaConfig.entryList.any { entry -> entry.key == ALIAS_KEY_HASS_ENTITY_ID }
    }

    /**
     * Query unit configs for all DAL units that belong to devices managed by the device cache.
     * This includes both already-linked units (with ALIAS_KEY_HASS_ENTITY_ID) and
     * unlinked units (belonging to a device in the device cache).
     */
    override fun queryUnitConfigs(unitRegistry: UnitRegistry): List<UnitConfig> {
        val deviceConfigs = unitRegistry.getUnitConfigsByUnitType(UnitType.DEVICE)
            .filter { it.metaConfig.entryList.any { entry -> entry.key == ALIAS_KEY_HASS_DEVICE_ID } }

        val dalUnitIds = deviceConfigs
            .flatMap { it.deviceConfig.unitIdList }
            .toSet()

        return dalUnitIds
            .mapNotNull { unitId ->
                runCatching { unitRegistry.getUnitConfigById(unitId) }.getOrNull()
            }
    }

    override fun buildUnitConfig(hassDto: HassEntityDto): UnitConfig {
        // Find the BCO device config that corresponds to this entity's device
        val deviceUnitConfig = deviceCache.getUnitConfigByDtoId(hassDto.deviceId)
            ?: return UnitConfig.getDefaultInstance() // shouldn't happen if dependencies are resolved

        // Find the states to determine entity device classes
        val entityIdToDeviceClass: Map<String, String?> =
            hassCommunicator.getStates().associate { it.entityId to it.deviceClass }

        // Get all entities for this device
        val deviceEntities = hassCommunicator.getEntities()
            .filter { it.deviceId == hassDto.deviceId }

        // Find the matching DAL unit from the device's unit list
        for (dalUnitId in deviceUnitConfig.deviceConfig.unitIdList) {
            val dalUnit = runCatching {
                Registries.getUnitRegistry().getUnitConfigById(dalUnitId)
            }.getOrNull() ?: continue

            val templateMetaConfig = runCatching {
                Registries.getTemplateRegistry().getUnitTemplateByType(dalUnit.unitType).metaConfig
            }.getOrNull() ?: continue

            val entityType = templateMetaConfig[HASS_ENTITY_TYPE] ?: continue
            val entityDeviceClass = templateMetaConfig[HASS_ENTITY_DEVICE_CLASS]

            // Match entity to this DAL unit
            val matchingEntity = deviceEntities
                .filter { entityIdToDeviceClass[it.entityId] == entityDeviceClass }
                .find { it.type == entityType }

            if (matchingEntity != null && matchingEntity.entityId == hassDto.entityId) {
                return dalUnit
                    .toBuilder()
                    .apply {
                        if (hassDto.entityId !in aliasList) {
                            addAlias(hassDto.entityId)
                        }
                        metaConfigBuilder[ALIAS_KEY_HASS_ENTITY_ID] = hassDto.entityId
                        areaCache.getUnitConfigByDtoId(hassDto.areaId)
                            ?.let { unitLocation ->
                                placementConfigBuilder.locationId = unitLocation.id
                            }
                    }
                    .link(hassDto)
                    .build()
            }
        }

        // No matching DAL unit found - return a minimal config
        // This will be handled by the synchronizer (won't save since it has no ID)
        return UnitConfig.getDefaultInstance()
    }

    override fun buildHassInputDto(unitConfig: UnitConfig): HassEntityInputDto = HassEntityInputDto(
        entityId = unitConfig.metaConfig[ALIAS_KEY_HASS_ENTITY_ID],
    )

    override fun saveHassDto(dto: HassEntityInputDto): HassEntityDto =
        throw NotImplementedError("Entities are managed by Home Assistant and cannot be saved from BCO")

    override fun deleteHassDto(dto: HassEntityDto): HassEntityDto = dto // Entities cannot be deleted via API

    override fun UnitConfig.toHassId(): String? =
        metaConfig[ALIAS_KEY_HASS_ID]

    override fun UnitConfig.Builder.link(hassDto: HassEntityDto): UnitConfig.Builder = apply {
        metaConfigBuilder[ALIAS_KEY_HASS_ID] = hassDto.id
        metaConfigBuilder[ALIAS_KEY_HASS_TYPE] = hassType.name
        metaConfigBuilder[ALIAS_KEY_HASS_ENTITY_ID] = hassDto.entityId
    }

    override fun queryHassDtos(): List<HassEntityDto> {
        val deviceIds = deviceCache.dtos.map { it.id }.toSet()
        return hassCommunicator.getEntities()
            .filter { it.deviceId in deviceIds }
    }

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

    companion object {
        private val LOGGER = LoggerFactory.getLogger(EntitySyncStrategy::class.java)
    }
}

