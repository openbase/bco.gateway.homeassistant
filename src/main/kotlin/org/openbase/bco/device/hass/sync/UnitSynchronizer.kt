package org.openbase.bco.device.hass.sync

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.openbase.bco.device.hass.communication.HassCommunicator
import org.openbase.bco.device.hass.manager.dto.HassDto
import org.openbase.bco.device.hass.manager.dto.HassInputDto
import org.openbase.bco.device.hass.sync.strategy.UnitSyncStrategy
import org.openbase.bco.device.hass.type.InputDtoProvider
import org.openbase.bco.device.hass.type.Mergeable
import org.openbase.bco.device.hass.util.*
import org.openbase.bco.registry.unit.lib.UnitRegistry
import org.openbase.jul.exception.printer.ExceptionPrinter
import org.openbase.jul.exception.printer.LogLevel
import org.openbase.jul.exception.tryOrNull
import org.openbase.jul.extension.protobuf.ProtoBufBuilderProcessor.mergeFromWithoutRepeatedFields
import org.openbase.jul.iface.Activatable
import org.openbase.jul.schedule.RecurrenceEventFilter
import org.openbase.type.domotic.state.ConnectionStateType
import org.openbase.type.domotic.unit.UnitConfigType.UnitConfig
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlin.time.toKotlinDuration

