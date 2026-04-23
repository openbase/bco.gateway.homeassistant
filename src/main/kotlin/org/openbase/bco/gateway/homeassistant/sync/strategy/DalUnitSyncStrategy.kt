package org.openbase.bco.gateway.homeassistant.sync.strategy

import org.openbase.bco.gateway.homeassistant.communication.HassCommunicator
import org.openbase.bco.gateway.homeassistant.communication.HassCommunicator.Companion.EVENT_WS_SUBSCRIPTION
import org.openbase.bco.gateway.homeassistant.communication.websocket.command.SubscriptionEvent
import org.openbase.bco.gateway.homeassistant.manager.HassDeviceManager.Companion.ALIAS_KEY_BCO_ICON
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
import org.openbase.bco.gateway.homeassistant.sync.strategy.ZoneSyncStrategy.Companion.DEFAULT_HASS_ROOT_LOCATION_ID
import org.openbase.bco.gateway.homeassistant.type.HassType
import org.openbase.bco.gateway.homeassistant.util.get
import org.openbase.bco.gateway.homeassistant.util.set
import org.openbase.bco.registry.lib.util.UnitConfigProcessor
import org.openbase.bco.registry.remote.Registries
import org.openbase.bco.registry.unit.lib.UnitRegistry
import org.openbase.jul.extension.type.processing.LabelProcessor
import org.openbase.jul.pattern.Observer
import org.openbase.jul.pattern.provider.DataProvider
import org.openbase.type.domotic.registry.UnitRegistryDataType.UnitRegistryData
import org.openbase.type.domotic.unit.UnitConfigType.UnitConfig
import org.openbase.type.domotic.unit.UnitTemplateType.UnitTemplate.UnitType
import org.openbase.type.domotic.unit.location.LocationConfigType.LocationConfig.LocationType
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
class DalUnitSyncStrategy(
    private val deviceCache: DtoCache<HassDeviceDto>,
    private val areaCache: DtoCache<HassAreaDto>,
    private val hassCommunicator: HassCommunicator = HassCommunicator.instance,
    override val unitRegistry: UnitRegistry = Registries.getUnitRegistry(),
) : UnitSyncStrategy<HassEntityDto, HassEntityInputDto> {

    override val dependencies = listOf(deviceCache, areaCache)
    override val unitType: UnitType = UnitType.UNKNOWN
    override val hassType: HassType = HassType.ENTITY
    override val unitFilter: (UnitConfig) -> Boolean = {
        UnitConfigProcessor.isDalUnit(it)
                && it.metaConfig.entryList.any { entry -> entry.key == ALIAS_KEY_HASS_ENTITY_ID }
                && it.metaConfig.entryList.any { entry -> entry.key == ALIAS_KEY_HASS_ID }
                && it.metaConfig[ALIAS_KEY_HASS_TYPE] == hassType.name
    }

    fun lookupDalUnitConfigOfDevice(hassDto: HassEntityDto): UnitConfig? {
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
        return deviceUnitConfig.deviceConfig.unitIdList
            .asSequence()
            .mapNotNull { dalUnitId ->
                runCatching { unitRegistry.getUnitConfigById(dalUnitId) }.getOrNull()
            }
            .firstOrNull { dalUnit ->
                val templateMetaConfig = runCatching {
                    Registries.getTemplateRegistry().getUnitTemplateByType(dalUnit.unitType).metaConfig
                }.getOrNull() ?: return@firstOrNull false

                val entityType = templateMetaConfig[HASS_ENTITY_TYPE] ?: return@firstOrNull false
                val entityDeviceClass = templateMetaConfig[HASS_ENTITY_DEVICE_CLASS]

                val matchingEntity = deviceEntities
                    .filter { entityIdToDeviceClass[it.entityId] == entityDeviceClass }
                    .find { it.type == entityType }

                matchingEntity?.entityId == hassDto.entityId
            }
    }

    override fun buildUnitConfig(hassDto: HassEntityDto): UnitConfig {
        val dalUnitConfigBuilder: UnitConfig.Builder? = lookupDalUnitConfigOfDevice(hassDto)
            ?.toBuilder()

        return dalUnitConfigBuilder
            ?.apply {
                if (hassDto.entityId !in aliasList) {
                    addAlias(hassDto.entityId)
                }
                metaConfigBuilder[ALIAS_KEY_HASS_ENTITY_ID] = hassDto.entityId
                areaCache.getUnitConfigByDtoId(hassDto.areaId)
                    ?.let { unitLocation ->
                        placementConfigBuilder.locationId = unitLocation.id
                    }
                hassDto.icon?.let { metaConfigBuilder[ALIAS_KEY_BCO_ICON] = it }
            }
            // TODO: What do we set here when name is null?
            ?.apply { hassDto.name?.let { setLabel(LabelProcessor.generateLabelBuilder(it)) } }
            ?.link(hassDto)
            ?.build()
            ?: UnitConfig.getDefaultInstance()
    }

    override fun buildHassInputDto(unitConfig: UnitConfig): HassEntityInputDto = HassEntityInputDto(
        entityId = unitConfig.metaConfig[ALIAS_KEY_HASS_ENTITY_ID],
        areaId = areaCache.getDtoByUnitId(unitConfig.placementConfig.locationId)?.id,
        icon = unitConfig.metaConfig[ALIAS_KEY_BCO_ICON],
        name = runCatching { LabelProcessor.getBestMatch(unitConfig.label) }.getOrNull(),
    )

    override fun saveHassDto(dto: HassEntityInputDto): HassEntityDto =
        hassCommunicator.saveEntity(dto)

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
        private val LOGGER = LoggerFactory.getLogger(DalUnitSyncStrategy::class.java)
    }
}
