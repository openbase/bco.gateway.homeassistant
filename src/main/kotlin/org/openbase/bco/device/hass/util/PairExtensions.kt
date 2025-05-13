package org.openbase.bco.device.hass.util


fun <FIRST, SECOND, FIRST_OUT> List<Pair<FIRST, SECOND>>.mapFirst(
    transform: (Pair<FIRST, SECOND>) -> FIRST_OUT
): List<Pair<FIRST_OUT, SECOND>> =
    map { transform(it) to it.second }

fun <FIRST, SECOND, SECOND_OUT> List<Pair<FIRST, SECOND>>.mapSecond(
    transform: (Pair<FIRST, SECOND>) -> SECOND_OUT
): List<Pair<FIRST, SECOND_OUT>> =
    map { it.first to transform(it) }

fun <FIRST, SECOND> List<Pair<FIRST, SECOND>>.mapInverted(): List<Pair<SECOND, FIRST>> =
    map { (first, second) -> second to first }
