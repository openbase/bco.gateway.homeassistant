package org.openbase.bco.device.hass.manager.cache

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

abstract class BasicCache<KEY, VALUE> {
    val mapLock = ReentrantReadWriteLock()

    private val map: MutableMap<KEY, VALUE> = mutableMapOf()

    fun addEntry(key: KEY, value: VALUE) = mapLock.write {
        map[key] = value
    }

    fun getEntry(key: KEY): VALUE? = mapLock.read {
        map[key]
    }

    fun removeEntry(key: KEY): VALUE? = mapLock.write {
        map.remove(key)
    }
}