class UnitSynchronizer<HASS_DTO, HASS_INPUT_DTO : HassInputDto>(
    val strategy: UnitSyncStrategy<HASS_DTO, HASS_INPUT_DTO>,
    val cache: DtoCache<HASS_DTO> = DtoCache(),
    val hassCommunicator: HassCommunicator,
    val unitRegistry: UnitRegistry,
) : Activatable where
HASS_DTO : HassDto,
HASS_DTO : Mergeable<HASS_INPUT_DTO, HASS_DTO>,
HASS_DTO : InputDtoProvider<HASS_INPUT_DTO> {

    private var coroutineScope: CoroutineScope? = null
//    private val debounceDuration = Duration.ofSeconds(1).toKotlinDuration()
    private val activationMutex = Mutex()
    private var observer = listOf<AutoCloseable>()

    private val bcoToHassSyncDebounceFilter = DebounceFilter<Unit> { syncBCOtoHass() }
    private val hassToBCOSyncDebounceFilter = DebounceFilter<Unit> { syncHassToBCO() }

//    private val bcoToHassSyncTrigger = MutableSharedFlow<kotlin.Unit>(
//        replay = 0,
//        extraBufferCapacity = 1,
//        onBufferOverflow = BufferOverflow.DROP_OLDEST
//    )
//
//    private val hassToBCOSyncTrigger = MutableSharedFlow<kotlin.Unit>(
//        replay = 0,
//        extraBufferCapacity = 1,
//        onBufferOverflow = BufferOverflow.DROP_OLDEST
//    )

//    @OptIn(FlowPreview::class)
//    private fun initTrigger(scope: CoroutineScope) {
//        scope.launch {
//            bcoToHassSyncTrigger
////                .debounce(debounceDuration)
//                .collect { syncBCOtoHass() }
//        }
//        scope.launch {
//            hassToBCOSyncTrigger
////                .debounce (debounceDuration)
//                .collect { syncHassToBCO() }
//        }
//    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun activate() {
        GlobalScope.launch {
            activationMutex.withLock {
                if (this@UnitSynchronizer.isActive) return@withLock

//                CoroutineScope(SupervisorJob() + Dispatchers.IO)
//                    .also { coroutineScope = it }
//                    .let { initTrigger(it) }

                if (!hassCommunicator.isConnected) {
                    LOGGER.info("Waiting for hass connection...")
                    hassCommunicator.waitForConnectionState(ConnectionStateType.ConnectionState.State.CONNECTED)
                }

                strategy.onDtoChanges() {
                    hassToBCOSyncDebounceFilter.trigger()
                }.also { observer += it }

                strategy.onUnitChanges() {
                    bcoToHassSyncDebounceFilter.trigger()
                }.also { observer += it }

                LOGGER.info("Activated ${strategy.name}")
                syncAll()
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun deactivate() {
        GlobalScope.launch {
            activationMutex.withLock {
                observer.forEach(AutoCloseable::close)
                coroutineScope?.cancel()
                coroutineScope = null
            }
        }
    }

    override fun isActive(): Boolean = coroutineScope?.isActive == true

    private fun syncAll() {
        syncHassToBCO() // order important for sync!
        syncBCOtoHass() // order important for sync!
        cache.confirmInit()
    }

//    private fun triggerHassToBCOSync() = hassToBCOSyncTrigger.tryEmit(Unit).also {
//        if (it == false) {
//            LOGGER.warn("Failed to trigger sync from hass to bco.")
//        }
//    }
//    private fun triggerBCOToHassSync() = bcoToHassSyncTrigger.tryEmit(Unit).also {
//        if (it == false) {
//            LOGGER.warn("Failed to trigger sync from hass to bco.")
//        }
//    }

    private fun syncHassToBCO() {
        LOGGER.info("Sync ${strategy.unitType.name.lowercase()}s from hass to bco...")
        val unitConfigs = getUnitConfigMap()
        strategy.queryHassDtos().let { hassDtos ->
            // handle: add and update
            hassDtos
                .map { hassDto -> hassDto to hassDto.toUnitConfig() }
                .mapSecond { (hassDto, unitConfig) ->
                    cache.getUnitIdByDtoId(hassDto.id)
                        .tryOrNull { unitConfigs[it] }
                        ?.toBuilder()
                        ?.mergeFromWithoutRepeatedFields(unitConfig)
                        ?.build()
                        ?: unitConfig
                }
                .also { pairs ->
                    pairs
                        .filter { (_, unitConfig) -> unitConfig != cache.getUnitConfigById(unitConfig.id) }
                        .mapSecondNotNull { (hassDto, unitConfig) ->
                            LOGGER.info("Save unit ${unitConfig.label.bestMatch()} of corresponding hass dto ${hassDto.name}.")
                            runCatching { unitRegistry.saveUnitConfig(unitConfig).await() }
                                .getOrElse {
                                    ExceptionPrinter.printHistory(
                                        "Could not save unit ${unitConfig.label.bestMatch()} of corresponding hass dto ${hassDto.name}.",
                                        it,
                                        LOGGER,
                                        LogLevel.WARN,
                                    )
                                    null
                                }
                        }
                        .mapInverted()
                        .also { cache.putAll(it) }
                }
                .map { (_, dto) -> dto.id }
                .let { dtoIds ->
                    // handle: delete
                    unitConfigs
                        .map { (_, unitConfig) -> strategy.run { unitConfig.toHassId() } to unitConfig }
                        .filterFirstNotNull()
                        .filter { (dtoId, _) -> dtoId !in dtoIds }
                        .onEach { (_, unitConfig) ->
                            LOGGER.info("Remove unit ${unitConfig.label.bestMatch()} since no corresponding dto in hass exist.")
                            runCatching { unitRegistry.removeUnitConfig(unitConfig).await() }
                                .getOrElse {
                                    ExceptionPrinter.printHistory(
                                        "Could not remove unit ${unitConfig.label.bestMatch()}.bestMatch() where no corresponding dto in hass exist.",
                                        it,
                                        LOGGER,
                                        LogLevel.WARN,
                                    )
                                }
                        }
                        .map { (_, unitConfig) -> unitConfig }
                        .also { unitConfigs -> cache.evictAllByUnitConfigs(unitConfigs) }
                }
        }
        LOGGER.info("Sync ${strategy.unitType.name.lowercase()}s from hass to bco done.")
    }

    private fun syncBCOtoHass() {
        LOGGER.info("Sync ${strategy.unitType.name.lowercase()}s from bco to hass...")
        getUnitConfigMap().values
            .map { unitConfig -> unitConfig to unitConfig.toHassInputDto() }
            .map { (unitConfig, inputDto) -> Triple(unitConfig, inputDto, cache.getDtoByUnitId(unitConfig.id)?.merge(inputDto)) }
            .let { pairs ->
                pairs
                    .filter { (_, _, hassDto) -> hassDto == null || hassDto != cache.getDtoById(hassDto.id) }
                    .map { (unitConfig, inputDto, hassDto) -> unitConfig to (hassDto?.toInputDto() ?: inputDto) }
                    .mapSecondNotNull { (unitConfig, inputDto) ->

                        LOGGER.info("Save hass dto ${inputDto.name} of corresponding unit ${unitConfig.label.bestMatch()}.")
                        runCatching { strategy.saveHassDto(inputDto) }
                            .getOrElse {
                                ExceptionPrinter.printHistory(
                                    "Could not save hass dto ${inputDto.name} of corresponding unit ${unitConfig.label.bestMatch()}.",
                                    it,
                                    LOGGER,
                                    LogLevel.WARN,
                                )
                                null
                            }
                    }
                    .also { cache.putAll(it) }
            }
            .map { (_, dto) -> dto }
            .associateBy { it.id }
            .let { dtos ->
                // handle: delete
                cache.dtos
                    .filter { dto -> dto.id !in dtos.keys }
                    .onEach { dto ->
                        LOGGER.info("Delete hass dto ${dto.name} since no corresponding unit in bco exist.")
                        runCatching { strategy.deleteHassDto(dto) }
                            .getOrElse {
                                ExceptionPrinter.printHistory(
                                    "Could not delete hass dto ${dto.name} where no corresponding unit in bco exist.",
                                    it,
                                    LOGGER,
                                    LogLevel.WARN,
                                )
                            }
                    }
            }
            .also { dtos -> cache.evictAllByDtos(dtos) }

        LOGGER.info("Sync ${strategy.unitType.name.lowercase()}s from bco to hass done.")
    }

    private fun getUnitConfigMap(): Map<String, UnitConfig> = unitRegistry
        .getUnitConfigsByUnitType(strategy.unitType)
        .filter(strategy.unitFilter)
        .associateBy { it.id }

    private fun HASS_DTO.toUnitConfig(): UnitConfig = strategy.buildUnitConfig(this)
    private fun UnitConfig.toHassInputDto(): HASS_INPUT_DTO = strategy.buildHassInputDto(this)

    companion object {
        private val LOGGER = LoggerFactory.getLogger(UnitSynchronizer::class.java)
    }
}
