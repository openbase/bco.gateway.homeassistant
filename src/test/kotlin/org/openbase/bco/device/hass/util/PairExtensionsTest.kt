package org.openbase.bco.device.hass.util

import org.junit.jupiter.api.Test

class PairExtensionsTest {
    @Test
    fun `should map first element`() {
        val list = listOf("a" to 1, "b" to 2)
        val result = list.mapFirst { it.first.uppercase() }
        assert(result == listOf("A" to 1, "B" to 2))
    }

    @Test
    fun `should map second element`() {
        val list = listOf("a" to 1, "b" to 2)
        val result = list.mapSecond { it.second + 1 }
        assert(result == listOf("a" to 2, "b" to 3))
    }

    @Test
    fun `should invert elements`() {
        val list = listOf("a" to 1, "b" to 2)
        val result = list.mapInverted()
        assert(result == listOf(1 to "a", 2 to "b"))
    }

    @Test
    fun `should map first and remove null values`() {
        val list = listOf("a" to 1, "b" to 2, null to 3)
        val result = list.mapFirstNotNull { it.first?.uppercase() }
        assert(result == listOf("A" to 1, "B" to 2))
    }

    @Test
    fun `should map second and remove null values`() {
        val list = listOf("a" to 1, "b" to 2, "c" to null)
        val result = list.mapSecondNotNull { it.second?.plus(1) }
        assert(result == listOf("a" to 2, "b" to 3))
    }

    @Test
    fun `should filter null keys`() {
        val list = listOf("a" to 1, "b" to 2, null to 3)
        val result = list.filterFirstNotNull()
        assert(result == listOf("a" to 1, "b" to 2))
    }

    @Test
    fun `should filter null values`() {
        val list = listOf("a" to 1, "b" to 2, "c" to null)
        val result = list.filterSecondNotNull()
        assert(result == listOf("a" to 1, "b" to 2))
    }
}
