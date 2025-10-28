package org.openbase.bco.gateway.homeassistant

import org.openbase.bco.authentication.lib.BCO
import org.openbase.bco.authentication.lib.jp.JPBCOHomeDirectory
import org.openbase.bco.authentication.lib.jp.JPCredentialsDirectory
import org.openbase.bco.gateway.homeassistant.jp.JPHassHost
import org.openbase.bco.gateway.homeassistant.jp.JPHassToken
import org.openbase.bco.gateway.homeassistant.jp.JpHassPort
import org.openbase.bco.gateway.homeassistant.manager.HassDeviceManager
import org.openbase.jps.core.JPService
import org.openbase.jps.preset.JPDebugMode
import org.openbase.jps.preset.JPLogLevel
import org.openbase.jul.communication.jp.JPComHost
import org.openbase.jul.communication.jp.JPComPort
import org.openbase.jul.pattern.launch.AbstractLauncher
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths


class HassGatewayLauncher : AbstractLauncher<HassDeviceManager>(
    HassGatewayLauncher::class.java,
    HassDeviceManager::class.java
) {

    private val LOG: Logger = LoggerFactory.getLogger(HassGatewayLauncher::class.java)

    override fun loadProperties() {
        // Log all reachable environment variables for debugging
        try {
            val env = System.getenv()
            if (env.isEmpty()) {
                LOG.info("No environment variables found.")
            } else {
                // Sort keys for stable output
                env.toSortedMap().forEach { (k, v) ->
                    // Use parameterized logging to avoid string concatenation
                    LOG.info("ENV {}={}", k, v)
                }
            }
        } catch (t: Throwable) {
            LOG.warn("Failed to read environment variables: {}", t.toString())
        }

        // Read Home Assistant options.json (if present) and log its content for debugging.
        try {
            listOf(
                "/data/options.json",
                "/config/options.json",
                "options.json"
            ).firstOrNull { Files.exists(Paths.get(it)) }
                ?.let { optionFile ->

                    val bytes = Files.readAllBytes(Paths.get(optionFile))
                    val content = String(bytes, StandardCharsets.UTF_8)
                    if (content.isBlank()) {
                        LOG.info("Found options file {} but it's empty.", optionFile)
                    } else {
                        // Log the raw content. Parameterized logging will call toString on the content.
                        LOG.info("Content of options.json ({}): {}", optionFile, content)
                    }
                } ?: LOG.info("No options.json found")
        } catch (t: Throwable) {
            LOG.warn("Failed to read options.json: {}", t.toString())
        }

        JPService.registerProperty(JpHassPort::class.java)
        JPService.registerProperty(JPHassHost::class.java)
        JPService.registerProperty(JPHassToken::class.java)
        JPService.registerProperty(JPDebugMode::class.java)
        JPService.registerProperty(JPLogLevel::class.java)
        JPService.registerProperty(JPCredentialsDirectory::class.java)
        JPService.registerProperty(JPBCOHomeDirectory::class.java, File("/data"))
        JPService.registerProperty(JPComHost::class.java, System.getenv("OPTION_BCO_MIDDLEWARE_HOST")?: "addon_f71ff8bf_bco-middleware")
        JPService.registerProperty(JPComPort::class.java, System.getenv("OPTION_BCO_MIDDLEWARE_PORT")?.toInt()?: 13780)
    }

    companion object {
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
