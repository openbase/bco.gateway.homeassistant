package org.openbase.bco.device.hass.communication

import com.google.gson.*
import jakarta.ws.rs.ProcessingException
import jakarta.ws.rs.client.Client
import jakarta.ws.rs.client.ClientBuilder
import jakarta.ws.rs.client.Entity
import jakarta.ws.rs.client.WebTarget
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriBuilder
import org.openbase.bco.device.hass.jp.JPHassHost
import org.openbase.bco.device.hass.jp.JpHassPort
import org.glassfish.jersey.client.oauth2.OAuth2ClientSupport
import org.openbase.bco.device.hass.communication.websocket.HassWebsocketConnection
import org.openbase.bco.registry.remote.Registries
import org.openbase.jps.core.JPService
import org.openbase.jps.exception.JPNotAvailableException
import org.openbase.jul.exception.*
import org.openbase.jul.exception.ExceptionProcessor.setInitialCause
import org.openbase.jul.extension.type.processing.LabelProcessor.contains
import org.openbase.jul.extension.type.processing.MetaConfigProcessor
import org.openbase.jul.iface.Shutdownable
import org.openbase.jul.pattern.ObservableImpl
import org.openbase.jul.pattern.Observer
import org.openbase.jul.schedule.GlobalScheduledExecutorService
import org.openbase.type.domotic.state.ConnectionStateType
import org.openbase.type.domotic.unit.UnitTemplateType.UnitTemplate.UnitType
import org.openbase.type.domotic.unit.gateway.GatewayClassType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.ConnectException
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

abstract class HassConnection : Shutdownable, TokenProvider {
    private val topicObservableMapLock = ReentrantLock()
    private val connectionStateLock = ReentrantLock()
    private val connectionStateCondition = connectionStateLock.newCondition()
    private var topicObservableMap: MutableMap<String, ObservableImpl<Any, JsonObject>>

    private var restClient: Client
    private var restTarget: WebTarget
    private var webSocketConnection: HassWebsocketConnection = HassWebsocketConnection(this)

    var isShutdownInitiated: Boolean = false
        private set

    protected var gson: Gson

    private var connectionTask: ScheduledFuture<*>? = null

    var hassConnectionState: ConnectionStateType.ConnectionState.State =
        ConnectionStateType.ConnectionState.State.DISCONNECTED
        protected set

    final override val token: String
        get() = findHassGatewayClass()?.let { hassGatewayClass ->
            Registries.getUnitRegistry()
                .getUnitConfigsByUnitType(UnitType.GATEWAY)
                .find { it.gatewayConfig.gatewayClassId == hassGatewayClass.id }
                ?.let { MetaConfigProcessor.getValue(it.metaConfig, META_CONFIG_TOKEN_KEY) }
        }?: error("Home Assistant token missing!")

    init {
        try {
            this.topicObservableMap = HashMap()
            this.gson = GsonBuilder().setExclusionStrategies(object : ExclusionStrategy {
                override fun shouldSkipField(fieldAttributes: FieldAttributes): Boolean {
                    return false
                }

                override fun shouldSkipClass(aClass: Class<*>): Boolean {
                    // ignore Command Description because its an interface and can not be serialized without any instance creator.
                    return false
                }
            }).create()

            this.restClient = ClientBuilder.newClient()
            try {
                restClient.register(OAuth2ClientSupport.feature(token))
            } catch (ex: NotAvailableException) {
                LOGGER.warn("Could not retrieve Hass token from gateway config!", ex)
            }

            val hassUri = UriBuilder.fromUri("http://${JPService.getValue(JPHassHost::class.java)}")
                .port(JPService.getValue(JpHassPort::class.java))
                .path(REST_ENDPOINT)
            this.restTarget = restClient.target(hassUri)
            this.setConnectState(ConnectionStateType.ConnectionState.State.CONNECTING)
        } catch (ex: JPNotAvailableException) {
            throw InstantiationException(this, ex)
        } catch (ex: CouldNotPerformException) {
            throw InstantiationException(this, ex)
        }
    }

    private val isTargetReachable: Boolean
        get() = runCatching { testConnection() }.isSuccess

