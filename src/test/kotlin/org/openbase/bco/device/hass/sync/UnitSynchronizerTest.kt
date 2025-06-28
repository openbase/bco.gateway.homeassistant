package org.openbase.bco.device.hass.sync

import io.mockk.*
import org.amshove.kluent.shouldBe
import org.junit.jupiter.api.Test
import org.openbase.bco.device.hass.communication.HassCommunicator
import org.openbase.bco.device.hass.communication.websocket.command.SubscriptionEvent
import org.openbase.bco.device.hass.manager.HassDeviceManager.Companion.ALIAS_KEY_HASS_AREA_ID
import org.openbase.bco.device.hass.manager.dto.HassDto
import org.openbase.bco.device.hass.manager.dto.HassInputDto
import org.openbase.bco.device.hass.sync.strategy.UnitSyncStrategy
import org.openbase.bco.device.hass.type.InputDtoProvider
import org.openbase.bco.device.hass.type.Mergeable
import org.openbase.bco.device.hass.util.*
import org.openbase.bco.registry.unit.lib.UnitRegistry
import org.openbase.jul.extension.type.processing.LabelProcessor
import org.openbase.type.domotic.unit.UnitConfigType.UnitConfig
import org.openbase.type.domotic.unit.UnitTemplateType.UnitTemplate.UnitType
import org.openbase.type.domotic.unit.location.LocationConfigType.LocationConfig.LocationType
import java.util.*
import java.util.concurrent.CompletableFuture

class UnitSynchronizerTest {
    private val hassCommunicator: HassCommunicator = mockk()

    private val unitRegistry: UnitRegistry = mockk()

    private val tileSyncStrategy: UnitSyncStrategy<TestHassDto, TestHassDtoInput> = mockk()

    private val onUnitChangesCallbackSlot = slot<() -> Any>()
    private val hassDtoSlot = slot<TestHassDto>()
    private val uniConfigSlot = slot<UnitConfig>()
    private val onDtoChangesCallbackSlot = slot<(event: SubscriptionEvent.Event) -> Any>()

