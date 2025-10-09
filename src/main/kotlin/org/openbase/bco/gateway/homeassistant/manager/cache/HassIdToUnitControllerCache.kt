package org.openbase.bco.gateway.homeassistant.manager.cache

import org.openbase.bco.dal.lib.layer.unit.UnitController
import org.openbase.bco.dal.lib.layer.unit.UnitControllerRegistry
import org.openbase.bco.gateway.homeassistant.manager.HassDeviceManager.Companion.ALIAS_KEY_HASS_ENTITY_ID
import org.openbase.bco.gateway.homeassistant.util.get
import kotlin.concurrent.write

class HassIdToUnitControllerCache(
    private val deviceControllerRegistry: UnitControllerRegistry<UnitController<*, *>>,
) : BasicCache<String, UnitController<*, *>>() {

    init {
        deviceControllerRegistry.addObserver { _, _ -> updateCache() }
        updateCache()
    }

    private fun updateCache() {
        deviceControllerRegistry.entries.forEach { addEntry(it) }
    }

    private fun addEntry(unitController: UnitController<*, *>) = mapLock.write {
        unitController.config.metaConfig[ALIAS_KEY_HASS_ENTITY_ID]?.let { hassId ->
            addEntry(hassId, unitController)
        }
    }
}
