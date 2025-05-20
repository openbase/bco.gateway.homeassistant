package org.openbase.bco.device.hass.sync

import org.openbase.jul.schedule.RecurrenceEventFilter

class DebounceFilter<VALUE>(val block: () -> Unit): RecurrenceEventFilter<VALUE>() {
    override fun relay() = block()
}