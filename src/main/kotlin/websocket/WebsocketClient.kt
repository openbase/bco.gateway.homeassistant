package org.example.websocket

import io.ktor.client.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import org.example.util.toRequest
import org.slf4j.LoggerFactory
import java.net.http.HttpClient
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write

class WebsocketClient {
    private val token: String = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJhY2M5MDdlNDg3MTg0YmMwOGQ1MzI3MGUyNGNiYmNmYSIsImlhdCI6MTcyMzI4OTEzMCwiZXhwIjoyMDM4NjQ5MTMwfQ.h94N7dFk9052oe6I5BsYJ3fVmBWoxTW3yXonhgwGhxU"

    private val log = LoggerFactory.getLogger(javaClass)

    private val client = HttpClient(Java) {
        engine {
            pipelining = true
            protocolVersion = HttpClient.Version.HTTP_2
        }
        install(WebSockets)
    }

    private val authRequest = mapOf(
        "type" to "auth",
        "access_token" to token
    ).toRequest()

    val requestLock = ReentrantReadWriteLock()
    val requestStack = mutableListOf<Pair<String, CompletableFuture<String>>>()
    val newRequestCondition = requestLock.writeLock().newCondition()

    fun activate() {
        runBlocking {
            request(mapOf("id" to 18, "type" to "ping").toRequest())
            initConnectionAndProcess()
        }
    }

    fun request(message: String): Future<String> =
        CompletableFuture<String>().also {
            requestLock.write {
                requestStack.add(message to it)
                newRequestCondition.signalAll()
            }
        }

    suspend fun initConnectionAndProcess() {
        client.webSocket(host = "scummbar", port = 8123, path = "/api/websocket") {

            logMessage((incoming.receive() as? Frame.Text)?.readText())
            send(authRequest)
            val auth = (incoming.receive() as? Frame.Text)?.readText() ?: ""
            if (auth.contains("auth_ok")) {
                log.debug("Authentication successful")
            } else {
                error("Websocket authentication failed")
            }
            this.processRequests()
        }
    }

    private suspend fun DefaultWebSocketSession.processRequests() {
        while (isActive && !Thread.currentThread().isInterrupted) {
            requestLock.write {

                if(requestStack.isEmpty()) {
                    log.info("Waiting for new requests")
                    newRequestCondition.await()
                }

                log.info("Processing requests")
                requestStack.removeFirst().also { (message, future) ->
                    send(message)
                    future.complete((incoming.receive() as? Frame.Text)?.readText().also { logMessage(it) })
                }
            }
        }
    }

    suspend fun subscribe(message: String): List<String?> {
        val result: MutableList<String?> = emptyList<String?>().toMutableList()

        client.webSocket(host = "scummbar", port = 8123, path = "/api/websocket") {
            logMessage((incoming.receive() as? Frame.Text)?.readText())
            send(authRequest)
            val auth = (incoming.receive() as? Frame.Text)?.readText() ?: ""
            if (auth.contains("auth_ok")) {
                log.debug("Authentication successful")
            } else {
                error("Websocket authentication failed")
            }

            send(message)
            logMessage((incoming.receive() as? Frame.Text)?.readText())

            while (true) {
                val temp = (incoming.receive() as? Frame.Text)?.readText()
                result.addLast(temp)
                logMessage(temp)
            }
        }

        return result
    }

    private fun logMessage(message: String?) = runBlocking {
        log.debug("Received message: $message")
    }
}
