package org.openbase.bco.device.hass.manager /*-
 * #%L
 * BCO Hass Device Manager
 * %%
 * Copyright (C) 2015 - 2021 openbase.org
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
import org.openbase.bco.dal.control.layer.unit.device.DeviceManagerImpl
import org.openbase.bco.dal.lib.layer.unit.UnitController
import org.openbase.bco.device.hass.action.ServiceActionExecutor
import org.openbase.bco.device.hass.communication.HassCommunicator
import org.openbase.bco.device.hass.communication.HassCommunicator.Companion.EVENT_WS_SUBSCRIPTION
import org.openbase.bco.device.hass.manager.cache.HassIdToUnitControllerCache
import org.openbase.bco.device.hass.manager.dto.HassDeviceDto
import org.openbase.bco.device.hass.manager.dto.HassEntityDto
import org.openbase.bco.device.hass.manager.dto.HassStateDto
import org.openbase.bco.device.hass.manager.service.location.LocationSynchronizer
import org.openbase.bco.device.hass.manager.unit.HassGatewayControllerFactory
import org.openbase.bco.device.hass.util.await
import org.openbase.bco.device.hass.util.get
import org.openbase.bco.device.hass.util.isNotNull
import org.openbase.bco.device.hass.util.set
import org.openbase.bco.registry.remote.Registries
import org.openbase.bco.registry.remote.login.BCOLogin
import org.openbase.jul.exception.CouldNotPerformException
import org.openbase.jul.exception.ExceptionProcessor
import org.openbase.jul.exception.printer.ExceptionPrinter
import org.openbase.jul.exception.printer.LogLevel
import org.openbase.jul.extension.protobuf.ProtoBufBuilderProcessor.mergeFromWithoutRepeatedFields
import org.openbase.jul.extension.type.processing.LabelProcessor
import org.openbase.jul.iface.Launchable
import org.openbase.jul.iface.VoidInitializable
import org.openbase.jul.pattern.Observer
import org.openbase.jul.pattern.provider.DataProvider
import org.openbase.jul.schedule.RecurrenceEventFilter
import org.openbase.type.domotic.unit.UnitConfigType.UnitConfig
import org.openbase.type.domotic.unit.UnitTemplateType.UnitTemplate.UnitType
import org.openbase.type.domotic.unit.device.DeviceClassType.DeviceClass
import org.openbase.type.domotic.unit.location.LocationConfigType.LocationConfig.LocationType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.collections.filter

class HassDeviceManager :
    DeviceManagerImpl(HassGatewayControllerFactory(), false),
    Launchable<Void>,
    VoidInitializable {
    private val executor: ServiceActionExecutor

    private val hassIdToUnitControllerCache: HassIdToUnitControllerCache = HassIdToUnitControllerCache(unitControllerRegistry)

    /**
     * Synchronization observer that triggers resynchronization of all units if their configuration changes.
     */
    private val synchronizationObserver:
        Observer<DataProvider<MutableMap<String, UnitController<*, *>>>, MutableMap<String, UnitController<*, *>>>
    private lateinit var unitFilter: RecurrenceEventFilter<Any>

    private var entityIdToUnitId = mapOf<String, String>()

    private var supportedEntities = listOf<HassEntityDto>()

    private val locationSynchronizer = LocationSynchronizer()

    init {
        // the sync observer triggers a lot when the device manager is initially activated and all unit controllers are created
        this.unitFilter =
            object : RecurrenceEventFilter<Any>(5000) {
                @Throws(InterruptedException::class)
                override fun relay() {
                    // skip update if hass is not available but trigger again so the sync is performed later on.

                    if (!HassCommunicator.instance.isConnected) {
                        try {
                            unitFilter.triggerDelayed()
                        } catch (ex: CouldNotPerformException) {
                            ExceptionPrinter.printHistory("Could not delay unit sync task!", ex, LOGGER)
                        }
                        return
                    }

                    val notIdentifiedDevices = mutableListOf<HassDeviceDto>()
                    val deviceClasses = Registries.getClassRegistry(true).deviceClasses
                    val deviceClassMapping: Map<String, Pair<HassDeviceDto, DeviceClass>> =
                        HassCommunicator.instance
                            .getDevices()
                            .filter { !it.model.isNullOrBlank() }
                            .map { hassDevice ->
                                hassDevice to
                                    deviceClasses
                                        .find { deviceClass ->
                                            deviceClass.productNumber == hassDevice.model ||
                                                deviceClass.metaConfig[ALIAS_KEY_HASS_DEVICE_MODEL] == hassDevice.model
                                        }
                            }.filter { (hassDevice, deviceClass) ->
                                (deviceClass.isNotNull())
                                    .also {
                                        if (!it) {
                                            notIdentifiedDevices.add(hassDevice)
                                        }
                                    }
                            }.map { (hassDevice, deviceClass) -> hassDevice to deviceClass!! }
                            .onEach { (hassDevice, deviceClass) ->
                                println("found compatible device: $hassDevice served by ${LabelProcessor.getBestMatch(deviceClass.label)}")
                            }.associateBy { (hassDevice, _) -> hassDevice.id }

                    val deviceIdToEntity: Map<String, List<HassEntityDto>> =
                        HassCommunicator.instance
                            .getEntities()
                            .groupBy { it.deviceId }

                    // ======= SYNC DEVICES ========
                    val deviceIdToDevices =
                        Registries
                            .getUnitRegistry()
                            .getUnitConfigsByUnitType(UnitType.DEVICE)
                            .filter { it.metaConfig.entryList.any { it.key == ALIAS_KEY_HASS_DEVICE_ID } }
                            .associateBy { it.metaConfig[ALIAS_KEY_HASS_DEVICE_ID] }

                    val dalUnitConfigs =
                        HassCommunicator.instance
                            .getDevices()
                            .filter { device -> deviceClassMapping[device.id].isNotNull() }
                            .map { device ->
                                UnitConfig
                                    .newBuilder()
                                    .setUnitType(UnitType.DEVICE)
                                    .apply {
                                        deviceClassMapping[device.id]!!.let { (_, deviceClass) ->
                                            deviceConfigBuilder.deviceClassId = deviceClass.id
                                        }
                                    }.setLabel(LabelProcessor.generateLabelBuilder(device.name))
                                    .apply { metaConfigBuilder[ALIAS_KEY_HASS_DEVICE_ID] = device.id }
                                    .apply {
                                        locationSynchronizer.findTileByAreaId(device.areaId)
                                            ?.let { unitLocation ->
                                                placementConfigBuilder.locationId = unitLocation.id
                                            }
                                    }.build()
                            }.map { deviceConfig ->
                                deviceIdToDevices[deviceConfig.metaConfig[ALIAS_KEY_HASS_DEVICE_ID]]?.let { existingDeviceConfig ->
                                    existingDeviceConfig.toBuilder().mergeFromWithoutRepeatedFields(deviceConfig).build().let {
                                        Registries.getUnitRegistry().updateUnitConfig(it).await()
                                    }
                                } ?: Registries.getUnitRegistry().registerUnitConfig(deviceConfig).await()
                            }.flatMap { deviceConfig ->
                                deviceConfig.deviceConfig.unitIdList.map { dalUnitId ->
                                    val unit = Registries.getUnitRegistry().getUnitConfigById(dalUnitId)
                                    val entityType =
                                        unit.unitType
                                            .let { Registries.getTemplateRegistry().getUnitTemplateByType(it) }
                                            .metaConfig.entryList
                                            .associate { it.key to it.value!! }[HASS_ENTITY_TYPE]

                                    deviceIdToEntity[deviceConfig.metaConfig[ALIAS_KEY_HASS_DEVICE_ID]]
                                        ?.find { it.type == entityType }
                                        ?.let { entity ->
                                            unit
                                                .toBuilder()
                                                .apply {
                                                    // set hass id as alias and add to meta config
                                                    addAlias(entity.entityId)
                                                    metaConfigBuilder[ALIAS_KEY_HASS_ENTITY_ID] = entity.entityId
                                                    // add location to unit
//                                                    locationSynchronizer.find(areaId) -.
                                                    locationSynchronizer.findTileByAreaId(entity.areaId)
                                                        ?.let { unitLocation ->
                                                            placementConfigBuilder.locationId = unitLocation.id
                                                        }
                                                }.build()
                                        }?.let { Registries.getUnitRegistry().updateUnitConfig(it).await() }
                                }
                            }.filterNotNull()

                    // TODO: Location and Device initial sync draft is ready, however we have issues with repeated field that are not merged correctly.
                    // TODO: Finish initial state mapping (there we have to map from the state type onto the service type by analysing the entire event)

                    // initial device synchronization
                    supportedEntities =
                        HassCommunicator.instance
                            .getEntities()
                            .filter { deviceIdToDevices.keys.contains(it.deviceId) }

                    try {
                        HassCommunicator.instance.getStates().applyStateUpdates(systemSync = true)
                    } catch (ex: CouldNotPerformException) {
                        if (!ExceptionProcessor.isCausedBySystemShutdown(ex)) {
                            ExceptionPrinter.printHistory(
                                "Could not retrieve item states from hass!",
                                ex,
                                LOGGER,
                                LogLevel.WARN,
                            )
                        }
                    }

                    notIdentifiedDevices
                        .takeIf { it.isNotEmpty() }
                        ?.also { println("The following devices are found but not yet compatible with bco:") }
                        ?.distinct()
                        ?.forEach { println("${it.name}: ${it.model}") }
                }
            }
        this.executor = ServiceActionExecutor(hassIdToUnitControllerCache)
        this.synchronizationObserver =
            (Observer { observable: Any?, value: Any? -> unitFilter.trigger() })
    }

    override fun isGatewaySupported(config: UnitConfig?): Boolean = config?.gatewayConfig?.gatewayClassId == HASS_GATEWAY_CLASS_ID

    override fun isUnitSupported(config: UnitConfig): Boolean = config.metaConfig.entryList.any { it.key == ALIAS_KEY_HASS_DEVICE_ID }

    @Throws(CouldNotPerformException::class, InterruptedException::class)
    override fun activate() {
        unitControllerRegistry.addObserver(synchronizationObserver)

        HassCommunicator.instance.subscribe(EVENT_WS_SUBSCRIPTION, HassCommunicator.HassEventType.STATE_UPDATE) { event ->
            LOGGER.info("new state event: $event")
            listOf(event.data.newState).applyStateUpdates(systemSync = false)
        }

        LOGGER.info("Connect to bco...")
        Registries.waitUntilReady()
        LOGGER.info("Login to bco...")

        BCOLogin.getSession().loginBCOUser()

        super.activate()

        locationSynchronizer.activate()
        unitFilter.trigger()
    }

    fun List<HassStateDto>.applyStateUpdates(systemSync: Boolean) =
        this
            .filter { supportedEntities.map { it.entityId }.contains(it.entityId) }
            .forEach { state ->
                try {
                    executor.applyStateUpdate(
                        hassState = state,
                        systemSync = systemSync,
                    )
                } catch (ex: CouldNotPerformException) {
                    ExceptionPrinter.printHistory(
                        "Skip synchronization of item[name: ${state.name}, type: ${state.type}, state: ${state.state}]",
                        ex,
                        LOGGER,
                        LogLevel.WARN,
                    )
                }
            }

    @Throws(CouldNotPerformException::class, InterruptedException::class)
    override fun deactivate() {
        unitControllerRegistry.removeObserver(synchronizationObserver)
        locationSynchronizer.deactivate()
        super.deactivate()
    }

    companion object {
        const val ALIAS_KEY_HASS_DEVICE_MODEL = "HASS_DEVICE_MODEL"
        const val ALIAS_KEY_HASS_FLOOR_ID = "HASS_FLOOR_ID"
        const val ALIAS_KEY_HASS_DEVICE_ID = "HASS_DEVICE_ID"
        const val HASS_GATEWAY_CLASS_ID = "96dd4c43-92de-48b6-ba16-f9bafefc3c44"
        const val HASS_ENTITY_TYPE = "HASS_ENTITY_TYPE"
        const val ALIAS_KEY_HASS_ENTITY_ID = "HASS_ENTITY_ID"
        const val ALIAS_KEY_HASS_AREA_ID = "HASS_AREA_ID"

        private val LOGGER: Logger = LoggerFactory.getLogger(HassDeviceManager::class.java)
    }
}
