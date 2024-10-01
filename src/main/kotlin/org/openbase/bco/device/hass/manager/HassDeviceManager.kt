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

import org.example.org.openbase.bco.device.hass.execution.ServiceActionExecutor
import org.example.org.openbase.bco.device.hass.manager.unit.HassGatewayControllerFactory
import org.openbase.bco.dal.control.layer.unit.device.DeviceManagerImpl
import org.openbase.bco.dal.lib.layer.unit.UnitController
import org.openbase.bco.device.hass.communication.HassCommunicator
import org.openbase.bco.device.hass.manager.dto.HassDeviceDto
import org.openbase.bco.registry.remote.Registries
import org.openbase.bco.registry.remote.login.BCOLogin
import org.openbase.jul.exception.CouldNotPerformException
import org.openbase.jul.exception.printer.ExceptionPrinter
import org.openbase.jul.extension.protobuf.ProtoBufBuilderProcessor.mergeFromWithoutRepeatedFields
import org.openbase.jul.extension.type.processing.LabelProcessor
import org.openbase.jul.extension.type.processing.MetaConfigProcessor
import org.openbase.jul.iface.Launchable
import org.openbase.jul.iface.VoidInitializable
import org.openbase.jul.pattern.Observer
import org.openbase.jul.pattern.provider.DataProvider
import org.openbase.jul.schedule.RecurrenceEventFilter
import org.openbase.type.domotic.unit.UnitConfigType.UnitConfig
import org.openbase.type.domotic.unit.device.DeviceClassType.DeviceClass
import org.openbase.type.domotic.unit.location.LocationConfigType.LocationConfig.LocationType
import org.openbase.type.domotic.unit.UnitTemplateType.UnitTemplate.UnitType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

