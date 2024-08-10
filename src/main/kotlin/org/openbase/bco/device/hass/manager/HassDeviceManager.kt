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
import org.openbase.bco.registry.remote.Registries
import org.openbase.jul.exception.CouldNotPerformException
import org.openbase.jul.exception.ExceptionProcessor.isCausedBySystemShutdown
import org.openbase.jul.exception.printer.ExceptionPrinter
import org.openbase.jul.exception.printer.LogLevel
import org.openbase.jul.iface.Launchable
import org.openbase.jul.iface.VoidInitializable
import org.openbase.jul.pattern.Observer
import org.openbase.jul.pattern.provider.DataProvider
import org.openbase.jul.schedule.RecurrenceEventFilter
import org.openbase.type.domotic.unit.UnitConfigType.UnitConfig
import org.openbase.type.domotic.unit.device.DeviceClassType.DeviceClass
import org.slf4j.Logger
import org.slf4j.LoggerFactory

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

                try {
                    for (entity in HassCommunicator.instance.entities) {
                        try {
                            executor.applyStateUpdate(entity.entityId, entity.type, entity.state, true)
                        } catch (ex: CouldNotPerformException) {
                            ExceptionPrinter.printHistory(
                                ((("Skip synchronization of item[name:" + entity.name + ", type:" + entity.type) + ", " + ", state:" + entity.state))+ "]",
                                ex,
                                LOGGER,
                                LogLevel.WARN
                            )
                        }
                    }
                } catch (ex: CouldNotPerformException) {
                    if (!isCausedBySystemShutdown(ex)) {
                        ExceptionPrinter.printHistory(
                            "Could not retrieve item states from hass!",
                            ex,
                            LOGGER,
                            LogLevel.WARN
                        )
                    }
                }
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
