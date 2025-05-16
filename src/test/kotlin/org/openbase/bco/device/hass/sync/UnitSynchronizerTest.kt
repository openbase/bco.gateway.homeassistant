package org.openbase.bco.device.hass.sync

import io.mockk.*
import org.junit.jupiter.api.Test
import org.openbase.bco.device.hass.communication.HassCommunicator
import org.openbase.bco.device.hass.communication.websocket.command.SubscriptionEvent
import org.openbase.bco.device.hass.manager.HassDeviceManager.Companion.ALIAS_KEY_HASS_AREA_ID
import org.openbase.bco.device.hass.manager.dto.HassDto
import org.openbase.bco.device.hass.manager.dto.HassInputDto
import org.openbase.bco.device.hass.sync.strategy.UnitSyncStrategy
import org.openbase.bco.device.hass.type.InputDtoProvider
import org.openbase.bco.device.hass.type.Mergeable
import org.openbase.bco.device.hass.util.get
import org.openbase.bco.device.hass.util.saveUnitConfig
import org.openbase.bco.device.hass.util.set
import org.openbase.bco.registry.unit.lib.UnitRegistry
import org.openbase.jul.extension.type.processing.LabelProcessor
import org.openbase.type.domotic.unit.UnitConfigType.UnitConfig
import org.openbase.type.domotic.unit.UnitTemplateType.UnitTemplate.UnitType
import org.openbase.type.domotic.unit.location.LocationConfigType.LocationConfig.LocationType
import java.util.concurrent.CompletableFuture

class UnitSynchronizerTest {
    private val  hassCommunicator: HassCommunicator = mockk()

    private val  unitRegistry: UnitRegistry = mockk()

    private val tileSyncStrategy: UnitSyncStrategy<TestHassDto, TestHassDtoInput> = mockk()

    private val onUnitChangesCallbackSlot = slot<() -> Any>()
    private val hassDtoSlot = slot<TestHassDto>()
    private val uniConfigSlot = slot<UnitConfig>()
    private val onDtoChangesCallbackSlot = slot<(event: SubscriptionEvent.Event) -> Any>()

    init {
        every { hassCommunicator.isConnected } returns true
        every { tileSyncStrategy.name } returns "TileSyncStrategy"
        every { tileSyncStrategy.unitType } returns UnitType.LOCATION
        every { tileSyncStrategy.unitFilter } returns { it.locationConfig?.locationType == LocationType.TILE }
        every { tileSyncStrategy.onDtoChanges(capture(onDtoChangesCallbackSlot)) } answers { mockk<AutoCloseable>() }
        every { tileSyncStrategy.onUnitChanges(capture(onUnitChangesCallbackSlot)) } answers { mockk<AutoCloseable>() }
        every { tileSyncStrategy.buildUnitConfig(capture(hassDtoSlot)) } answers {
            UnitConfig
                .newBuilder()
                .setUnitType(UnitType.LOCATION)
                .apply { locationConfigBuilder.locationType = LocationType.TILE }
                .setLabel(LabelProcessor.generateLabelBuilder(hassDtoSlot.captured.name))
                .apply { metaConfigBuilder[ALIAS_KEY_HASS_AREA_ID] = hassDtoSlot.captured.id }
                .build()
        }
        every { tileSyncStrategy.buildHassInputDto(capture(uniConfigSlot)) } answers {
            TestHassDtoInput(
                id = uniConfigSlot.captured.id,
                name = LabelProcessor.getBestMatch(uniConfigSlot.captured.label),
            )
        }
        mockkStatic(UnitRegistry::saveUnitConfig)
        val saveUnitConfigSlot = slot<UnitConfig>()
        every {
            unitRegistry.saveUnitConfig(
                capture(saveUnitConfigSlot),
            )
        } answers { CompletableFuture.completedFuture(saveUnitConfigSlot.captured) }
    }

    data class TestHassDto(
        override val id: String,
        override val name: String
    ): HassDto, Mergeable<TestHassDtoInput, TestHassDto>, InputDtoProvider<TestHassDtoInput> {
        override fun merge(input: TestHassDtoInput): TestHassDto = copy(
            id = input.id ?: id,
            name = input.name ?: name,
        )

        override fun toInputDto(): TestHassDtoInput = TestHassDtoInput(
            id = id,
            name = name,
        )
    }

    data class TestHassDtoInput(
        val id: String? = null,
        override val name: String? = null,
    ): HassInputDto

    fun prepareSynchronizer(
        unitConfigs: List<UnitConfig>,
        hassDtos: List<TestHassDto>,
        block: (UnitSynchronizer<TestHassDto, TestHassDtoInput>, DtoCache<TestHassDto>) -> Unit,
    ) {
        tileSyncStrategy.run {
            unitConfigs.forEach { unitConfig ->
                every { unitConfig.toHassId() } answers { unitConfig.metaConfig[ALIAS_KEY_HASS_AREA_ID] }
            }
        }

        val cache = DtoCache<TestHassDto>()

        every { unitRegistry.getUnitConfigsByUnitType(UnitType.LOCATION) } returns unitConfigs
        every { tileSyncStrategy.queryHassDtos() } returns hassDtos

        val synchronizer = UnitSynchronizer(
            cache = cache,
            strategy = tileSyncStrategy,
            unitRegistry = unitRegistry,
            hassCommunicator = hassCommunicator,
        )

        synchronizer.activate()
        cache.waitUntilReady()
        block(synchronizer, cache)
        synchronizer.deactivate()
    }

    @Test
    fun `a new hass location should be registered at bco`() {
        prepareSynchronizer(
            unitConfigs = listOf(
                UnitConfig.newBuilder()
                    .setUnitType(UnitType.LOCATION)
                    .setLabel(LabelProcessor.generateLabelBuilder("Office"))
                    .apply { locationConfigBuilder.locationType = LocationType.TILE }
                    .build(),
            ),
            hassDtos = listOf(
                TestHassDto(
                    id = "kitchen",
                    name = "Kitchen",
                ),
            )
        ) { synchronizer, cache ->
            // trigger change
            onDtoChangesCallbackSlot.captured.invoke(mockk<SubscriptionEvent.Event>())
            verify(exactly = 1) { unitRegistry.saveUnitConfig(any()) }

            // no further changes should be triggered
            onDtoChangesCallbackSlot.captured.invoke(mockk<SubscriptionEvent.Event>())
            verify(exactly = 1) { unitRegistry.saveUnitConfig(any()) }
        }
    }
}
