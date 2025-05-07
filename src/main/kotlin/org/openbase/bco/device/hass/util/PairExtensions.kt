package org.openbase.bco.device.hass.util


fun <A, B, C> List<Pair<A, B>>.mapFirst(transform: (Pair<A, B>) -> C): List<Pair<C, B>> =
    map { transform(it) to it.second }

fun <A, B, C> List<Pair<A, B>>.mapSecond(transform: (Pair<A, B>) -> C): List<Pair<A, C>> =
    map { it.first to transform(it) }

fun <A, B> List<Pair<A, B>>.mapInverted(): List<Pair<B, A>> =
    map { (first, second) -> second to first }
