package org.openbase.bco.gateway.homeassistant.util

import org.openbase.bco.registry.unit.lib.UnitRegistry
import org.openbase.type.domotic.unit.UnitConfigType
import java.util.concurrent.Future

fun UnitRegistry.saveUnitConfig(unitConfig: UnitConfigType.UnitConfig): Future<UnitConfigType.UnitConfig> =
    unitConfig.id
        ?.takeIf { containsUnitConfigById(it) }
        ?.let { updateUnitConfig(unitConfig) }
        ?: registerUnitConfig(unitConfig)
