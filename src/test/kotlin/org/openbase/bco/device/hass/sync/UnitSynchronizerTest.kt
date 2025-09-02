package org.openbase.bco.device.hass.sync

import io.mockk.*
import org.amshove.kluent.shouldBe
import org.junit.jupiter.api.Test
import org.openbase.bco.device.hass.communication.HassCommunicator
import org.openbase.bco.device.hass.communication.websocket.command.SubscriptionEvent
import org.openbase.bco.device.hass.manager.HassDeviceManager
import org.openbase.bco.device.hass.manager.HassDeviceManager.Companion.ALIAS_KEY_HASS_ID
import org.openbase.bco.device.hass.manager.HassDeviceManager.Companion.ALIAS_KEY_HASS_TYPE
import org.openbase.bco.device.hass.manager.dto.HassDto
import org.openbase.bco.device.hass.manager.dto.HassInputDto
import org.openbase.bco.device.hass.sync.strategy.UnitSyncStrategy
import org.openbase.bco.device.hass.type.InputDtoProvider
import org.openbase.bco.device.hass.type.Mergeable
import org.openbase.bco.device.hass.util.*
import org.openbase.bco.registry.unit.lib.UnitRegistry
import org.openbase.bco.registry.unit.remote.UnitRegistryRemote
import org.openbase.jul.extension.type.processing.LabelProcessor
import org.openbase.type.configuration.EntryType
import org.openbase.type.domotic.unit.UnitConfigType.UnitConfig
import org.openbase.type.domotic.unit.UnitTemplateType.UnitTemplate.UnitType
import org.openbase.type.domotic.unit.location.LocationConfigType.LocationConfig.LocationType
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture

class UnitSynchronizerTest {
    private val hassCommunicator: HassCommunicator = mockk()

    private val unitRegistry: UnitRegistryRemote = mockk()

    private val tileSyncStrategy: UnitSyncStrategy<TestHassDto, TestHassDtoInput> = mockk()

    private val onUnitChangesCallbackSlot = slot<() -> Any>()
    private val hassDtoSlot = slot<TestHassDto>()
    private val uniConfigSlot = slot<UnitConfig>()
    private val onDtoChangesCallbackSlot = slot<(event: SubscriptionEvent.Event) -> Any>()

    private val unitConfigDB = mutableListOf<UnitConfig>()
    private val hassDtoDB = mutableListOf<TestHassDto>()

    init {
        every { hassCommunicator.isConnected } returns true
        every { tileSyncStrategy.name } returns "TileSyncStrategy"
        every { tileSyncStrategy.unitType } returns UnitType.LOCATION
        every { tileSyncStrategy.unitFilter } returns { it.locationConfig?.locationType == LocationType.TILE }
        every { tileSyncStrategy.onDtoChanges(capture(onDtoChangesCallbackSlot)) } answers { mockk<AutoCloseable>(relaxed = true) }
        every { tileSyncStrategy.onUnitChanges(capture(onUnitChangesCallbackSlot)) } answers { mockk<AutoCloseable>(relaxed = true) }
        every { tileSyncStrategy.buildUnitConfig(capture(hassDtoSlot)) } answers {
            UnitConfig
                .newBuilder()
                .setUnitType(UnitType.LOCATION)
                .apply { locationConfigBuilder.locationType = LocationType.TILE }
                .setLabel(LabelProcessor.generateLabelBuilder(hassDtoSlot.captured.name))
                .also { unitConfig -> tileSyncStrategy.run { unitConfig.link(hassDtoSlot.captured) } }
                .apply { metaConfigBuilder[ALIAS_KEY_HASS_ID] = hassDtoSlot.captured.id }
                .build()
        }
        every { tileSyncStrategy.buildHassInputDto(capture(uniConfigSlot)) } answers {
            TestHassDtoInput(
                id = uniConfigSlot.captured.metaConfig[ALIAS_KEY_HASS_ID],
                name = LabelProcessor.getBestMatch(uniConfigSlot.captured.label),
            )
        }
        mockkStatic(UnitRegistry::saveUnitConfig)
        // Mock the extension functions
        every {
            tileSyncStrategy.run { any<UnitConfig>().link(any()) }
        } answers {
            val unitConfig = firstArg<UnitConfig>()
            val hassDto = secondArg<TestHassDto>()
            unitConfig.toBuilder().apply {
                metaConfigBuilder[ALIAS_KEY_HASS_ID] = hassDto.id
            }
        }

        every {
            tileSyncStrategy.run { any<UnitConfig.Builder>().link(any()) }
        } answers {
            val builder = firstArg<UnitConfig.Builder>()
            val hassDto = secondArg<TestHassDto>()
            builder.apply {
                metaConfigBuilder[ALIAS_KEY_HASS_ID] = hassDto.id
            }
        }

        val saveHassDtoSlot  = slot<TestHassDtoInput>()
        every { tileSyncStrategy.saveHassDto(capture(saveHassDtoSlot)) } answers {
            saveHassDtoSlot.captured.toDto()
                .also { hassDto ->
                    hassDtoDB.removeIf { it.id == hassDto.id }
                    hassDtoDB.add(hassDto)
                }
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
                .also { unitConfig ->
                    unitConfigDB.removeIf { it.id == unitConfig.id }
                    unitConfigDB.add(unitConfig)
                }
                .also { saveUnitConfigSlot.clear() }
                .let { CompletableFuture.completedFuture(it) }
        }

        val deleteHassDtoSlot = slot<TestHassDto>()
        every { tileSyncStrategy.deleteHassDto(capture(deleteHassDtoSlot)) } answers {
            deleteHassDtoSlot.captured
                .also { hassDtoDB.remove(it) }
                .also { deleteHassDtoSlot.clear() }
        }

        val deleteUnitConfigSlot = slot<UnitConfig>()
        every { unitRegistry.removeUnitConfig(capture(deleteUnitConfigSlot)) } answers {
            deleteUnitConfigSlot.captured
                .also { unitConfigDB.remove(it) }
                .also { deleteUnitConfigSlot.clear() }
                .let { CompletableFuture.completedFuture(it) }
        }

        every { unitRegistry.getUnitConfigsByUnitType(UnitType.LOCATION) } answers { unitConfigDB }
        every { tileSyncStrategy.queryHassDtos() } answers { hassDtoDB }
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

        // wait until cache is initialized
        while (!cache.initialized) {
            cache.waitUntilReady(duration = Duration.ofSeconds(3))
            synchronizer.isActive shouldBe true
        }

        block(synchronizer, cache)
        synchronizer.deactivate()
    }

