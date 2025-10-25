package org.openbase.bco.gateway.homeassistant.sync

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.openbase.bco.gateway.homeassistant.communication.HassCommunicator
import org.openbase.bco.gateway.homeassistant.manager.HassDeviceManager
import org.openbase.bco.gateway.homeassistant.manager.dto.HassDto
import org.openbase.bco.gateway.homeassistant.manager.dto.HassInputDto
import org.openbase.bco.gateway.homeassistant.sync.strategy.UnitSyncStrategy
import org.openbase.bco.gateway.homeassistant.type.InputDtoProvider
import org.openbase.bco.gateway.homeassistant.type.Mergeable
import org.openbase.bco.gateway.homeassistant.util.*
import org.openbase.bco.registry.unit.remote.UnitRegistryRemote
import org.openbase.jul.exception.printer.ExceptionPrinter
import org.openbase.jul.exception.printer.LogLevel
import org.openbase.jul.iface.Activatable
import org.openbase.type.domotic.state.ConnectionStateType
import org.openbase.type.domotic.unit.UnitConfigType.UnitConfig
import org.slf4j.LoggerFactory
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class UnitSynchronizer<HASS_DTO, HASS_INPUT_DTO : HassInputDto>(
    val strategy: UnitSyncStrategy<HASS_DTO, HASS_INPUT_DTO>,
    val cache: DtoCache<HASS_DTO> = DtoCache(),
    val hassCommunicator: HassCommunicator,
    val unitRegistry: UnitRegistryRemote,
) : Activatable where
HASS_DTO : HassDto,
HASS_DTO : Mergeable<HASS_INPUT_DTO, HASS_DTO>,
HASS_DTO : InputDtoProvider<HASS_INPUT_DTO> {

    private val activationMutex = Mutex()
    private var observer = listOf<AutoCloseable>()

    private val bcoToHassSyncDebounceFilter = DebounceFilter<Unit> { synchronizationLock.withLock { syncBCOtoHass() } }
    private val hassToBCOSyncDebounceFilter = DebounceFilter<Unit> { synchronizationLock.withLock { syncHassToBCO() } }

    private var active = false

    private var synchronizationLock = ReentrantLock()

    @OptIn(DelicateCoroutinesApi::class)
    override fun activate() {
        GlobalScope.launch {
            try {
                activationMutex.withLock {
                    if (this@UnitSynchronizer.isActive) return@withLock

                    active = true

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
            } catch (e: Exception) {
                active = false
                throw e
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun deactivate() {
        GlobalScope.launch {
            activationMutex.withLock {
                observer.forEach(AutoCloseable::close)
                active = false
            }
        }
    }

    override fun isActive(): Boolean = active

    private fun syncAll() {
        synchronizationLock.withLock {
            syncHassToBCO() // order important for sync!
            syncBCOtoHass() // order important for sync!
        }
        cache.confirmInit()
    }

    private fun syncHassToBCO() {

        LOGGER.info("Sync ${strategy.unitType.name.lowercase()}s from hass to bco...")
        val unitConfigs = getUnitConfigMap()
        val unitConfigByDtoId = unitConfigs.values.associateBy { it.metaConfig[HassDeviceManager.ALIAS_KEY_HASS_ID] }

        strategy.queryHassDtos().let { hassDtos ->
            // handle: add and update
            hassDtos
                .map { hassDto -> hassDto to hassDto.toUnitConfig() }
                .mapSecond { (hassDto, unitConfig) ->
                    run {
                        cache.getUnitConfigByDtoId(hassDto.id)
                            ?: unitConfigByDtoId[hassDto.id]
                    }
                        ?.toBuilder()
                        ?.mergeFromWithRepeatedFields(unitConfig)
                        ?.build()
                        ?: unitConfig
                }
                .also { pairs ->
                    pairs
                        .filter { (_, unitConfig) -> unitConfig != cache.getUnitConfigById(unitConfig.id) }
                        .mapSecondNotNull { (hassDto, unitConfig) ->
                            if (unitConfigByDtoId[hassDto.id] != unitConfig) {
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
                            } else {
                                unitConfig
                            }
                        }
                        .mapInverted()
                        .also { cache.putAll(it) }
                }
                .map { (dto, _) -> dto.id }
                .let { dtoIds ->
                    // handle: delete
                    unitConfigs
                        .map { (_, unitConfig) -> strategy.run { unitConfig.toHassId() } to unitConfig }
                        .filterFirstNotNull()
                        .filter { (dtoId, _) -> dtoId !in dtoIds }
                        .filterNot { (_, unitConfig) -> unitConfig.locationConfig.root }
                        .onEach { (_, unitConfig) ->
                            LOGGER.info("Remove unit ${unitConfig.label.bestMatch()} since no corresponding dto in hass exist.")
                            runCatching { unitRegistry.removeUnitConfig(unitConfig).await() }
                                .getOrElse {
                                    ExceptionPrinter.printHistory(
                                        "Could not remove unit ${unitConfig.label.bestMatch()} where no corresponding dto in hass exist.",
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

    inner class SyncObject(
        var unitConfig: UnitConfig,
        val hassDtoMap: Map<String, HASS_DTO>
    ) {
        val incomingInputDto: HASS_INPUT_DTO = unitConfig.toHassInputDto()
        var hassDto: HASS_DTO? = run {
            cache.getDtoByUnitId(unitConfig.id)
                ?: hassDtoMap[unitConfig.metaConfig[HassDeviceManager.ALIAS_KEY_HASS_ID]]
        }?.merge(incomingInputDto)
        val inputDto: HASS_INPUT_DTO = hassDto?.toInputDto() ?: incomingInputDto
        val changed: Boolean = hassDto?.let {
            hassDto != (cache.getDtoById(it.id)
                ?: hassDtoMap[unitConfig.metaConfig[HassDeviceManager.ALIAS_KEY_HASS_ID]])
        } ?: true
        var skipped: Boolean = false
    }

    private fun syncBCOtoHass() {
        LOGGER.info("Sync ${strategy.unitType.name.lowercase()}s from bco to hass...")
        val hassDtoMap: Map<String, HASS_DTO> = strategy.queryHassDtos().associateBy { it.id }
        getUnitConfigMap().values
            .map { unitConfig -> SyncObject(unitConfig = unitConfig, hassDtoMap = hassDtoMap) }
            .onEach { sync ->
                if (sync.changed) {
                    LOGGER.info("Save hass dto ${sync.inputDto.name} of corresponding unit ${sync.unitConfig.label.bestMatch()}.")
                    runCatching {
                        strategy
                            .saveHassDto(sync.inputDto)
                            .also { hassDto -> sync.hassDto = hassDto }
                            .also { hassDto ->
                                strategy.run {
                                    sync.unitConfig
                                        .link(hassDto)
                                        .build()
                                        .takeIf { it != sync.unitConfig }
                                        ?.let { newUnitConfig ->
                                            sync.unitConfig = unitRegistry
                                                .saveUnitConfig(newUnitConfig)
                                                .await()
                                        }
                                }
                            }
                            .also { hassDto -> cache.put(sync.unitConfig, hassDto) }
                    }
                        .getOrElse {
                            ExceptionPrinter.printHistory(
                                "Could not save hass dto ${sync.inputDto.name} of corresponding unit ${sync.unitConfig.label.bestMatch()}.",
                                it,
                                LOGGER,
                                LogLevel.WARN,
                            )
                            sync
                            sync.skipped = true
                        }
                }
            }
            .associateBy { it.hassDto?.id }
            .takeIf { it.keys.contains(null).not() } // todo: what is this
            ?.let { syncs ->

                // handle: delete
                cache.dtos
                    .filter { dto -> dto.id !in syncs.keys }
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
                    .also { dtos -> cache.evictAllByDtos(dtos) }
            }


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
