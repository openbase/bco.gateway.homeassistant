package org.openbase.bco.gateway.homeassistant

import org.openbase.bco.authentication.lib.BCO
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


class HassGatewayLauncher : AbstractLauncher<HassDeviceManager>(
    HassGatewayLauncher::class.java,
    HassDeviceManager::class.java
) {
    override fun loadProperties() {
        JPService.registerProperty(JpHassPort::class.java)
        JPService.registerProperty(JPHassHost::class.java)
        JPService.registerProperty(JPHassToken::class.java)
        JPService.registerProperty(JPDebugMode::class.java)
        JPService.registerProperty(JPLogLevel::class.java)
        JPService.registerProperty(JPCredentialsDirectory::class.java)
        JPService.registerProperty(JPComHost::class.java, System.getenv("OPTION_BCO_MIDDLEWARE_HOST")?: "localhost")
        JPService.registerProperty(JPComPort::class.java, System.getenv("OPTION_BCO_MIDDLEWARE_PORT")?.toInt()?: 1883)
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
