package org.openbase.bco.gateway.homeassistant.util

import java.time.Duration
import java.time.Instant

object Infinity {
    val instant: Instant get()  = Instant.parse("9999-12-31T23:59:59.999999999Z")
    val duration: Duration get()  = Duration.between(Instant.EPOCH, instant)
}