    fun changeContext(
        unitConfigs: List<UnitConfig>? = null,
        hassDtos: List<TestHassDto>? = null,
    ) {
        unitConfigs?.let {
            unitConfigDB.clear()
            unitConfigDB.addAll(unitConfigs)
        }

        hassDtos?.let {
            hassDtoDB.clear()
            hassDtoDB.addAll(hassDtos)
        }

        tileSyncStrategy.run {
            unitConfigDB.forEach { unitConfig ->
                every { unitConfig.toHassId() } answers { unitConfig.metaConfig[ALIAS_KEY_HASS_ID] }
            }
        }

        if(hassDtos.isNotNull() && onDtoChangesCallbackSlot.isCaptured) {
            onDtoChangesCallbackSlot.captured.invoke(mockk<SubscriptionEvent.Event>())
        }

        tileSyncStrategy.run {
            unitConfigDB.forEach { unitConfig ->
                every { unitConfig.toHassId() } answers { unitConfig.metaConfig[ALIAS_KEY_HASS_ID] }
            }
        }

        if(unitConfigs.isNotNull() && onUnitChangesCallbackSlot.isCaptured) {
            onUnitChangesCallbackSlot.captured.invoke()
        }

        tileSyncStrategy.run {
            unitConfigDB.forEach { unitConfig ->
                every { unitConfig.toHassId() } answers { unitConfig.metaConfig[ALIAS_KEY_HASS_ID] }
            }
        }
    }

