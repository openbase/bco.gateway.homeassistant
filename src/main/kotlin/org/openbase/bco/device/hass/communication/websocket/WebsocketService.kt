package org.openbase.bco.device.hass.communication.websocket

import org.jvnet.hk2.annotations.Service

@Service
class WebsocketService(
    private val websocketHandler: HassWebsocketHandler
) {
    fun sendDynamicMessage(message: String) {
        websocketHandler.sendMessage(message)
    }
}