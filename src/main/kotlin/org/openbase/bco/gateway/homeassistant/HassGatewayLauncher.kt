package org.openbase.bco.gateway.homeassistant

import org.openbase.bco.authentication.lib.BCO
import org.openbase.bco.authentication.lib.jp.JPBCOHomeDirectory
import org.openbase.bco.authentication.lib.jp.JPCredentialsDirectory
import org.openbase.bco.gateway.homeassistant.jp.*
import org.openbase.bco.gateway.homeassistant.manager.HassDeviceManager
import org.openbase.bco.gateway.homeassistant.option.AddonOptions
import org.openbase.bco.gateway.homeassistant.option.OptionsParser
import org.openbase.jps.core.JPService
import org.openbase.jps.preset.JPDebugMode
import org.openbase.jps.preset.JPLogLevel
import org.openbase.jul.communication.jp.JPComHost
import org.openbase.jul.communication.jp.JPComPort
import org.openbase.jul.pattern.launch.AbstractLauncher
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File


class HassGatewayLauncher : AbstractLauncher<HassDeviceManager>(
    HassGatewayLauncher::class.java,
    HassDeviceManager::class.java
) {

    private val LOG: Logger = LoggerFactory.getLogger(HassGatewayLauncher::class.java)

    override fun loadProperties() {

        // Read Home Assistant options.json (if present) and optionally log its content when OPTION_DEBUG is set.
        val options: AddonOptions? = OptionsParser.parseOptionsJson()

        JPService.registerProperty(JPHassToken::class.java)

        HASS_ADDON_PERSISTENT_DATA_DIRECTORY
            .takeIf { it.exists() && it.isDirectory }
            ?.let { bcoHomeAtAddon ->
                JPService.registerProperty(JPBCOHomeDirectory::class.java, bcoHomeAtAddon)
            } ?: JPService.registerProperty(JPBCOHomeDirectory::class.java)

        options?.admin?.let {
            JPService.registerProperty(JPBcoAdminUsername::class.java, options.admin)
        } ?: JPService.registerProperty(JPBcoAdminUsername::class.java)

        options?.adminPassword?.let {
            JPService.registerProperty(JPBcoAdminPassword::class.java, options.adminPassword)
        } ?: JPService.registerProperty(JPBcoAdminPassword::class.java)

        options?.host?.let {
            JPService.registerProperty(JPComHost::class.java, options.host)
        } ?: JPService.registerProperty(JPComHost::class.java)

        options?.port?.let {
            JPService.registerProperty(JPComPort::class.java, options.port)
        } ?: JPService.registerProperty(JPComPort::class.java)

        options?.homeAssistantHost?.let {
            JPService.registerProperty(JPHassHost::class.java, options.homeAssistantHost)
        } ?: JPService.registerProperty(JPHassHost::class.java)

        options?.homeAssistantPort?.let {
            JPService.registerProperty(JPHassPort::class.java, options.homeAssistantPort)
        } ?: JPService.registerProperty(JPHassPort::class.java)

        options?.logLevel?.let {
            JPService.registerProperty(JPLogLevel::class.java, JPLogLevel.LogLevel.valueOf(options.logLevel))
        } ?: JPService.registerProperty(JPLogLevel::class.java)

        options?.debugMode?.let {
            JPService.registerProperty(JPDebugMode::class.java, options.debugMode)
        } ?: JPService.registerProperty(JPDebugMode::class.java)

        options?.homeAssistantWebsocketEndpoint?.let {
            JPService.registerProperty(JPHassWebsocketEndpoint::class.java, options.homeAssistantWebsocketEndpoint)
        } ?: JPService.registerProperty(JPHassWebsocketEndpoint::class.java)

        options?.homeAssistantRestEndpoint?.let {
            JPService.registerProperty(JPHassRestEndpoint::class.java, options.homeAssistantRestEndpoint)
        } ?: JPService.registerProperty(JPHassRestEndpoint::class.java)

        JPService.registerProperty(JPCredentialsDirectory::class.java)
    }

    companion object {

        val HASS_ADDON_PERSISTENT_DATA_DIRECTORY: File = File("/data")

        /**
         * @param args the command line arguments
         */
        @JvmStatic
        @Throws (InterruptedException::class)
        fun main(args: Array<String>) {
            BCO.printLogo()
            main(
                BCO::class.java,
                HassGatewayLauncher::class.java,
                args,
                HassGatewayLauncher::class.java,
//                 HassConfigSynchronizerLauncher.class,
            )
        }
    }
}