    @Test
    fun `a new hass location should be registered at bco`() {

        changeContext(
            unitConfigs = listOf(),
            hassDtos = listOf(),
        )

        openSynchronizerSession { synchronizer, cache ->

            cache.dtos.size shouldBe 0
            cache.units.size shouldBe 0

            verify(exactly = 0) { tileSyncStrategy.saveHassDto(any()) }
            verify(exactly = 0) { tileSyncStrategy.deleteHassDto(any()) }
            verify(exactly = 0) { unitRegistry.saveUnitConfig(any()) }
            verify(exactly = 0) { unitRegistry.removeUnitConfig(any()) }

            // trigger change
            changeContext(
                unitConfigs = listOf(),
                hassDtos = listOf(
                    TestHassDto(
                        id = "kitchen",
                        name = "Kitchen",
                    ),
                ),
            )

            cache.dtos.size shouldBe 1
            cache.units.size shouldBe 1
            verify(exactly = 0) { tileSyncStrategy.saveHassDto(any()) }
            verify(exactly = 0) { tileSyncStrategy.deleteHassDto(any()) }
            verify(exactly = 1) { unitRegistry.saveUnitConfig(any()) }
            verify(exactly = 1) { unitRegistry.saveUnitConfig(match { it.label.bestMatch() == "Kitchen" }) }
            verify(exactly = 0) { unitRegistry.removeUnitConfig(any()) }

            // no further changes should be triggered
            changeContext(
                unitConfigs = listOf(),
                hassDtos = listOf(
                    TestHassDto(
                        id = "kitchen",
                        name = "Kitchen",
                    ),
                ),
            )
            cache.dtos.size shouldBe 1
            cache.units.size shouldBe 1
            verify(exactly = 0) { tileSyncStrategy.saveHassDto(any()) }
            verify(exactly = 0) { tileSyncStrategy.deleteHassDto(any()) }
            verify(exactly = 1) { unitRegistry.saveUnitConfig(any()) }
            verify(exactly = 0) { unitRegistry.removeUnitConfig(any()) }
        }
    }

