package org.openbase.bco.device.hass.util

data class ListDiff<A>(
    val added: List<A> = emptyList(),
    val updated: List<A> = emptyList(),
    val removed: List<A> = emptyList(),
)

/**
 * Compares this list against another list of [IdProvider]s and returns a [ListDiff] containing the added,
 * updated and removed elements.
 */
fun <A : IdProvider<out IdProvider<*>?>> List<A>?.toListDiff(others: List<A>?): ListDiff<out A> =
    diff(others) { it }

/**
 * Compares this list against another list of [IdProvider]s and returns a [ListDiff] containing the added,
 * updated and removed elements.
 */
fun <A : IdProvider<*>, O> List<A>?.diff(others: List<A>?, block: (ListDiff<A>) -> O): O  = run {

    val originIds = this?.map { it.id } ?: emptyList()
    val otherIds = others?.map { it.id } ?: emptyList()

    val added: List<A> = others
        ?.filter { it.id !in originIds }
        ?: emptyList()
    val updated: List<A> = this
        ?.filter { element -> element.id in otherIds && this.find { it.id == element.id  } != others?.find { it.id == element.id }}
        ?: emptyList()
    val removed: List<A> = this
        ?.filter { it.id !in otherIds }
        ?: emptyList()
    ListDiff(added, updated, removed)
}.let { block(it) }
