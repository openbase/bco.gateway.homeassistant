package org.openbase.bco.gateway.homeassistant.sync.strategy

import org.openbase.bco.gateway.homeassistant.communication.HassCommunicator
import org.openbase.bco.gateway.homeassistant.communication.HassCommunicator.Companion.EVENT_WS_SUBSCRIPTION
import org.openbase.bco.gateway.homeassistant.communication.websocket.command.SubscriptionEvent
import org.openbase.bco.gateway.homeassistant.manager.HassDeviceManager.Companion.ALIAS_KEY_BCO_ICON
import org.openbase.bco.gateway.homeassistant.manager.HassDeviceManager.Companion.ALIAS_KEY_HASS_DEVICE_ID
import org.openbase.bco.gateway.homeassistant.manager.HassDeviceManager.Companion.ALIAS_KEY_HASS_DEVICE_MODEL
import org.openbase.bco.gateway.homeassistant.manager.HassDeviceManager.Companion.ALIAS_KEY_HASS_DEVICE_MODEL_ID
import org.openbase.bco.gateway.homeassistant.manager.HassDeviceManager.Companion.ALIAS_KEY_HASS_ID
import org.openbase.bco.gateway.homeassistant.manager.HassDeviceManager.Companion.ALIAS_KEY_HASS_TYPE
import org.openbase.bco.gateway.homeassistant.manager.dto.HassAreaDto
import org.openbase.bco.gateway.homeassistant.manager.dto.HassDeviceDto
import org.openbase.bco.gateway.homeassistant.manager.dto.HassDeviceInputDto
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
import org.openbase.type.domotic.unit.device.DeviceClassType
import org.slf4j.LoggerFactory

class DeviceSyncStrategy(
    private val areaCache: DtoCache<HassAreaDto>,
    private val hassCommunicator: HassCommunicator = HassCommunicator.instance,
    private val unitRegistry: UnitRegistry = Registries.getUnitRegistry(),
): UnitSyncStrategy<HassDeviceDto, HassDeviceInputDto> {
    override val dependencies = listOf(areaCache)
    override val unitType: UnitType = UnitType.DEVICE
    override val hassType: HassType = HassType.DEVICE
    override val unitFilter: (UnitConfig) -> Boolean = {
        it.metaConfig.entryList.any { entry -> entry.key == ALIAS_KEY_HASS_DEVICE_ID }
    }

    override fun buildUnitConfig(hassDto: HassDeviceDto): UnitConfig {
        val deviceClass = findMatchingDeviceClass(hassDto)
        return UnitConfig
            .newBuilder()
            .setUnitType(unitType)
            .apply {
                if (deviceClass != null) {
                    deviceConfigBuilder.deviceClassId = deviceClass.id
                }
            }
            .setLabel(LabelProcessor.generateLabelBuilder(hassDto.nameByUser ?: hassDto.name))
            .link(hassDto)
            .apply {
                areaCache.getUnitConfigByDtoId(hassDto.areaId)
                    ?.let { unitLocation ->
                        placementConfigBuilder.locationId = unitLocation.id
                    }
            }
            .apply { hassDto.icon?.let { metaConfigBuilder[ALIAS_KEY_BCO_ICON] = it } }
            .build()
    }

    override fun buildHassInputDto(unitConfig: UnitConfig): HassDeviceInputDto = HassDeviceInputDto(
        id = unitConfig.toHassId(),
        nameByUser = runCatching { LabelProcessor.getBestMatch(unitConfig.label) }.getOrNull(),
        name = null,
        labels = null,
        areaId = areaCache.getDtoByUnitId(unitConfig.placementConfig.locationId)?.id,
    )

    override fun saveHassDto(dto: HassDeviceInputDto): HassDeviceDto =
        hassCommunicator.saveDevice(dto)

    override fun deleteHassDto(dto: HassDeviceDto): HassDeviceDto = dto // Devices cannot be deleted via API

    override fun UnitConfig.Builder.link(hassDto: HassDeviceDto): UnitConfig.Builder = apply {
        metaConfigBuilder[ALIAS_KEY_HASS_DEVICE_ID] = hassDto.id
        metaConfigBuilder[ALIAS_KEY_HASS_ID] = hassDto.id
        metaConfigBuilder[ALIAS_KEY_HASS_TYPE] = hassType.name
    }

    override fun UnitConfig.toHassId(): String? = metaConfig[ALIAS_KEY_HASS_DEVICE_ID]
        ?: metaConfig[ALIAS_KEY_HASS_ID]

    override fun queryHassDtos(): List<HassDeviceDto> {
        val deviceClasses = Registries.getClassRegistry(true).deviceClasses
        return hassCommunicator.getDevices()
            .filter { !it.model.isNullOrBlank() }
            .filter { hassDevice -> findMatchingDeviceClass(hassDevice, deviceClasses) != null }
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

    private fun findMatchingDeviceClass(
        hassDevice: HassDeviceDto,
        deviceClasses: List<DeviceClassType.DeviceClass> = Registries.getClassRegistry(true).deviceClasses,
    ): DeviceClassType.DeviceClass? =
        deviceClasses.find { deviceClass -> identifyDeviceClass(deviceClass, hassDevice) }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(DeviceSyncStrategy::class.java)

        fun identifyDeviceClass(
            deviceClass: DeviceClassType.DeviceClass,
            hassDevice: HassDeviceDto,
        ): Boolean {
            deviceClass.metaConfig[ALIAS_KEY_HASS_DEVICE_MODEL]?.let { model ->
                deviceClass.metaConfig[ALIAS_KEY_HASS_DEVICE_MODEL_ID]?.let { modelId ->
                    if (model == hassDevice.model && modelId == hassDevice.modelId) {
                        return true
                    }
                }
            }

            deviceClass.metaConfig[ALIAS_KEY_HASS_DEVICE_MODEL_ID]?.let { model ->
                if (model == hassDevice.modelId) {
                    return true
                }
            }

            deviceClass.metaConfig[ALIAS_KEY_HASS_DEVICE_MODEL]?.let { model ->
                if (model == hassDevice.model) {
                    return true
                }
            }
            return deviceClass.productNumber == hassDevice.modelId
        }
    }
}