    @Test
    fun `a new bco location should be registered at hass`() {

        changeContext(
            unitConfigs = listOf(),
            hassDtos = listOf(),
        )

        openSynchronizerSession { synchronizer, cache ->

            cache.dtos.size shouldBe 0
            cache.units.size shouldBe 0

            verify(exactly = 0) { tileSyncStrategy.saveHassDto(any()) }
            verify(exactly = 0) { tileSyncStrategy.deleteHassDto(any()) }
            verify(exactly = 0) { unitRegistry.saveUnitConfig(any()) }
            verify(exactly = 0) { unitRegistry.removeUnitConfig(any()) }

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
                hassDtos = listOf(),
            )
            cache.dtos.size shouldBe 1
            cache.units.size shouldBe 1
            verify(exactly = 1) { tileSyncStrategy.saveHassDto(match { it.name == "Office" }) }
            verify(exactly = 0) { tileSyncStrategy.deleteHassDto(any()) }
            verify(exactly = 1) { unitRegistry.saveUnitConfig(any()) }
            verify(exactly = 0) { unitRegistry.removeUnitConfig(any()) }

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
                hassDtos = listOf(),
            )
            cache.dtos.size shouldBe 1
            cache.units.size shouldBe 1
            verify(exactly = 1) { tileSyncStrategy.saveHassDto(any()) }
            verify(exactly = 0) { tileSyncStrategy.deleteHassDto(any()) }
            verify(exactly = 1) { unitRegistry.saveUnitConfig(any()) }
            verify(exactly = 0) { unitRegistry.removeUnitConfig(any()) }
        }
    }

    @Test
    fun `entities should be synchronized in both directions`() {

        changeContext(
            unitConfigs = listOf(),
            hassDtos = listOf(),
        )

        openSynchronizerSession { synchronizer, cache ->

            cache.dtos.size shouldBe 0
            cache.units.size shouldBe 0

            verify(exactly = 0) { tileSyncStrategy.saveHassDto(any()) }
            verify(exactly = 0) { tileSyncStrategy.deleteHassDto(any()) }
            verify(exactly = 0) { unitRegistry.saveUnitConfig(any()) }
            verify(exactly = 0) { unitRegistry.removeUnitConfig(any()) }

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
                ),
            )
            cache.dtos.size shouldBe 2
            cache.units.size shouldBe 2
            verify(exactly = 1) { tileSyncStrategy.saveHassDto(match { it.name == "Office" }) }
            verify(exactly = 0) { tileSyncStrategy.deleteHassDto(any()) }
            verify(exactly = 2) { unitRegistry.saveUnitConfig(any()) }
            verify(exactly = 1) { unitRegistry.saveUnitConfig(match { it.label.bestMatch() == "Kitchen" }) }
            verify(exactly = 1) { unitRegistry.saveUnitConfig(match { it.label.bestMatch() == "Office" }) }
            verify(exactly = 0) { unitRegistry.removeUnitConfig(any()) }

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
                ),
            )
            cache.dtos.size shouldBe 2
            cache.units.size shouldBe 2
            verify(exactly = 1) { tileSyncStrategy.saveHassDto(any()) }
            verify(exactly = 0) { tileSyncStrategy.deleteHassDto(any()) }
            verify(exactly = 2) { unitRegistry.saveUnitConfig(any()) }
            verify(exactly = 0) { unitRegistry.removeUnitConfig(any()) }
        }
    }
    @Test
    fun `a fully synced system should not further trigger any sync actions`() {

        changeContext(
            unitConfigs = listOf(
                UnitConfig.newBuilder()
                    .setId("Kitchen")
                    .setUnitType(UnitType.LOCATION)
                    .setLabel(LabelProcessor.generateLabelBuilder("Kitchen"))
                    .apply { locationConfigBuilder.locationType = LocationType.TILE }
                    .apply {
                        metaConfigBuilder.addEntry(
                            EntryType.Entry.newBuilder()
                                .setKey(ALIAS_KEY_HASS_ID)
                                .setValue("kitchen")
                                .build()
                        )
                    }
                    .apply {
                        metaConfigBuilder.addEntry(
                            EntryType.Entry.newBuilder()
                                .setKey(HassDeviceManager.ALIAS_KEY_HASS_TYPE)
                                .setValue("AREA")
                                .build()
                        )
                    }
                    .build(),
            ),
            hassDtos = listOf(
                TestHassDto(
                    id = "kitchen",
                    name = "Kitchen",
                ),
            ),
        )

        openSynchronizerSession { synchronizer, cache ->

            cache.dtos.size shouldBe 1
            cache.units.size shouldBe 1

            verify(exactly = 0) { tileSyncStrategy.saveHassDto(any()) }
            verify(exactly = 0) { tileSyncStrategy.deleteHassDto(any()) }
            verify(exactly = 0) { unitRegistry.saveUnitConfig(any()) }
            verify(exactly = 0) { unitRegistry.removeUnitConfig(any()) }

            // trigger change
            changeContext(
                unitConfigs = listOf(
                    UnitConfig.newBuilder()
                        .setId("Kitchen")
                        .setUnitType(UnitType.LOCATION)
                        .setLabel(LabelProcessor.generateLabelBuilder("Kitchen"))
                        .apply { locationConfigBuilder.locationType = LocationType.TILE }
                        .apply {
                            metaConfigBuilder.addEntry(
                                EntryType.Entry.newBuilder()
                                    .setKey(ALIAS_KEY_HASS_ID)
                                    .setValue("kitchen")
                                    .build()
                            )
                        }
                        .apply {
                            metaConfigBuilder.addEntry(
                                EntryType.Entry.newBuilder()
                                    .setKey(HassDeviceManager.ALIAS_KEY_HASS_TYPE)
                                    .setValue("AREA")
                                    .build()
                            )
                        }
                        .build(),
                ),
                hassDtos = listOf(
                    TestHassDto(
                        id = "kitchen",
                        name = "Kitchen",
                    ),
                ),
            )
            cache.dtos.size shouldBe 1
            cache.units.size shouldBe 1
            verify(exactly = 0) { tileSyncStrategy.saveHassDto(any()) }
            verify(exactly = 0) { tileSyncStrategy.deleteHassDto(any()) }
            verify(exactly = 0) { unitRegistry.saveUnitConfig(any()) }
            verify(exactly = 0) { unitRegistry.removeUnitConfig(any()) }
        }
    }

    @Test
    fun `a removed hass location should be removed at bco`() {

        changeContext(
            hassDtos = listOf(
                TestHassDto(
                    id = "kitchen",
                    name = "Kitchen",
                ),
                TestHassDto(
                    id = "restroom",
                    name = "Restroom",
                ),
                TestHassDto(
                    id = "dungeon",
                    name = "Dungeon",
                ),
            ),
        )

        openSynchronizerSession { synchronizer, cache ->

            cache.dtos.size shouldBe 3
            cache.units.size shouldBe 3
            unitConfigDB.size shouldBe 3
            hassDtoDB.size shouldBe 3

            verify(exactly = 0) { tileSyncStrategy.saveHassDto(any()) }
            verify(exactly = 0) { tileSyncStrategy.deleteHassDto(any()) }
            verify(exactly = 3) { unitRegistry.saveUnitConfig(any()) }
            verify(exactly = 0) { unitRegistry.removeUnitConfig(any()) }

            // trigger change
            changeContext(
                hassDtos = listOf(
                    TestHassDto(
                        id = "kitchen",
                        name = "Kitchen",
                    ),
                    TestHassDto(
                        id = "dungeon",
                        name = "Dungeon",
                    ),
                ),
            )

            cache.dtos.size shouldBe 2
            cache.units.size shouldBe 2
            unitConfigDB.size shouldBe 2
            hassDtoDB.size shouldBe 2

            verify(exactly = 0) { tileSyncStrategy.saveHassDto(any()) }
            verify(exactly = 0) { tileSyncStrategy.deleteHassDto(any()) }
            verify(exactly = 3) { unitRegistry.saveUnitConfig(any()) }
            verify(exactly = 1) { unitRegistry.removeUnitConfig(any()) }

            // no further changes should be triggered
            changeContext(
                hassDtos = listOf(
                    TestHassDto(
                        id = "kitchen",
                        name = "Kitchen",
                    ),
                    TestHassDto(
                        id = "dungeon",
                        name = "Dungeon",
                    ),
                ),
            )

            cache.dtos.size shouldBe 2
            cache.units.size shouldBe 2
            unitConfigDB.size shouldBe 2
            hassDtoDB.size shouldBe 2

            verify(exactly = 0) { tileSyncStrategy.saveHassDto(any()) }
            verify(exactly = 0) { tileSyncStrategy.deleteHassDto(any()) }
            verify(exactly = 3) { unitRegistry.saveUnitConfig(any()) }
            verify(exactly = 1) { unitRegistry.removeUnitConfig(any()) }
        }
    }

    @Test
    fun `a removed bco location should be removed at hass`() {
        val kitchenUnitConfig = UnitConfig.newBuilder()
            .setId("kitchen")
            .setUnitType(UnitType.LOCATION)
            .setLabel(LabelProcessor.generateLabelBuilder("Kitchen"))
            .apply { locationConfigBuilder.locationType = LocationType.TILE }
            .apply {
                metaConfigBuilder.addEntry(
                    EntryType.Entry.newBuilder()
                        .setKey(ALIAS_KEY_HASS_ID)
                        .setValue("kitchen")
                        .build()
                )
            }
            .apply {
                metaConfigBuilder.addEntry(
                    EntryType.Entry.newBuilder()
                        .setKey(HassDeviceManager.ALIAS_KEY_HASS_TYPE)
                        .setValue("AREA")
                        .build()
                )
            }
            .build()

        val kitchenHassDto = TestHassDto(
            id = "kitchen",
            name = "Kitchen",
        )

        val restroomUnitConfig = UnitConfig.newBuilder()
            .setId("restroom")
            .setUnitType(UnitType.LOCATION)
            .setLabel(LabelProcessor.generateLabelBuilder("Restroom"))
            .apply { locationConfigBuilder.locationType = LocationType.TILE }
            .apply {
                metaConfigBuilder.addEntry(
                    EntryType.Entry.newBuilder()
                        .setKey(ALIAS_KEY_HASS_ID)
                        .setValue("restroom")
                        .build()
                )
            }
            .apply {
                metaConfigBuilder.addEntry(
                    EntryType.Entry.newBuilder()
                        .setKey(HassDeviceManager.ALIAS_KEY_HASS_TYPE)
                        .setValue("AREA")
                        .build()
                )
            }
            .build()

        val restroomHassDto = TestHassDto(
            id = "restroom",
            name = "Restroom",
        )

        changeContext(
            unitConfigs = listOf(
                kitchenUnitConfig,
                restroomUnitConfig,
            ),
            hassDtos = listOf(
                kitchenHassDto,
                restroomHassDto,
            ),
        )

        openSynchronizerSession { synchronizer, cache ->
            cache.dtos.size shouldBe 2
            cache.units.size shouldBe 2
            unitConfigDB.size shouldBe 2
            hassDtoDB.size shouldBe 2

            verify(exactly = 0) { tileSyncStrategy.saveHassDto(any()) }
            verify(exactly = 0) { tileSyncStrategy.deleteHassDto(any()) }
            verify(exactly = 0) { unitRegistry.saveUnitConfig(any()) }
            verify(exactly = 0) { unitRegistry.removeUnitConfig(any()) }

            // trigger change
            changeContext(
                unitConfigs = listOf(
                    kitchenUnitConfig,
                ),
                hassDtos = listOf(
                    kitchenHassDto,
                    restroomHassDto,
                ),
            )

            cache.dtos.size shouldBe 1
            cache.units.size shouldBe 1
            unitConfigDB.size shouldBe 1
            hassDtoDB.size shouldBe 1

            verify(exactly = 0) { tileSyncStrategy.saveHassDto(any()) }
            verify(exactly = 1) { tileSyncStrategy.deleteHassDto(any()) }
            verify(exactly = 0) { unitRegistry.saveUnitConfig(any()) }
            verify(exactly = 0) { unitRegistry.removeUnitConfig(any()) }

            // no further changes should be triggered
            changeContext(
                unitConfigs = listOf(
                    kitchenUnitConfig,
                ),
                hassDtos = listOf(
                    kitchenHassDto,
                ),
            )

            cache.dtos.size shouldBe 1
            cache.units.size shouldBe 1
            unitConfigDB.size shouldBe 1
            hassDtoDB.size shouldBe 1

            verify(exactly = 0) { tileSyncStrategy.saveHassDto(any()) }
            verify(exactly = 1) { tileSyncStrategy.deleteHassDto(any()) }
            verify(exactly = 0) { unitRegistry.saveUnitConfig(any()) }
            verify(exactly = 0) { unitRegistry.removeUnitConfig(any()) }
        }
    }

    @Test
    fun `a removed location should be synchronized in both directions`() {
        val kitchenUnitConfig = UnitConfig.newBuilder()
            .setId("kitchen")
            .setUnitType(UnitType.LOCATION)
            .setLabel(LabelProcessor.generateLabelBuilder("Kitchen"))
            .apply { locationConfigBuilder.locationType = LocationType.TILE }
            .apply {
                metaConfigBuilder.addEntry(
                    EntryType.Entry.newBuilder()
                        .setKey(ALIAS_KEY_HASS_ID)
                        .setValue("kitchen")
                        .build()
                )
            }
            .apply {
                metaConfigBuilder.addEntry(
                    EntryType.Entry.newBuilder()
                        .setKey(HassDeviceManager.ALIAS_KEY_HASS_TYPE)
                        .setValue("AREA")
                        .build()
                )
            }
            .build()

        val kitchenHassDto = TestHassDto(
            id = "kitchen",
            name = "Kitchen",
        )

        val restroomUnitConfig = UnitConfig.newBuilder()
            .setId("restroom")
            .setUnitType(UnitType.LOCATION)
            .setLabel(LabelProcessor.generateLabelBuilder("Restroom"))
            .apply { locationConfigBuilder.locationType = LocationType.TILE }
            .apply {
                metaConfigBuilder.addEntry(
                    EntryType.Entry.newBuilder()
                        .setKey(ALIAS_KEY_HASS_ID)
                        .setValue("restroom")
                        .build()
                )
            }
            .apply {
                metaConfigBuilder.addEntry(
                    EntryType.Entry.newBuilder()
                        .setKey(HassDeviceManager.ALIAS_KEY_HASS_TYPE)
                        .setValue("AREA")
                        .build()
                )
            }
            .build()

        val restroomHassDto = TestHassDto(
            id = "restroom",
            name = "Restroom",
        )

        changeContext(
            unitConfigs = listOf(
                kitchenUnitConfig,
                restroomUnitConfig,
            ),
            hassDtos = listOf(
                kitchenHassDto,
                restroomHassDto,
            ),
        )

        openSynchronizerSession { synchronizer, cache ->
            cache.dtos.size shouldBe 2
            cache.units.size shouldBe 2
            unitConfigDB.size shouldBe 2
            hassDtoDB.size shouldBe 2

            verify(exactly = 0) { tileSyncStrategy.saveHassDto(any()) }
            verify(exactly = 0) { tileSyncStrategy.deleteHassDto(any()) }
            verify(exactly = 0) { unitRegistry.saveUnitConfig(any()) }
            verify(exactly = 0) { unitRegistry.removeUnitConfig(any()) }

            // trigger change
            changeContext(
                unitConfigs = listOf(
                    kitchenUnitConfig,
                ),
                hassDtos = listOf(
                    restroomHassDto,
                ),
            )

            cache.dtos.size shouldBe 0
            cache.units.size shouldBe 0
            unitConfigDB.size shouldBe 0
            hassDtoDB.size shouldBe 0

            verify(exactly = 0) { tileSyncStrategy.saveHassDto(any()) }
            verify(exactly = 1) { tileSyncStrategy.deleteHassDto(any()) }
            verify(exactly = 0) { unitRegistry.saveUnitConfig(any()) }
            verify(exactly = 1) { unitRegistry.removeUnitConfig(any()) }

            // no further changes should be triggered
            changeContext(
                unitConfigs = emptyList(),
                hassDtos = emptyList(),
            )

            cache.dtos.size shouldBe 0
            cache.units.size shouldBe 0
            unitConfigDB.size shouldBe 0
            hassDtoDB.size shouldBe 0

            verify(exactly = 0) { tileSyncStrategy.saveHassDto(any()) }
            verify(exactly = 1) { tileSyncStrategy.deleteHassDto(any()) }
            verify(exactly = 0) { unitRegistry.saveUnitConfig(any()) }
            verify(exactly = 1) { unitRegistry.removeUnitConfig(any()) }
        }
    }

    @Test
    fun `an update of a bco unit should update the linked hass dto`() {

        val kitchenUnitConfig = UnitConfig.newBuilder()
            .setId("kitchen")
            .setUnitType(UnitType.LOCATION)
            .setLabel(LabelProcessor.generateLabelBuilder("Kitchen"))
            .apply { locationConfigBuilder.locationType = LocationType.TILE }
            .apply {
                metaConfigBuilder.addEntry(
                    EntryType.Entry.newBuilder()
                        .setKey(ALIAS_KEY_HASS_ID)
                        .setValue("kitchen")
                        .build()
                )
            }
            .apply {
                metaConfigBuilder.addEntry(
                    EntryType.Entry.newBuilder()
                        .setKey(HassDeviceManager.ALIAS_KEY_HASS_TYPE)
                        .setValue("AREA")
                        .build()
                )
            }
            .build()

        val kitchenHassDto = TestHassDto(
            id = "kitchen",
            name = "Kitchen",
        )

        changeContext(
            unitConfigs = listOf(
                kitchenUnitConfig,
            ),
            hassDtos = listOf(
                kitchenHassDto,
            ),
        )

        openSynchronizerSession { _, cache ->
            cache.dtos.size shouldBe 1
            cache.units.size shouldBe 1
            unitConfigDB.size shouldBe 1
            hassDtoDB.size shouldBe 1

            verify(exactly = 0) { tileSyncStrategy.saveHassDto(any()) }
            verify(exactly = 0) { tileSyncStrategy.deleteHassDto(any()) }
            verify(exactly = 0) { unitRegistry.saveUnitConfig(any()) }
            verify(exactly = 0) { unitRegistry.removeUnitConfig(any()) }

            // trigger change
            changeContext(
                unitConfigs = listOf(
                    kitchenUnitConfig.toBuilder().setLabel(LabelProcessor.generateLabelBuilder("Updated Kitchen")).build(),
                ),
            )

            cache.dtos.size shouldBe 1
            cache.units.size shouldBe 1
            unitConfigDB.size shouldBe 1
            hassDtoDB.size shouldBe 1

            verify(exactly = 1) { tileSyncStrategy.saveHassDto(match { it.name == "Updated Kitchen" }) }
            verify(exactly = 0) { tileSyncStrategy.deleteHassDto(any()) }
            verify(exactly = 0) { unitRegistry.saveUnitConfig(any()) }
            verify(exactly = 0) { unitRegistry.removeUnitConfig(any()) }
        }
    }

    @Test
    fun `an update of a hass unit should update the linked bco unit`() {

        val kitchenUnitConfig = UnitConfig.newBuilder()
            .setId("kitchen")
            .setUnitType(UnitType.LOCATION)
            .setLabel(LabelProcessor.generateLabelBuilder("Kitchen"))
            .apply { locationConfigBuilder.locationType = LocationType.TILE }
            .apply {
                metaConfigBuilder.addEntry(
                    EntryType.Entry.newBuilder()
                        .setKey(ALIAS_KEY_HASS_ID)
                        .setValue("kitchen")
                        .build()
                )
            }
            .apply {
                metaConfigBuilder.addEntry(
                    EntryType.Entry.newBuilder()
                        .setKey(HassDeviceManager.ALIAS_KEY_HASS_TYPE)
                        .setValue("AREA")
                        .build()
                )
            }
            .build()

        val kitchenHassDto = TestHassDto(
            id = "kitchen",
            name = "Kitchen",
        )

        changeContext(
            unitConfigs = listOf(
                kitchenUnitConfig,
            ),
            hassDtos = listOf(
                kitchenHassDto,
            ),
        )

        openSynchronizerSession { _, cache ->
            cache.dtos.size shouldBe 1
            cache.units.size shouldBe 1
            unitConfigDB.size shouldBe 1
            hassDtoDB.size shouldBe 1

            verify(exactly = 0) { tileSyncStrategy.saveHassDto(any()) }
            verify(exactly = 0) { tileSyncStrategy.deleteHassDto(any()) }
            verify(exactly = 0) { unitRegistry.saveUnitConfig(any()) }
            verify(exactly = 0) { unitRegistry.removeUnitConfig(any()) }

            // trigger change
            changeContext(
                unitConfigs = listOf(
                    kitchenUnitConfig,
                ),
                hassDtos = listOf(
                    kitchenHassDto.copy(name = "Updated Kitchen"),
                ),
            )

            cache.dtos.size shouldBe 1
            cache.units.size shouldBe 1
            unitConfigDB.size shouldBe 1
            hassDtoDB.size shouldBe 1

            verify(exactly = 0) { tileSyncStrategy.saveHassDto(any()) }
            verify(exactly = 0) { tileSyncStrategy.deleteHassDto(any()) }
            verify(exactly = 1) { unitRegistry.saveUnitConfig(match { it.label.bestMatch() == "Updated Kitchen" }) }
            verify(exactly = 0) { unitRegistry.removeUnitConfig(any()) }
        }
    }

    @Test
    fun `updates should be triggered in both directions`() {

        val kitchenUnitConfig = UnitConfig.newBuilder()
            .setId("kitchen")
            .setUnitType(UnitType.LOCATION)
            .setLabel(LabelProcessor.generateLabelBuilder("Kitchen"))
            .apply { locationConfigBuilder.locationType = LocationType.TILE }
            .apply {
                metaConfigBuilder.addEntry(
                    EntryType.Entry.newBuilder()
                        .setKey(ALIAS_KEY_HASS_ID)
                        .setValue("kitchen")
                        .build()
                )
            }
            .apply {
                metaConfigBuilder.addEntry(
                    EntryType.Entry.newBuilder()
                        .setKey(HassDeviceManager.ALIAS_KEY_HASS_TYPE)
                        .setValue("AREA")
                        .build()
                )
            }
            .build()

        val kitchenHassDto = TestHassDto(
            id = "kitchen",
            name = "Kitchen",
        )

        val restroomUnitConfig = UnitConfig.newBuilder()
            .setId("restroom")
            .setUnitType(UnitType.LOCATION)
            .setLabel(LabelProcessor.generateLabelBuilder("Restroom"))
            .apply { locationConfigBuilder.locationType = LocationType.TILE }
            .apply {
                metaConfigBuilder.addEntry(
                    EntryType.Entry.newBuilder()
                        .setKey(ALIAS_KEY_HASS_ID)
                        .setValue("restroom")
                        .build()
                )
            }
            .apply {
                metaConfigBuilder.addEntry(
                    EntryType.Entry.newBuilder()
                        .setKey(HassDeviceManager.ALIAS_KEY_HASS_TYPE)
                        .setValue("AREA")
                        .build()
                )
            }
            .build()

        val restroomHassDto = TestHassDto(
            id = "restroom",
            name = "Restroom",
        )

        changeContext(
            unitConfigs = listOf(
                kitchenUnitConfig,
                restroomUnitConfig,
            ),
            hassDtos = listOf(
                kitchenHassDto,
                restroomHassDto,
            ),
        )

        openSynchronizerSession { _, cache ->
            cache.dtos.size shouldBe 2
            cache.units.size shouldBe 2
            unitConfigDB.size shouldBe 2
            hassDtoDB.size shouldBe 2

            verify(exactly = 0) { tileSyncStrategy.saveHassDto(any()) }
            verify(exactly = 0) { tileSyncStrategy.deleteHassDto(any()) }
            verify(exactly = 0) { unitRegistry.saveUnitConfig(any()) }
            verify(exactly = 0) { unitRegistry.removeUnitConfig(any()) }

            // trigger change
            changeContext(
                unitConfigs = listOf(
                    kitchenUnitConfig.toBuilder().setLabel(LabelProcessor.generateLabelBuilder("New Kitchen")).build(),
                    restroomUnitConfig,
                ),
                hassDtos = listOf(
                    kitchenHassDto,
                    restroomHassDto.copy(name = "New Restroom"),
                ),
            )

            cache.dtos.size shouldBe 2
            cache.units.size shouldBe 2
            unitConfigDB.size shouldBe 2
            hassDtoDB.size shouldBe 2

            verify(exactly = 1) { tileSyncStrategy.saveHassDto(match { it.name == "New Kitchen" }) }
            verify(exactly = 0) { tileSyncStrategy.saveHassDto(match { it.name == "New Restroom" }) }
            verify(exactly = 0) { tileSyncStrategy.deleteHassDto(any()) }
            verify(exactly = 0) { unitRegistry.saveUnitConfig(match { it.label.bestMatch() == "New Kitchen" }) }
            verify(exactly = 1) { unitRegistry.saveUnitConfig(match { it.label.bestMatch() == "New Restroom" }) }
            verify(exactly = 0) { unitRegistry.removeUnitConfig(any()) }
        }
    }
}
