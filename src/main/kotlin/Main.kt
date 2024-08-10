package org.example

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.example.util.toRequest
import org.example.websocket.WebsocketClient

fun main() {
    val client = WebsocketClient()

    runBlocking {
        client.query(mapOf("id" to 19, "type" to "ping").toRequest())
        client.subscribe(mapOf("id" to 18, "type" to "subscribe_events").toRequest()).collect()
    }
}
