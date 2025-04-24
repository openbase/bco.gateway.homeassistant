package org.openbase.bco.device.hass.util


fun <A, B, C> List<Pair<A, B>>.mapFirst(transform: (A) -> C): List<Pair<C, B>> =
    map { (first, second) -> Pair(transform(first), second) }

fun <A, B, C> List<Pair<A, B>>.mapSecond(transform: (B) -> C): List<Pair<A, C>> =
    map { (first, second) -> Pair(first, transform(second)) }
