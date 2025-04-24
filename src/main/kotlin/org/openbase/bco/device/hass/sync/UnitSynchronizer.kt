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
import org.openbase.bco.device.hass.util.Mergeable
import org.openbase.bco.device.hass.util.saveUnitConfig
import org.openbase.bco.registry.remote.Registries
import org.openbase.bco.registry.unit.lib.UnitRegistry
import org.openbase.jul.exception.tryOrNull
import org.openbase.jul.extension.protobuf.ProtoBufBuilderProcessor.mergeFromWithoutRepeatedFields
import org.openbase.jul.iface.Activatable
import org.openbase.type.domotic.state.ConnectionStateType
import org.openbase.type.domotic.unit.UnitConfigType.UnitConfig
import org.slf4j.LoggerFactory

class UnitSynchronizer<HASS_DTO, HASS_INPUT_DTO : HassInputDto>(
    val strategy: UnitSyncStrategy<HASS_DTO, HASS_INPUT_DTO>,
    val hassCommunicator: HassCommunicator = HassCommunicator.instance,
    val unitRegistry: UnitRegistry = Registries.getUnitRegistry(),
) : Activatable where
HASS_DTO : HassDto,
HASS_DTO : Mergeable<HASS_INPUT_DTO, HASS_DTO>,
HASS_DTO : InputDtoProvider<HASS_INPUT_DTO> {

    private var coroutineScope: CoroutineScope? = null
    private val debounceDuration = 500L
    private val activationMutex = Mutex()
    private var observer = listOf<AutoCloseable>()

    val dtoCache = mutableMapOf<String, HASS_DTO>()
    var unitIdToDtoCache: Map<String, HASS_DTO> = emptyMap()
    var dtoIdToUnitIdCache: Map<String, String> = emptyMap()

    private val bcoToHassSyncTrigger = MutableSharedFlow<kotlin.Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val hassToBCOSyncTrigger = MutableSharedFlow<kotlin.Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private fun initTrigger(scope: CoroutineScope) {
        scope.launch {
            bcoToHassSyncTrigger
                .debounce(debounceDuration)
                .collect {
                    LOGGER.info("Debounced syncAll triggered")
                    syncBCOtoHass()
                }
            hassToBCOSyncTrigger
                .debounce(debounceDuration)
                .collect {
                    LOGGER.info("Debounced syncAll triggered")
                    syncHassToBCO()
                }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun activate() {
        GlobalScope.launch {
            activationMutex.withLock {
                if (isActive) return@withLock

                CoroutineScope(SupervisorJob() + Dispatchers.IO)
                    .also { coroutineScope = it }
                    .let { initTrigger(it) }


                if (!hassCommunicator.isConnected) {
                    LOGGER.info("Waiting for hass connection...")
                    hassCommunicator.waitForConnectionState(ConnectionStateType.ConnectionState.State.CONNECTED)
                }

                strategy.onDtoChanges() {
                    triggerHassToBCOSync()
                }.also { observer += it }

                strategy.onUnitChanges() {
                    triggerBCOToHassSync()
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
        syncHassToBCO()
        syncBCOtoHass()
    }

    private fun triggerHassToBCOSync() = hassToBCOSyncTrigger.tryEmit(Unit)
    private fun triggerBCOToHassSync() = bcoToHassSyncTrigger.tryEmit(Unit)

    private fun syncHassToBCO() {
        strategy.queryHassDtos()
            .map { hassDto -> hassDto to hassDto.toUnitConfig() }
            .map { (hassDto, unitConfig) ->
                dtoIdToUnitIdCache[hassDto.id]
                    .tryOrNull { unitRegistry.getUnitConfigById(it) }
                    ?.toBuilder()
                    ?.mergeFromWithoutRepeatedFields(unitConfig)
                    ?.build()
                    ?: unitConfig
            }
            // todo: do only on change!
            .map { unitConfig -> unitRegistry.saveUnitConfig(unitConfig) }
    }

    private fun syncBCOtoHass() {
        unitRegistry
            .getUnitConfigsByUnitType(strategy.unitType)
            .filter(strategy.unitFilter)
            .map { unitConfig -> unitConfig to unitConfig.toHassInputDto() }
            .mapNotNull { (unitConfig, input) -> unitIdToDtoCache[unitConfig.id]?.merge(input) }
            .filter { hassDto -> hassDto != dtoCache[hassDto.id] }
            .map { it.toInputDto() }
            .let { strategy.saveHassDtos(it) }
            .let { dtoCache.putAll(it.associateBy { it.id }) }
    }

    private fun HASS_DTO.toUnitConfig(): UnitConfig = strategy.buildUnitConfig(this)
    private fun UnitConfig.toHassInputDto(): HASS_INPUT_DTO = strategy.buildHassInputDto(this)

    companion object {
        private val LOGGER = LoggerFactory.getLogger(UnitSynchronizer::class.java)
    }
}