    @Throws(CouldNotPerformException::class)
    protected abstract fun testConnection()

    @Throws(InterruptedException::class)
    fun waitForConnectionState(
        connectionState: ConnectionStateType.ConnectionState.State,
        timeout: Long,
        timeUnit: TimeUnit,
    ) {
        connectionStateLock.withLock {
            while (hassConnectionState != connectionState) {
                connectionStateCondition.await(timeout, timeUnit)
            }
        }
    }

    @Throws(InterruptedException::class)
    fun waitForConnectionState(connectionState: ConnectionStateType.ConnectionState.State) {
        connectionStateLock.withLock {
            while (hassConnectionState != connectionState) {
                connectionStateCondition.await()
            }
        }
    }

    private fun setConnectState(connectState: ConnectionStateType.ConnectionState.State) {
        connectionStateLock.withLock {
            // filter non changing states
            if (connectState == this.hassConnectionState) {
                return
            }
            LOGGER.trace("Hass Connection State changed to: {}", connectState)

            // update state
            this.hassConnectionState = connectState

            when (connectState) {
                ConnectionStateType.ConnectionState.State.CONNECTING -> {
                    LOGGER.info("Wait for hass...")
                    try {
                        connectionTask?.cancel(true)
                        connectionTask = GlobalScheduledExecutorService.scheduleWithFixedDelay({
                            if (isTargetReachable) {
                                // set connected
                                setConnectState(ConnectionStateType.ConnectionState.State.CONNECTED)

                                // cleanup own task
                                connectionTask?.cancel(false)
                            }
                        }, 0, 15, TimeUnit.SECONDS)
                    } catch (ex: NotAvailableException) {
                        // if global executor service is not available we have no chance to connect.
                        LOGGER.warn("Wait for hass...", ex)
                        setConnectState(ConnectionStateType.ConnectionState.State.DISCONNECTED)
                    } catch (ex: RejectedExecutionException) {
                        LOGGER.warn("Wait for hass...", ex)
                        setConnectState(ConnectionStateType.ConnectionState.State.DISCONNECTED)
                    }
                }

                ConnectionStateType.ConnectionState.State.CONNECTED -> {
                    LOGGER.info("Connection to Hass established.")
                    webSocketConnection.activate()
                }

                ConnectionStateType.ConnectionState.State.RECONNECTING -> {
                    LOGGER.warn("Connection to Hass lost!")
                    resetConnection()
                    setConnectState(ConnectionStateType.ConnectionState.State.CONNECTING)
                }

                ConnectionStateType.ConnectionState.State.DISCONNECTED -> {
                    LOGGER.info("Connection to Hass closed.")
                    resetConnection()
                }

                else -> {
                    LOGGER.warn("Unknown connection state: $connectState")
                }
            }
            // notify state change
            connectionStateCondition.signalAll()
            if (connectState == ConnectionStateType.ConnectionState.State.RECONNECTING) {
                setConnectState(ConnectionStateType.ConnectionState.State.CONNECTING)
            }
        }
    }

//    private fun initWebsocket() {

//        webSocketConnection.HassWebsocketConnection
//        if (websocketSource != null) {
//            LOGGER.warn("WEBSOCKET already initialized!")
//            return
//        }
//
//        websocketSource = WebsocketEventSource
//            .target(restTarget.path(EVENTS_TARGET))
//            .reconnectingEvery(15, TimeUnit.SECONDS)
//            .build()
//            .also { it.open() }
//
//
//        val evenConsumer = Consumer { inboundWebsocketEvent: InboundWebsocketEvent ->
//            // dispatch event
//            try {
//                val payload = JsonParser.parseString(inboundWebsocketEvent.readData()).asJsonObject
//                for ((key, value) in topicObservableMap) {
//                    try {
//                        payload[TOPIC_KEY]
//                            ?.asString
//                            ?.takeIf { it.matches(key.toRegex()) }
//                            ?.run { value.notifyObservers(payload) }
//                    } catch (ex: Exception) {
//                        ExceptionPrinter.printHistory(
//                            CouldNotPerformException(
//                                "Could not notify listeners on topic[$key]",
//                                ex
//                            ), LOGGER
//                        )
//                    }
//                }
//            } catch (ex: Exception) {
//                ExceptionPrinter.printHistory(CouldNotPerformException("Could not handle WEBSOCKET payload!", ex), LOGGER)
//            }
//        }
//
//        val errorHandler = Consumer { ex: Throwable ->
//            ExceptionPrinter.printHistory("Hass connection error detected!", ex, LOGGER, LogLevel.DEBUG)
//            checkConnectionState()
//        }
//
//        val reconnectHandler = Runnable {
//            setConnectState(ConnectionStateType.ConnectionState.State.RECONNECTING)
//        }
//
//        websocketSource?.register(evenConsumer, errorHandler, reconnectHandler)
//    }