    private var unitConfigDB = listOf<UnitConfig>()
    private var hassDtoDB = listOf<TestHassDto>()

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
                id = uniConfigSlot.captured.metaConfig[ALIAS_KEY_HASS_AREA_ID],
                name = LabelProcessor.getBestMatch(uniConfigSlot.captured.label),
            )
        }
        mockkStatic(UnitRegistry::saveUnitConfig)

        val saveHassDtoSlot  = slot<TestHassDtoInput>()
        every { tileSyncStrategy.saveHassDto(capture(saveHassDtoSlot)) } answers {
            saveHassDtoSlot.captured.toDto()
                .also { hassDtoDB = hassDtoDB.plus(it) }
                .also { saveHassDtoSlot.clear() }
        }
        val saveUnitConfigSlot = slot<UnitConfig>()
        every {
            unitRegistry.saveUnitConfig(
                capture(saveUnitConfigSlot),
            )
        } answers {
            saveUnitConfigSlot.captured
                .let { unitConfig ->
                    unitConfig
                        .toBuilder()
                        .setId(unitConfig.id.ifBlank { unitConfig.label.bestMatch() })
                        .build() }
                .also { unitConfigDB = unitConfigDB.plus(it) }
                .also { saveUnitConfigSlot.clear() }
                .let { CompletableFuture.completedFuture(it) }
        }
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

    private fun TestHassDtoInput.toDto(): TestHassDto = TestHassDto(
        id = id ?: UUID.randomUUID().toString(),
        name = name ?: "",
    )

    fun openSynchronizerSession(
        block: (UnitSynchronizer<TestHassDto, TestHassDtoInput>, DtoCache<TestHassDto>) -> Unit,
    ) {
        val cache = DtoCache<TestHassDto>()
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

    fun changeContext(
        unitConfigs: List<UnitConfig>?,
        hassDtos: List<TestHassDto>?,
    ) {
        tileSyncStrategy.run {
            unitConfigs?.forEach { unitConfig ->
                every { unitConfig.toHassId() } answers { unitConfig.metaConfig[ALIAS_KEY_HASS_AREA_ID] }
            }
        }

        unitConfigDB = unitConfigs.orEmpty()
        hassDtoDB = hassDtos.orEmpty()

        unitConfigs?.let {
            every { unitRegistry.getUnitConfigsByUnitType(UnitType.LOCATION) } answers { unitConfigDB }
        }

        hassDtos?.let {
            every { tileSyncStrategy.queryHassDtos() } answers { hassDtoDB }
        }

        if(hassDtos.isNotNull() && onDtoChangesCallbackSlot.isCaptured) {
            onDtoChangesCallbackSlot.captured.invoke(mockk<SubscriptionEvent.Event>())
        }

        if(unitConfigs.isNotNull() && onUnitChangesCallbackSlot.isCaptured) {
            onUnitChangesCallbackSlot.captured.invoke()
        }
    }

    @Test
    fun `a new hass location should be registered at bco`() {

        changeContext(
            unitConfigs = listOf(),
            hassDtos = listOf()
        )

        openSynchronizerSession { synchronizer, cache ->

            cache.dtos.size shouldBe 0
            cache.units.size shouldBe 0

            verify(exactly = 0) { tileSyncStrategy.saveHassDto(any()) }
            verify(exactly = 0) { unitRegistry.saveUnitConfig(any()) }

            // trigger change
            changeContext(
                unitConfigs = listOf(),
                hassDtos = listOf(
                    TestHassDto(
                        id = "kitchen",
                        name = "Kitchen",
                    ),
                )
            )

            cache.dtos.size shouldBe 1
            cache.units.size shouldBe 1
            verify(exactly = 0) { tileSyncStrategy.saveHassDto(any()) }
            verify(exactly = 1) { unitRegistry.saveUnitConfig(any()) }
            verify(exactly = 1) { unitRegistry.saveUnitConfig(match { it.label.bestMatch() == "Kitchen" }) }

            // no further changes should be triggered
            changeContext(
                unitConfigs = listOf(),
                hassDtos = listOf(
                    TestHassDto(
                        id = "kitchen",
                        name = "Kitchen",
                    ),
                )
            )
            cache.dtos.size shouldBe 1
            cache.units.size shouldBe 1
            verify(exactly = 0) { tileSyncStrategy.saveHassDto(any()) }
            verify(exactly = 1) { unitRegistry.saveUnitConfig(any()) }
        }
    }

    @Test
    fun `a new bco location should be registered at bco`() {

        changeContext(
            unitConfigs = listOf(),
            hassDtos = listOf()
        )

        openSynchronizerSession { synchronizer, cache ->

            cache.dtos.size shouldBe 0
            cache.units.size shouldBe 0

            verify(exactly = 0) { tileSyncStrategy.saveHassDto(any()) }
            verify(exactly = 0) { unitRegistry.saveUnitConfig(any()) }

            // trigger change
            changeContext(
                unitConfigs = listOf(
                    UnitConfig.newBuilder()
                        .setId("Office")
                        .setUnitType(UnitType.LOCATION)
                        .setLabel(LabelProcessor.generateLabelBuilder("Office"))
                        .apply { locationConfigBuilder.locationType = LocationType.TILE }
                        .build(),
                ),
                hassDtos = listOf()
            )
            cache.dtos.size shouldBe 1
            cache.units.size shouldBe 1
            verify(exactly = 1) { tileSyncStrategy.saveHassDto(match { it.name == "Office" }) }
            verify(exactly = 0) { unitRegistry.saveUnitConfig(any()) }

            // no further changes should be triggered
            changeContext(
                unitConfigs = listOf(
                    UnitConfig.newBuilder()
                        .setId("Office")
                        .setUnitType(UnitType.LOCATION)
                        .setLabel(LabelProcessor.generateLabelBuilder("Office"))
                        .apply { locationConfigBuilder.locationType = LocationType.TILE }
                        .build(),
                ),
                hassDtos = listOf()
            )
            cache.dtos.size shouldBe 1
            cache.units.size shouldBe 1
            verify(exactly = 1) { tileSyncStrategy.saveHassDto(any()) }
            verify(exactly = 0) { unitRegistry.saveUnitConfig(any()) }
        }
    }

    @Test
    fun `entities should be synchronized in both directions`() {

        changeContext(
            unitConfigs = listOf(),
            hassDtos = listOf()
        )

        openSynchronizerSession { synchronizer, cache ->

            cache.dtos.size shouldBe 0
            cache.units.size shouldBe 0

            verify(exactly = 0) { tileSyncStrategy.saveHassDto(any()) }
            verify(exactly = 0) { unitRegistry.saveUnitConfig(any()) }

            // trigger change
            changeContext(
                unitConfigs = listOf(
                    UnitConfig.newBuilder()
                        .setId("Office")
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
            )
            cache.dtos.size shouldBe 2
            cache.units.size shouldBe 2
            verify(exactly = 1) { tileSyncStrategy.saveHassDto(match { it.name == "Office" }) }
            verify(exactly = 1) { unitRegistry.saveUnitConfig(match { it.label.bestMatch() == "Kitchen" }) }

            // no further changes should be triggered
            changeContext(
                unitConfigs = listOf(
                    UnitConfig.newBuilder()
                        .setId("Office")
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
            )
            cache.dtos.size shouldBe 2
            cache.units.size shouldBe 2
            verify(exactly = 1) { tileSyncStrategy.saveHassDto(any()) }
            verify(exactly = 1) { unitRegistry.saveUnitConfig(any()) }
        }
    }
}

/**
 * bco register -> hass
 * hass register -> bco
 * both register -> both
 * bco update -> hass
 * hass update -> bco
 * both update -> both
 * bco delete -> hass
 * hass delete -> bco
 * both delete -> both
 */
