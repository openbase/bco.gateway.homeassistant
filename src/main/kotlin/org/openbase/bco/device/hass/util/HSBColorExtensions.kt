package org.openbase.bco.device.hass.util

fun Double.toHassHue(): Double = this

fun Double.toBCOHue(): Double = this

fun Double.toHassSaturation(): Double = this * 100

fun Double.toBCOSaturation(): Double = this / 100

fun Double.toHassBrightness(): Double = this * 255

fun Double.toBCOBrightness(): Double = this / 255