    private fun checkConnectionState() {
        connectionStateLock.withLock {
            // only validate if connected
            if (!isConnected) {
                return
            }

            // if not reachable init a reconnect
            if (!isTargetReachable) {
                setConnectState(ConnectionStateType.ConnectionState.State.RECONNECTING)
            }
        }
    }

    val isConnected: Boolean
        get() = hassConnectionState == ConnectionStateType.ConnectionState.State.CONNECTED

    @JvmOverloads
    fun addWebsockedObserver(observer: Observer<Any, JsonObject>, topicRegex: String = "") {
        topicObservableMapLock.withLock {
            topicObservableMap
                .getOrPut(topicRegex) { ObservableImpl<Any, JsonObject>(this) }
                .addObserver(observer)
        }
    }

    @JvmOverloads
    fun removeWebsockedObserver(observer: Observer<Any, JsonObject>, topicFilter: String = "") {
        topicObservableMapLock.withLock {
            topicObservableMap[topicFilter]?.removeObserver(observer)
        }
    }

    private fun resetConnection() {
        // cancel ongoing connection task
        connectionTask
            ?.takeIf { !it.isDone }
            ?.cancel(false)


        webSocketConnection.deactivate()
    }

    @Throws(CouldNotPerformException::class)
    fun validateConnection() {
        if (!isConnected) {
            throw InvalidStateException("Hass not reachable yet!")
        }
    }

    @Throws(CouldNotPerformException::class, ProcessingException::class)
    private fun validateResponse(response: Response, skipConnectionValidation: Boolean = false): String =
        response.readEntity(String::class.java).let { result ->
            when (response.status) {
                200, 201, 202 -> {
                    result
                }

                404 -> {
                    if (!skipConnectionValidation) {
                        checkConnectionState()
                    }
                    throw NotAvailableException("URL")
                }

                503 -> {
                    if (!skipConnectionValidation) {
                        checkConnectionState()
                    }
                    // throw a processing exception to indicate that hass is still not fully started, this is used to wait for hass
                    throw ProcessingException("Hass server not ready")
                }

                else -> {
                    throw CouldNotPerformException("Response returned with ErrorCode[" + response.status + "], Result[" + result + "] and ErrorMessage[" + response.statusInfo.reasonPhrase + "]")
                }
            }
        }

    @Throws(CouldNotPerformException::class)
    protected fun get(target: String, skipValidation: Boolean = false): String {
        try {
            // handle validation

            if (!skipValidation) {
                validateConnection()
            }

            val webTarget = restTarget.path(target)
            val response = webTarget.request().get()

            return validateResponse(response, skipValidation)
        } catch (ex: CouldNotPerformException) {
            if (isShutdownInitiated) {
                setInitialCause(ex, ShutdownInProgressException(this))
            }
            throw CouldNotPerformException("Could not get sub-URL[$target]", ex)
        } catch (ex: ProcessingException) {
            if (isShutdownInitiated) {
                setInitialCause(ex, ShutdownInProgressException(this))
            }
            throw CouldNotPerformException("Could not get sub-URL[$target]", ex)
        }
    }

    @Throws(CouldNotPerformException::class)
    protected fun delete(target: String): String {
        try {
            validateConnection()
            val webTarget = restTarget.path(target)
            val response = webTarget.request().delete()

            return validateResponse(response)
        } catch (ex: CouldNotPerformException) {
            if (isShutdownInitiated) {
                setInitialCause(ex, ShutdownInProgressException(this))
            }
            throw CouldNotPerformException("Could not delete sub-URL[$target]", ex)
        } catch (ex: ProcessingException) {
            if (isShutdownInitiated) {
                setInitialCause(ex, ShutdownInProgressException(this))
            }
            throw CouldNotPerformException("Could not delete sub-URL[$target]", ex)
        }
    }

