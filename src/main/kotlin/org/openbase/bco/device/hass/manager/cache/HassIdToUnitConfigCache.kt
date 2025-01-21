package org.openbase.bco.device.hass.manager.cache

import org.openbase.bco.device.hass.manager.HassDeviceManager.Companion.ALIAS_KEY_HASS_ENTITY_ID
import org.openbase.type.domotic.unit.UnitConfigType
import org.openbase.bco.device.hass.util.get
import kotlin.concurrent.write

class HassIdToUnitConfigCache : BasicCache<String, UnitConfigType.UnitConfig>() {
    fun addEntry(unitConfig: UnitConfigType.UnitConfig) = mapLock.write {
        unitConfig.metaConfig[ALIAS_KEY_HASS_ENTITY_ID]?.let { hassId ->
            addEntry(hassId, unitConfig)
        }
    }
}