class HassDeviceManager : DeviceManagerImpl(HassGatewayControllerFactory(), false),
    Launchable<Void>, VoidInitializable {
    private val serviceActionExecutor: ServiceActionExecutor

    /**
     * Synchronization observer that triggers resynchronization of all units if their configuration changes.
     */
    private val synchronizationObserver: Observer<DataProvider<MutableMap<String, UnitController<*, *>>>, MutableMap<String, UnitController<*, *>>>
    private val unitChangeSynchronizationFilter: RecurrenceEventFilter<Any>

    val unitFilter:RecurrenceEventFilter<Any> get() = unitChangeSynchronizationFilter
    val executor:ServiceActionExecutor get() = serviceActionExecutor

    init {
        // the sync observer triggers a lot when the device manager is initially activated and all unit controllers are created
        // thus add an event filter
        this.unitChangeSynchronizationFilter = object : RecurrenceEventFilter<Any>(5000) {
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

                val deviceClasses = Registries.getClassRegistry(true).deviceClasses
                val deviceClassMapping: Map<String, Pair<HassDeviceDto, DeviceClass>> = HassCommunicator.instance.getDevices()
                    .map { hassDevice -> hassDevice to deviceClasses
                        .find { deviceClass ->
                            deviceClass.productNumber
                                .split(",")
                                .map(String::trim)
                        .contains(hassDevice.model) } }
                    .filter { (_, deviceClass) -> deviceClass != null }
                    .map { (hassDevice, deviceClass) -> hassDevice to deviceClass!! }
                    .onEach { (hassDevice, deviceClass) -> println("found compatible device: $hassDevice served by ${LabelProcessor.getBestMatch(deviceClass.label)}") }
                    .associateBy { (hassDevice, _) -> hassDevice.id }

                // ======= SYNC FlOORS ========

                val ALIAS_KEY_HASS_FLOOR_ID = "HASS_FLOOR_ID"

                val floorIdToZones = Registries.getUnitRegistry()
                    .getUnitConfigsByUnitType(UnitType.LOCATION)
                    .filter { it.metaConfig.entryList.any { it.key == ALIAS_KEY_HASS_FLOOR_ID} }
                    .associateBy { MetaConfigProcessor.getValue(it.metaConfig, ALIAS_KEY_HASS_FLOOR_ID) }

                HassCommunicator.instance.getFloors()
                    .map { floor ->
                        UnitConfig.newBuilder()
                            .setUnitType(UnitType.LOCATION)
                            .apply {
                                locationConfigBuilder
                                    .setLocationType(LocationType.ZONE)
                            }
                            .setLabel(LabelProcessor.generateLabelBuilder(floor.name))
                            .apply { MetaConfigProcessor.setValue(metaConfigBuilder, ALIAS_KEY_HASS_FLOOR_ID, floor.id) }
                            .build()
                    }.map { zoneConfig ->
                        floorIdToZones[MetaConfigProcessor.getValue(zoneConfig.metaConfig, ALIAS_KEY_HASS_FLOOR_ID)]?.let { existingZoneConfig ->
                            existingZoneConfig.toBuilder().mergeFrom(zoneConfig).build().let {
                                Registries.getUnitRegistry().updateUnitConfig(it).get(30, TimeUnit.SECONDS)
                            }
                        } ?: Registries.getUnitRegistry().registerUnitConfig(zoneConfig).get(30, TimeUnit.SECONDS)
                    }

                // ======= SYNC AREAS ========
                val ALIAS_KEY_HASS_AREA_ID = "HASS_AREA_ID"
                val areaIdToTiles = Registries.getUnitRegistry()
                    .getUnitConfigsByUnitType(UnitType.LOCATION)
                    .filter { it.metaConfig.entryList.any { it.key == ALIAS_KEY_HASS_AREA_ID} }
                    .associateBy { MetaConfigProcessor.getValue(it.metaConfig, ALIAS_KEY_HASS_AREA_ID) }

                HassCommunicator.instance.getAreas()
                    .map { area ->
                        UnitConfig.newBuilder()
                            .setUnitType(UnitType.LOCATION)
                            .apply {
                                locationConfigBuilder
                                    .setLocationType(LocationType.TILE)
                            }
                            .setLabel(LabelProcessor.generateLabelBuilder(area.name))
                            .apply { MetaConfigProcessor.setValue(metaConfigBuilder, ALIAS_KEY_HASS_AREA_ID, area.id) }
                            .build()
                    }.map { tileConfig ->
                        areaIdToTiles[MetaConfigProcessor.getValue(tileConfig.metaConfig, ALIAS_KEY_HASS_AREA_ID)]?.let { existingTileConfig ->
                            existingTileConfig.toBuilder().mergeFromWithoutRepeatedFields(tileConfig).build().let {
                                Registries.getUnitRegistry().updateUnitConfig(it).get(30, TimeUnit.SECONDS)
                            }
                        } ?: Registries.getUnitRegistry().registerUnitConfig(tileConfig).get(30, TimeUnit.SECONDS)
                    }

                // ======= SYNC DEVICES ========

                val ALIAS_KEY_HASS_DEVICE_ID = "HASS_DEVICE_ID"
                val deviceIdToDevices = Registries.getUnitRegistry()
                    .getUnitConfigsByUnitType(UnitType.DEVICE)
                    .filter { it.metaConfig.entryList.any { it.key == ALIAS_KEY_HASS_DEVICE_ID} }
                    .associateBy { MetaConfigProcessor.getValue(it.metaConfig, ALIAS_KEY_HASS_DEVICE_ID) }

                HassCommunicator.instance.getDevices()
                    .filter { device -> deviceClassMapping[device.id] != null }
                    .map { device ->
                        UnitConfig.newBuilder()
                            .setUnitType(UnitType.DEVICE)
                            .apply {
                                deviceClassMapping[device.id]!!.let { (_, deviceClass) ->
                                    deviceConfigBuilder
                                        .setDeviceClassId(deviceClass.id)
                                }
                            }
                            .setLabel(LabelProcessor.generateLabelBuilder(device.name))
                            .apply { MetaConfigProcessor.setValue(metaConfigBuilder, ALIAS_KEY_HASS_DEVICE_ID, device.id) }
                            .build()
                    }.map { deviceConfig ->
                        deviceIdToDevices[MetaConfigProcessor.getValue(deviceConfig.metaConfig, ALIAS_KEY_HASS_DEVICE_ID)]?.let { existingDeviceConfig ->
                            existingDeviceConfig.toBuilder().mergeFromWithoutRepeatedFields(deviceConfig).build().let {
                                Registries.getUnitRegistry().updateUnitConfig(it).get(30, TimeUnit.SECONDS)
                            }
                        } ?: Registries.getUnitRegistry().registerUnitConfig(deviceConfig).get(30, TimeUnit.SECONDS)
                    }


                // TODO: Location and Device initial sync draft is ready, however we have issues with repeated field that are not merged correctly.
                // TODO: Implement Service Mapping BCO -> HASS (COLORABLE LIGHT)
                // TODO: Implement Service Mapping HASS -> BCO (COLORABLE LIGHT)



//
//                try {
//                    for (entity in HassCommunicator.instance.entities) {
//                        try {
//                            executor.applyStateUpdate(entity.entityId, entity.type, entity.state, true)
//                        } catch (ex: CouldNotPerformException) {
//                            ExceptionPrinter.printHistory(
//                                ((("Skip synchronization of item[name:" + entity.name + ", type:" + entity.type) + ", " + ", state:" + entity.state))+ "]",
//                                ex,
//                                LOGGER,
//                                LogLevel.WARN
//                            )
//                        }
//                    }
//                } catch (ex: CouldNotPerformException) {
//                    if (!isCausedBySystemShutdown(ex)) {
//                        ExceptionPrinter.printHistory(
//                            "Could not retrieve item states from hass!",
//                            ex,
//                            LOGGER,
//                            LogLevel.WARN
//                        )
//                    }
//                }
            }
        }
        this.serviceActionExecutor = ServiceActionExecutor(unitControllerRegistry)
        this.synchronizationObserver =
            (Observer { observable: Any?, value: Any? -> unitChangeSynchronizationFilter.trigger() })
    }

    override fun isUnitSupported(config: UnitConfig): Boolean {
        val deviceClass: DeviceClass
        try {
            try {
                deviceClass = Registries.getClassRegistry(true).getDeviceClassById(config.deviceConfig.deviceClassId)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        } catch (e: CouldNotPerformException) {
            return false
        }
        if (deviceClass.bindingConfig.bindingId != "HASS") {
            return false
        }

        return super.isUnitSupported(config)
    }

    @Throws(CouldNotPerformException::class, InterruptedException::class)
    override fun activate() {
        unitControllerRegistry.addObserver(synchronizationObserver)
        super.activate()

        LOGGER.info("Connect to bco...")
        Registries.waitUntilReady()
        LOGGER.info("Login to bco...")
        BCOLogin.getSession().loginBCOUser()

//        HassCommunicator.instance.addWEBSOCKETObserver(serviceActionExecutor, ENTITY_STATE_TOPIC_FILTER)
        unitChangeSynchronizationFilter.trigger()
    }

    @Throws(CouldNotPerformException::class, InterruptedException::class)
    override fun deactivate() {
        unitControllerRegistry.removeObserver(synchronizationObserver)
//        HassCommunicator.getInstance().removeWEBSOCKETObserver(serviceActionExecutor, ITEM_STATE_TOPIC_FILTER)
        super.deactivate()
    }

    companion object {
//        const val ITEM_STATE_TOPIC_FILTER: String = "hass/items/(.+)/state"

        private val LOGGER: Logger = LoggerFactory.getLogger(HassDeviceManager::class.java)
    }
}
