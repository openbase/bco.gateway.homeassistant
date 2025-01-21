package org.openbase.bco.device.hass.util

import java.time.Duration
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

fun <T> Future<T>.get(duration: Duration): T = this.get(duration.toMillis(), TimeUnit.MILLISECONDS)

fun <T> Future<T>.await(): T = get(Duration.ofSeconds(5))