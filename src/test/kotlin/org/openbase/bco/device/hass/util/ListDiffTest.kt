package org.openbase.bco.device.hass.util

import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldContain
import org.junit.jupiter.api.Test

class ListDiffTest {
    data class TestData(
        override val id: Int,
        val data: String = "TestData",
    ) : IdProvider<Int>

    @Test
    fun testListDiffCombination() {
        val origin = listOf(
            TestData(1),
            TestData(2),
            TestData(3),
            TestData(4),
            TestData(5),
        )

        val updatedList = listOf(
            TestData(0),
            TestData(2,"TestData2"),
            TestData(3,"TestData2"),
            TestData(7),
            TestData(8),
        )

        origin.diff(updatedList) {
            it.added shouldContain TestData(0)
            it.added shouldContain TestData(7)
            it.added shouldContain TestData(8)
            it.added.size `should be equal to` 3

            it.updated shouldContain TestData(2)
            it.updated shouldContain TestData(3)
            it.updated.size `should be equal to` 2

            it.removed shouldContain TestData(1)
            it.removed shouldContain TestData(4)
            it.removed shouldContain TestData(5)
            it.removed.size `should be equal to` 3
        }
    }

    @Test
    fun testListDiffWithNulls() {
        val origin = listOf(
            TestData(1),
            TestData(2),
            TestData(3),
            TestData(4),
            TestData(5),
        )

        val updatedList = listOf<TestData>(
            TestData(2, "TestData2"),
            TestData(3,"TestData2"),
            TestData(8,"TestData2"),
        )

        origin.diff(updatedList) {
            it.added shouldContain TestData(8,"TestData2")
            it.added.size `should be equal to` 1

            it.updated shouldContain TestData(2)
            it.updated shouldContain TestData(3)
            it.updated.size `should be equal to` 2

            it.removed shouldContain TestData(1)
            it.removed shouldContain TestData(4)
            it.removed shouldContain TestData(5)
            it.removed.size `should be equal to` 3
        }
    }

    @Test
    fun testListDiffWithNullsAndEmptyLists() {
        val origin = emptyList<TestData>()

        val updatedList = listOf<TestData>(
            TestData(2),
            TestData(3),
            TestData(8),
        )

        origin.diff(updatedList) {
            it.added shouldContain TestData(2)
            it.added shouldContain TestData(3)
            it.added shouldContain TestData(8)
            it.added.size `should be equal to` 3

            it.updated.size `should be equal to` 0

            it.removed.size `should be equal to` 0
        }
    }

    @Test
    fun testListDiffWithRemovals() {
        val origin = listOf(
            TestData(1),
            TestData(2),
            TestData(3),
            TestData(4),
            TestData(5),
        )

        val updatedList = emptyList<TestData>()

        origin.diff(updatedList) {
            it.added.size `should be equal to` 0

            it.updated.size `should be equal to` 0

            it.removed shouldContain TestData(1)
            it.removed shouldContain TestData(2)
            it.removed shouldContain TestData(3)
            it.removed shouldContain TestData(4)
            it.removed shouldContain TestData(5)
            it.removed.size `should be equal to` 5
        }
    }

    @Test
    fun testListDiffWithEmptyLists() {
        val origin = emptyList<TestData>()
        val updatedList = emptyList<TestData>()

        origin.diff(updatedList) {
            it.added.size `should be equal to` 0
            it.updated.size `should be equal to` 0
            it.removed.size `should be equal to` 0
        }
    }

    @Test
    fun testListDiffWithUpdateOnly() {
        val origin = listOf(
            TestData(1),
            TestData(2),
            TestData(3),
            TestData(4),
            TestData(5),
        )

        val updatedList = listOf<TestData>(
            TestData(1,"TestData2"),
            TestData(2,"TestData2"),
            TestData(3,"TestData2"),
            TestData(4,"TestData2"),
            TestData(5,"TestData2"),
        )

        origin.diff(updatedList) {
            it.added.size `should be equal to` 0

            it.updated shouldContain TestData(1)
            it.updated shouldContain TestData(2)
            it.updated shouldContain TestData(3)
            it.updated shouldContain TestData(4)
            it.updated shouldContain TestData(5)
            it.updated.size `should be equal to` 5

            it.removed.size `should be equal to` 0
        }
    }

    @Test
    fun testListDiffWithAddOnly() {
        val origin = listOf(
            TestData(1),
            TestData(2),
            TestData(3),
            TestData(4),
            TestData(5),
        )

        val updatedList = listOf<TestData>(
            TestData(0),
            TestData(1),
            TestData(2),
            TestData(3),
            TestData(4),
            TestData(5),
            TestData(6),
        )

        origin.diff(updatedList) {
            it.added shouldContain TestData(0)
            it.added shouldContain TestData(6)
            it.added.size `should be equal to` 2

            it.updated.size `should be equal to` 0

            it.removed.size `should be equal to` 0
        }
    }

    fun testListDiffWithAllRemovedOrUpdated() {
        val origin = listOf(
            TestData(1),
            TestData(2),
            TestData(3),
            TestData(4),
            TestData(5),
        )

        val updatedList = listOf<TestData>(
            TestData(2),
            TestData(3),
            TestData(4),
            TestData(5),
        )

        origin.diff(updatedList) {
            it.added.size `should be equal to` 0

            it.updated shouldContain TestData(2)
            it.updated shouldContain TestData(3)
            it.updated shouldContain TestData(4)
            it.updated shouldContain TestData(5)
            it.updated.size `should be equal to` 4

            it.removed shouldContain TestData(1)
            it.removed.size `should be equal to` 1
        }
    }

    fun testListDiffWithWipeOut() {
        val origin = listOf(
            TestData(1),
            TestData(2),
            TestData(3),
            TestData(4),
            TestData(5),
        )

        val updatedList = listOf<TestData>()

        origin.diff(updatedList) {
            it.added.size `should be equal to` 0

            it.updated.size `should be equal to` 0

            it.removed shouldContain TestData(1)
            it.removed shouldContain TestData(2)
            it.removed shouldContain TestData(3)
            it.removed shouldContain TestData(4)
            it.removed shouldContain TestData(5)
            it.removed.size `should be equal to` 5
        }
    }

    @Test
    fun testListDiffNoChange() {
        val origin = listOf(
            TestData(1),
            TestData(2),
            TestData(3),
            TestData(4),
            TestData(5),
        )

        val updatedList = listOf<TestData>(
            TestData(1),
            TestData(2),
            TestData(3),
            TestData(4),
            TestData(5),
        )

        origin.diff(updatedList) {
            it.added.size `should be equal to` 0
            it.updated.size `should be equal to` 0
            it.removed.size `should be equal to` 0
        }
    }
}
