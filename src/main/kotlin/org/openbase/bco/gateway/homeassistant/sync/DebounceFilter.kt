package org.openbase.bco.gateway.homeassistant.sync

import org.openbase.jul.schedule.RecurrenceEventFilter

class DebounceFilter<VALUE>(val block: () -> Unit): RecurrenceEventFilter<VALUE>() {
    override fun relay() = block()
}
