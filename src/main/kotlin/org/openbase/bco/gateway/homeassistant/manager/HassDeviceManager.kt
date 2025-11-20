package org.openbase.bco.gateway.homeassistant.manager /*-
 * #%L
 * BCO Hass Device Manager
 * %%
 * Copyright (C) 2015 - 2021 openbase.org
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
import org.openbase.bco.dal.control.layer.unit.device.DeviceManagerImpl
import org.openbase.bco.dal.lib.layer.unit.UnitController
import org.openbase.bco.gateway.homeassistant.action.ServiceActionExecutor
import org.openbase.bco.gateway.homeassistant.communication.HassCommunicator
import org.openbase.bco.gateway.homeassistant.communication.HassCommunicator.Companion.EVENT_WS_SUBSCRIPTION
import org.openbase.bco.gateway.homeassistant.jp.*
import org.openbase.bco.gateway.homeassistant.manager.cache.HassIdToUnitControllerCache
import org.openbase.bco.gateway.homeassistant.manager.dto.*
import org.openbase.bco.gateway.homeassistant.manager.unit.HassGatewayControllerFactory
import org.openbase.bco.gateway.homeassistant.sync.DtoCache
import org.openbase.bco.gateway.homeassistant.sync.UnitSynchronizer
import org.openbase.bco.gateway.homeassistant.sync.strategy.TileSyncStrategy
import org.openbase.bco.gateway.homeassistant.sync.strategy.ZoneSyncStrategy
import org.openbase.bco.gateway.homeassistant.util.*
import org.openbase.bco.registry.remote.Registries
import org.openbase.bco.registry.remote.login.BCOLogin
import org.openbase.bco.registry.unit.lib.UnitRegistry
import org.openbase.jps.core.JPService
import org.openbase.jul.communication.jp.JPComHost
import org.openbase.jul.communication.jp.JPComPort
import org.openbase.jul.exception.CouldNotPerformException
import org.openbase.jul.exception.ExceptionProcessor
import org.openbase.jul.exception.printer.ExceptionPrinter
import org.openbase.jul.exception.printer.LogLevel
import org.openbase.jul.extension.type.processing.LabelProcessor
import org.openbase.jul.extension.type.processing.MultiLanguageTextProcessor
import org.openbase.jul.iface.Launchable
import org.openbase.jul.iface.VoidInitializable
import org.openbase.jul.pattern.Observer
import org.openbase.jul.pattern.provider.DataProvider
import org.openbase.jul.schedule.RecurrenceEventFilter
import org.openbase.type.domotic.unit.UnitConfigType.UnitConfig
import org.openbase.type.domotic.unit.UnitTemplateType.UnitTemplate.UnitType
import org.openbase.type.domotic.unit.device.DeviceClassType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.util.*

class HassDeviceManager :
    DeviceManagerImpl(HassGatewayControllerFactory(), false),
    Launchable<Void>,
    VoidInitializable {
    private val executor: ServiceActionExecutor

    private val hassIdToUnitControllerCache: HassIdToUnitControllerCache =
        HassIdToUnitControllerCache(unitControllerRegistry)

    /**
     * Synchronization observer that triggers resynchronization of all units if their configuration changes.
     */
    private val synchronizationObserver:
            Observer<DataProvider<MutableMap<String, UnitController<*, *>>>, MutableMap<String, UnitController<*, *>>>
    private lateinit var unitFilter: RecurrenceEventFilter<Any>

    private var entityIdToUnitId = mapOf<String, String>()

    private var supportedEntities = listOf<HassEntityDto>()

    private val tileAreaCache: DtoCache<HassAreaDto> = DtoCache()
    private val zoneFloorCache: DtoCache<HassFloorDto> = DtoCache()

    private val synchronizer = listOf(
        UnitSynchronizer(
            strategy = TileSyncStrategy(zoneFloorCache),
            cache = tileAreaCache,
            hassCommunicator = HassCommunicator.instance,
            unitRegistry = Registries.getUnitRegistry(),
        ),
        UnitSynchronizer(
            strategy = ZoneSyncStrategy(),
            cache = zoneFloorCache,
            hassCommunicator = HassCommunicator.instance,
            unitRegistry = Registries.getUnitRegistry(),
        ),
    )

    init {
        // the sync observer triggers a lot when the device manager is initially activated and all unit controllers are created
        this.unitFilter =
            object : RecurrenceEventFilter<Any>(5000) {
                @Throws(InterruptedException::class)
                override fun relay() {
                    // skip update if hass is not available but trigger again so the sync is performed later on.

                    if (!HassCommunicator.instance.isConnected) {
                        try {
                            unitFilter.triggerDelayed()
                        } catch (ex: CouldNotPerformException) {
                            ExceptionPrinter.printHistory("Could not delay unit sync task!", ex, LOGGER)
                        }
                        return
                    }

                    // wait for caches to be loaded
                    tileAreaCache.waitUntilReady()
                    zoneFloorCache.waitUntilReady()

                    val notIdentifiedDevices = mutableListOf<HassDeviceDto>()
                    val deviceClasses = Registries.getClassRegistry(true).deviceClasses
                    val deviceClassMapping: Map<String, Pair<HassDeviceDto, DeviceClassType.DeviceClass>> =
                        HassCommunicator.instance
                            .getDevices()
                            .filter { !it.model.isNullOrBlank() }
                            .map { hassDevice ->
                                hassDevice to
                                        deviceClasses
                                            .find { deviceClass ->
                                                deviceClass.productNumber == hassDevice.model ||
                                                        deviceClass.metaConfig[(ALIAS_KEY_HASS_DEVICE_MODEL)] == hassDevice.model
                                            }
                            }.filter { (hassDevice, deviceClass) ->
                                (deviceClass.isNotNull())
                                    .also {
                                        if (!it) {
                                            notIdentifiedDevices.add(hassDevice)
                                        }
                                    }
                            }.map { (hassDevice, deviceClass) -> hassDevice to deviceClass!! }
                            .onEach { (hassDevice, deviceClass) ->
                                LOGGER.debug(
                                    "found compatible device: {} served by {}",
                                    hassDevice,
                                    LabelProcessor.getBestMatch(deviceClass.label)
                                )
                            }.associateBy { (hassDevice, _) -> hassDevice.id }

                    val deviceIdToEntity: Map<String, List<HassEntityDto>> =
                        HassCommunicator.instance
                            .getEntities()
                            .groupBy { it.deviceId }

                    // ======= SYNC DEVICES ========
                    val deviceIdToDevices =
                        Registries
                            .getUnitRegistry()
                            .getUnitConfigsByUnitType(UnitType.DEVICE)
                            .filter { it.metaConfig.entryList.any { it.key == ALIAS_KEY_HASS_DEVICE_ID } }
                            .associateBy { it.metaConfig[ALIAS_KEY_HASS_DEVICE_ID] }

                    val entityIdToDeviceClass: Map<String, String?> =
                        HassCommunicator.instance.getStates().associate { it.entityId to it.deviceClass }

                    val dalUnitConfigs =
                        HassCommunicator.instance
                            .getDevices()
                            .filter { device -> deviceClassMapping[device.id].isNotNull() }
                            .map { device ->
                                UnitConfig
                                    .newBuilder()
                                    .setUnitType(UnitType.DEVICE)
                                    .apply {
                                        deviceClassMapping[device.id]!!.let { (_, deviceClass) ->
                                            deviceConfigBuilder.deviceClassId = deviceClass.id
                                        }
                                    }.setLabel(LabelProcessor.generateLabelBuilder(device.name))
                                    .apply { metaConfigBuilder[ALIAS_KEY_HASS_DEVICE_ID] = device.id }
                                    .apply {
                                        tileAreaCache.getUnitConfigByDtoId(device.areaId)
                                            ?.let { unitLocation ->
                                                placementConfigBuilder.locationId = unitLocation.id
                                            }
                                    }.build()
                            }.map { deviceConfig ->
                                deviceIdToDevices[deviceConfig.metaConfig[ALIAS_KEY_HASS_DEVICE_ID]]?.let { existingDeviceConfig ->
                                    existingDeviceConfig.toBuilder().mergeFromWithRepeatedFields(deviceConfig).build()
                                        .let {
                                            Registries.getUnitRegistry().updateUnitConfig(it).await()
                                        }
                                } ?: Registries.getUnitRegistry().registerUnitConfig(deviceConfig).await()
                            }.flatMap { deviceConfig ->
                                deviceConfig.deviceConfig.unitIdList.map { dalUnitId ->
                                    val unit = Registries.getUnitRegistry().getUnitConfigById(dalUnitId)
                                    val metaConfig = unit.unitType
                                        .let { Registries.getTemplateRegistry().getUnitTemplateByType(it) }
                                        .metaConfig
                                    val entityType = metaConfig[HASS_ENTITY_TYPE]
                                    val entityDeviceClass = metaConfig[HASS_ENTITY_DEVICE_CLASS]

                                    deviceIdToEntity[deviceConfig.metaConfig[ALIAS_KEY_HASS_DEVICE_ID]]
                                        ?.filter { entityIdToDeviceClass[it.entityId] == entityDeviceClass }
                                        ?.find { it.type == entityType }
                                        ?.let { entity ->
                                            unit
                                                .toBuilder()
                                                .apply {
                                                    // set hass id as alias and add to meta config
                                                    addAlias(entity.entityId)
                                                    metaConfigBuilder[ALIAS_KEY_HASS_ENTITY_ID] = entity.entityId
                                                    // add location to unit
                                                    tileAreaCache.getUnitConfigByDtoId(entity.areaId)
                                                        ?.let { unitLocation ->
                                                            placementConfigBuilder.locationId = unitLocation.id
                                                        }
                                                }.build()
                                        }?.let { Registries.getUnitRegistry().updateUnitConfig(it).await() }
                                }
                            }.filterNotNull()

                    // TODO: Location and Device initial sync draft is ready, however we have issues with repeated field that are not merged correctly.

                    // initial device synchronization
                    supportedEntities =
                        HassCommunicator.instance
                            .getEntities()
                            .filter { deviceIdToDevices.keys.contains(it.deviceId) }

                    try {
                        HassCommunicator.instance.getStates().applyStateUpdates(systemSync = true)
                    } catch (ex: CouldNotPerformException) {
                        if (!ExceptionProcessor.isCausedBySystemShutdown(ex)) {
                            ExceptionPrinter.printHistory(
                                "Could not retrieve item states from hass!",
                                ex,
                                LOGGER,
                                LogLevel.WARN,
                            )
                        }
                    }

                    notIdentifiedDevices
                        .takeIf { it.isNotEmpty() }
                        ?.also { println("The following devices are found but not yet compatible with bco:") }
                        ?.distinct()
                        ?.forEach { println("${it.name}: ${it.model}") }
                }
            }
        this.executor = ServiceActionExecutor(hassIdToUnitControllerCache)
        this.synchronizationObserver =
            (Observer { observable: Any?, value: Any? -> unitFilter.trigger() })
    }

    override fun isGatewaySupported(config: UnitConfig?): Boolean =
        config?.gatewayConfig?.gatewayClassId == HASS_GATEWAY_CLASS_ID

    override fun isUnitSupported(config: UnitConfig): Boolean =
        config.metaConfig.entryList.any { it.key == ALIAS_KEY_HASS_DEVICE_ID }

    @Throws(CouldNotPerformException::class, InterruptedException::class)
    override fun activate() {

        LOGGER.info("#################################################")
        LOGGER.info("### Homeassistant  Host:  ${JPService.getValue(JPHassHost::class.java)}")
        LOGGER.info("### Homeassistant  Port:  ${JPService.getValue(JPHassPort::class.java)}")
        LOGGER.info("### Homeassistant Token: ${JPService.getValue(JPHassToken::class.java)}")
        LOGGER.info("### Homeassistant  Websocket: ${JPService.getValue(JPHassWebsocketEndpoint::class.java)}")
        LOGGER.info("###           BCO  Host: ${JPService.getValue(JPComHost::class.java)}")
        LOGGER.info("###           BCO  Port: ${JPService.getValue(JPComPort::class.java)}")
        LOGGER.info("###     BCO Admin  User: ${JPService.getValue(JPBcoAdminUsername::class.java)}")
        LOGGER.info("#################################################J")


        unitControllerRegistry.addObserver(synchronizationObserver)

        HassCommunicator.instance.subscribe(
            EVENT_WS_SUBSCRIPTION,
            HassCommunicator.HassEventType.STATE_UPDATE
        ) { event ->
            LOGGER.trace("new state event: {}", event)
            listOf(event.data.newState).applyStateUpdates(systemSync = false)
        }

        LOGGER.info("Connect to bco...")
        Registries.waitUntilReady()

        LOGGER.info("Login to bco...")

        loginToBCO()
        registerGatewayIfMissing()

        synchronizer.map { it.activate() }

        super.activate()

        unitFilter.trigger()
    }

    private fun loginToBCO() {
        if (!BCOLogin.getSession().isLoggedIn) {
            try {
                // home assistant addon environment: try to auto login default user
                BCOLogin.getSession().loginUserViaUsername(HASS_BCO_USER, true)
            } catch (ex: CouldNotPerformException) {
                // create or recover account
                createAndLoginNewHassUser()
            }
        }
    }

    private fun registerGatewayIfMissing() {
        val existingGateway = Registries.getUnitRegistry().getUnitConfigsByUnitType(UnitType.GATEWAY)
            .firstOrNull { it.gatewayConfig.gatewayClassId == HASS_GATEWAY_CLASS_ID }

        if (existingGateway == null) {
            LOGGER.info("No Home Assistant gateway found in BCO. Registering new gateway...")

            val gatewayConfig =
                UnitConfig.newBuilder().apply {
                    unitType = UnitType.GATEWAY
                    label = LabelProcessor.generateLabelBuilder("Home Assistant").build()
                    description = MultiLanguageTextProcessor.generateMultiLanguageTextBuilder(
                        "This is the connection to Home Assistant."
                    ).build()
                    gatewayConfigBuilder.apply {
                        gatewayClassId = HASS_GATEWAY_CLASS_ID
                    }
                }.build()

            Registries.getUnitRegistry().registerUnitConfig(gatewayConfig).await()

            LOGGER.info("Home Assistant gateway registered.")
        } else {
            LOGGER.info("Home Assistant gateway already registered in BCO: {}", existingGateway.id)
        }
    }

    fun createAndLoginNewHassUser() {
        // login via admin account
        val adminUser = JPService.getValue(JPBcoAdminUsername::class.java)
        val adminPassword = JPService.getValue(JPBcoAdminPassword::class.java)
        BCOLogin.getSession().loginUserViaUsername(adminUser, adminPassword, false)

        // remove existing hass user if any
        Registries.getUnitRegistry().getUnitConfigsByUnitType(UnitType.USER)
            .firstOrNull { it.userConfig.userName == HASS_BCO_USER }
            ?.let { userConfig ->
                LOGGER.info("Removing existing Home Assistant user with id {}...", userConfig)
                Registries.getUnitRegistry().removeUnitConfig(userConfig).await()
            }

        // register hass user
        val hassUser = UnitConfig.newBuilder().apply {
            unitType = UnitType.USER
            label = LabelProcessor.generateLabelBuilder("Home Assistant").build()
            description = MultiLanguageTextProcessor.generateMultiLanguageTextBuilder(
                "This user account is used to authorize Home Assistant to access BCO."
            ).build()
            userConfigBuilder.apply {
                userName = HASS_BCO_USER
                firstName = "Home"
                lastName = "Assistant"
                setSystemUser(true)
            }
        }.build().let { Registries.getUnitRegistry().registerUnitConfig(it).await() }

        // register hass user as bco admin (needed to maintain the setup)
        Registries.getUnitRegistry().getUnitConfigByAlias(UnitRegistry.ADMIN_GROUP_ALIAS).toBuilder().apply {
            authorizationGroupConfigBuilder.apply {
                addMemberId(hassUser.id)
            }
        }.build().also { Registries.getUnitRegistry().updateUnitConfig(it).await() }

        val hassUserPassword = generateSecurePassword() // generate random but secure password

        // setup password for hass user and save it in local credential store
        val credentials = BCOLogin.getSession().sessionManager.registerUser(hassUser.id, hassUserPassword, true).await()

        // login new hass user and locally store credentials
        BCOLogin.getSession().logout()
        BCOLogin.getSession().loginUserViaUsername(HASS_BCO_USER, hassUserPassword, true)
        BCOLogin.getSession().storeCredentials(hassUser.id, credentials)
    }

    fun List<HassStateDto>.applyStateUpdates(systemSync: Boolean) =
        this
            .filter { supportedEntities.map { it.entityId }.contains(it.entityId) }
            .forEach { state ->
                try {
                    executor.applyStateUpdate(
                        hassState = state,
                        systemSync = systemSync,
                    )
                } catch (ex: CouldNotPerformException) {
                    ExceptionPrinter.printHistory(
                        "Skip synchronization of item[name: ${state.name}, type: ${state.type}, state: ${state.state}]",
                        ex,
                        LOGGER,
                        LogLevel.WARN,
                    )
                }
            }

    @Throws(CouldNotPerformException::class, InterruptedException::class)
    override fun deactivate() {
        unitControllerRegistry.removeObserver(synchronizationObserver)
        synchronizer.map { deactivate() }
        super.deactivate()
    }

    companion object {
        const val ALIAS_KEY_HASS_DEVICE_MODEL = "HASS_DEVICE_MODEL"
        const val ALIAS_KEY_HASS_ID = "HASS_ID"
        const val ALIAS_KEY_HASS_TYPE = "HASS_TYPE"
        const val ALIAS_KEY_HASS_DEVICE_ID = "HASS_DEVICE_ID"
        const val HASS_GATEWAY_CLASS_ID = "96dd4c43-92de-48b6-ba16-f9bafefc3c44"
        const val HASS_ENTITY_TYPE = "HASS_ENTITY_TYPE"
        const val HASS_ENTITY_DEVICE_CLASS = "HASS_ENTITY_DEVICE_CLASS"
        const val ALIAS_KEY_HASS_ENTITY_ID = "HASS_ENTITY_ID"

        const val ALIAS_KEY_BCO_ICON = "ICON"

        const val HASS_BCO_USER: String = "homeassistant"

        private val LOGGER: Logger = LoggerFactory.getLogger(HassDeviceManager::class.java)
    }

    // Generate a secure random password encoded as URL-safe Base64 without padding.
    private fun generateSecurePassword(bytesLength: Int = 24): String {
        val random = SecureRandom()
        val bytes = ByteArray(bytesLength)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
