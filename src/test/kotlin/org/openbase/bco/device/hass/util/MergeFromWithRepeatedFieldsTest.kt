package org.openbase.bco.device.hass.util

import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import org.openbase.jul.extension.type.processing.LabelProcessor
import org.openbase.type.domotic.registry.UnitRegistryDataType
import org.openbase.type.domotic.unit.UnitConfigType
import java.util.Locale

class MergeFromWithRepeatedFieldsTest {
    @Test
    fun `should merge empty list`() {
        val id = "unit"
        val label = "label"

        val other = UnitConfigType.UnitConfig.newBuilder()
            .setId(id)
            .setLabel(LabelProcessor.generateLabelBuilder(label = label))
            .build()

        val original = UnitConfigType.UnitConfig.newBuilder()
            .setLabel(LabelProcessor.generateLabelBuilder(label = label))
            .setId(id)

        val updated = original.mergeFromWithRepeatedFields(other).build()

        updated.id shouldBeEqualTo id
        LabelProcessor.getBestMatch(label = updated.label) shouldBeEqualTo label
    }

    @Test
    fun `should merge simple list`() {
        val id = "unit"

        val other = UnitConfigType.UnitConfig.newBuilder()
            .setId(id)
            .apply {
                LabelProcessor.addLabel(labelBuilder, locale = Locale.ENGLISH, label = "updated label")
                LabelProcessor.addLabel(labelBuilder, locale = Locale.GERMANY, label = "german label")
            }
            .build()

        val original = UnitConfigType.UnitConfig.newBuilder()
            .setId(id)
            .apply {
                LabelProcessor.addLabel(labelBuilder, locale = Locale.ENGLISH, label = "original label")
                LabelProcessor.addLabel(labelBuilder, locale = Locale.CHINA, label = "chinese label")
            }

        val updated = original.mergeFromWithRepeatedFields(other).build()

        updated.id shouldBeEqualTo id
        updated.label.entryList.size shouldBeEqualTo 3
        updated.label.entryList.find { it.key == Locale.ENGLISH.toString() }!!.valueList.first() shouldBeEqualTo "updated label"
        updated.label.entryList.find { it.key == Locale.CHINESE.toString() }!!.valueList.first() shouldBeEqualTo "chinese label"
        updated.label.entryList.find { it.key == Locale.GERMAN.toString() }!!.valueList.first() shouldBeEqualTo "german label"
        LabelProcessor.getBestMatch(locale = Locale.ENGLISH, label = updated.label) shouldBeEqualTo "updated label"
        LabelProcessor.getBestMatch(locale = Locale.CHINESE, label = updated.label) shouldBeEqualTo "chinese label"
        LabelProcessor.getBestMatch(locale = Locale.GERMAN, label = updated.label) shouldBeEqualTo "german label"
    }

    @Test
    fun `should merge multi list`() {
        val unitRegistryData = UnitRegistryDataType.UnitRegistryData.newBuilder()
            .addAgentUnitConfig(
                UnitConfigType.UnitConfig.newBuilder()
                    .setId("agent 1")
                    .addAlias("alias 1")
                    .addAlias("alias 2")
                    .addAlias("alias 3")
            )
            .addAgentUnitConfig(
            UnitConfigType.UnitConfig.newBuilder()
                .setId("agent 2")
                .addAlias("alias 4")
                .addAlias("alias 5")
                .addAlias("alias 6")
        )

        val updatedUnitRegistryData: UnitRegistryDataType.UnitRegistryData = UnitRegistryDataType.UnitRegistryData.newBuilder()
            .addAgentUnitConfig(
                UnitConfigType.UnitConfig.newBuilder()
                    .setId("agent 1")
                    .addAlias("alias 1")
                    .addAlias("alias 2")
                    .addAlias("alias 3")
            )
            .addAgentUnitConfig(
                UnitConfigType.UnitConfig.newBuilder()
                    .setId("agent 3")
                    .addAlias("alias 7")
                    .addAlias("alias 8")
                    .addAlias("alias 9")
            ).build()

        val merged = unitRegistryData.mergeFromWithRepeatedFields(updatedUnitRegistryData).build()

        // Verify agent configs count
        merged.agentUnitConfigCount shouldBeEqualTo 3

        // Verify agent 1 aliases are deduplicated
        val agent1 = merged.agentUnitConfigList.find { it.id == "agent 1" }!!
        agent1.aliasList.sorted() shouldBeEqualTo listOf("alias 1", "alias 2", "alias 3")

        // Verify agent 2 aliases
        val agent2 = merged.agentUnitConfigList.find { it.id == "agent 2" }!!
        agent2.aliasList.sorted() shouldBeEqualTo listOf("alias 4", "alias 5", "alias 6")

        // Verify agent 3 aliases
        val agent3 = merged.agentUnitConfigList.find { it.id == "agent 3" }!!
        agent3.aliasList.sorted() shouldBeEqualTo listOf("alias 7", "alias 8", "alias 9")
    }
}
