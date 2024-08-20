package org.openbase.bco.device.hass

import jakarta.annotation.PostConstruct
import org.openbase.bco.device.hass.communication.websocket.WebsocketService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication


@SpringBootApplication
class WebSocketClientApplication(
    private val websocketService: WebsocketService,
) {
    @PostConstruct
    fun init() {
        println("Send message")
        websocketService.sendDynamicMessage("Hello World!")
    }
}

fun main(args: Array<String>) {
   runApplication<WebSocketClientApplication>(*args)
}