    @Throws(CouldNotPerformException::class)
    protected fun putJson(target: String, value: Any?): String {
        return put(target, gson.toJson(value), MediaType.APPLICATION_JSON_TYPE)
    }

    @Throws(CouldNotPerformException::class)
    protected fun put(target: String, value: String, mediaType: MediaType?): String {
        try {
            validateConnection()
            val webTarget = restTarget.path(target)
            val response = webTarget.request().put(Entity.entity(value, mediaType))

            return validateResponse(response)
        } catch (ex: CouldNotPerformException) {
            if (isShutdownInitiated) {
                setInitialCause(ex, ShutdownInProgressException(this))
            }
            throw CouldNotPerformException("Could not put value[$value] on sub-URL[$target]", ex)
        } catch (ex: ProcessingException) {
            if (isShutdownInitiated) {
                setInitialCause(ex, ShutdownInProgressException(this))
            }
            throw CouldNotPerformException("Could not put value[$value] on sub-URL[$target]", ex)
        } catch (ex: ConnectException) {
            if (isShutdownInitiated) {
                setInitialCause(ex, ShutdownInProgressException(this))
            }
            throw CouldNotPerformException("Could not put value[$value] on sub-URL[$target]", ex)
        }
    }

    @Throws(CouldNotPerformException::class)
    protected fun postJson(target: String, value: Any?): String {
        return post(target, gson.toJson(value), MediaType.APPLICATION_JSON_TYPE)
    }

    @Throws(CouldNotPerformException::class)
    protected fun post(target: String, value: String, mediaType: MediaType): String {
        try {
            validateConnection()
            val webTarget = restTarget.path(target)
            val response = webTarget.request().post(Entity.entity(value, mediaType))

            return validateResponse(response)
        } catch (ex: CouldNotPerformException) {
            if (isShutdownInitiated) {
                setInitialCause(ex, ShutdownInProgressException(this))
            }
            throw CouldNotPerformException(
                "Could not post Value[$value] of MediaType[$mediaType] on sub-URL[$target]",
                ex
            )
        } catch (ex: ProcessingException) {
            if (isShutdownInitiated) {
                setInitialCause(ex, ShutdownInProgressException(this))
            }
            throw CouldNotPerformException(
                "Could not post Value[$value] of MediaType[$mediaType] on sub-URL[$target]",
                ex
            )
        }
    }

    fun sendWSCommand(message: String): Future<String?> = webSocketConnection.sendCommand(message)

    override fun shutdown() {
        // prepare shutdown

        isShutdownInitiated = true
        setConnectState(ConnectionStateType.ConnectionState.State.DISCONNECTED)

        // stop rest service
        restClient.close()

        // stop org.openbase.bco.device.hass.communication.websocket service
        topicObservableMapLock.withLock {
            topicObservableMap.values.forEach { jsonObjectObservable ->
                jsonObjectObservable.shutdown()
            }
            topicObservableMap.clear()
            resetConnection()
        }
    }

    @Throws(CouldNotPerformException::class)
    private fun findHassGatewayClass(): GatewayClassType.GatewayClass? =
        Registries.getClassRegistry().gatewayClasses.find { contains(it.label, HASS_GATEWAY_CLASS_LABEL) }



    companion object {

        private const val HASS_GATEWAY_CLASS_LABEL = "Home Assistant"
        private const val META_CONFIG_TOKEN_KEY = "TOKEN"

        const val SEPARATOR: String = "/"
        const val REST_ENDPOINT: String = "api"

        const val APPROVE_TARGET: String = "approve"
        const val EVENTS_TARGET: String = "events"

        const val TOPIC_KEY: String = "topic"
        const val TOPIC_SEPARATOR: String = SEPARATOR

        private val LOGGER: Logger = LoggerFactory.getLogger(HassConnection::class.java)
    }
}
