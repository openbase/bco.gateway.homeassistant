package org.openbase.bco.gateway.homeassistant.util

import java.time.Duration
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

fun <T> Future<T>.get(duration: Duration): T = this.get(duration.toMillis(), TimeUnit.MILLISECONDS)

fun <T> Future<T>.await(duration: Duration = Duration.ofSeconds(5)): T = get(duration)
