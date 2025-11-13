package org.openbase.bco.gateway.homeassistant.option

import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.ReadContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Small utility to parse Home Assistant addon options.json for the options we care about.
 */
object OptionsParser {

    private val LOG: Logger = LoggerFactory.getLogger(OptionsParser::class.java)

    /**
     * Parse the provided JSON content into a JsonPath ReadContext.
     * Returns null when the content is blank or malformed.
     */
    fun parseToContext(content: String? = readContent()): ReadContext? =
        content
            ?.takeIf { it.isNotBlank() }
            ?.let { content ->
                runCatching { JsonPath.parse(content) }
                    .getOrNull()
            }

    /**
     * Extract BCO_MIDDLEWARE_HOST from the parsed JsonPath context.
     * Returns null when the key is missing or not a string.
     */
    fun parseHost(context: ReadContext): String? = runCatching {
        context.read<String>("$.BCO_MIDDLEWARE_HOST")
    }.getOrNull()

    /**
     * Extract BCO_MIDDLEWARE_PORT from the parsed JsonPath context.
     * Accepts numeric values or string values that can be parsed as integers.
     */
    fun parsePort(context: ReadContext): Int? = runCatching {
        val portAny: Any? = context.read<Any>("$.BCO_MIDDLEWARE_PORT")
        when (portAny) {
            is Number -> portAny.toInt()
            is String -> portAny.toIntOrNull()
            else -> null
        }
    }.getOrNull()

    /**
     * Extract BCO_ADMIN from the parsed JsonPath context.
     * Returns null when the key is missing or not a string.
     */
    fun parseAdmin(context: ReadContext): String? = runCatching {
        context.read<String>("$.BCO_ADMIN")
    }.getOrNull()?.takeIf { it.isNotBlank() }

    /**
     * Extract BCO_ADMIN_PASSWORD from the parsed JsonPath context.
     * Returns null when the key is missing or not a string.
     * Note: be careful not to log passwords in production; this helper returns the raw value.
     */
    fun parseAdminPassword(context: ReadContext): String? = runCatching {
        context.read<String>("$.BCO_ADMIN_PASSWORD")
    }.getOrNull()?.takeIf { it.isNotBlank() }

    /**
     * Extract LOG_LEVEL from the parsed JsonPath context.
     * Returns null when the key is missing or not a string.
     */
    fun parseLogLevel(context: ReadContext): String? = runCatching {
        context.read<String>("$.LOG_LEVEL")
    }.getOrNull()?.takeIf { it.isNotBlank() }

    /**
     * Extract DEBUG_MODE from the parsed JsonPath context.
     * Returns null when the key is missing or not a boolean.
     */
    fun parseDebugMode(context: ReadContext): Boolean? = runCatching {
        when (val value: Any? = context.read<Any>("$.DEBUG_MODE")) {
            is Boolean -> value
            is String -> value.toBooleanStrictOrNull()
            is Number -> value.toInt() != 0
            else -> null
        }
    }.getOrNull()

    /**
     * Extract HOME_ASSISTANT_HOST from the parsed JsonPath context.
     * Returns null when the key is missing or not a string.
     */
    fun parseHomeAssistantHost(context: ReadContext): String? = runCatching {
        context.read<String>("$.HOME_ASSISTANT_HOST")
    }.getOrNull()?.takeIf { it.isNotBlank() }

    /**
     * Extract HOME_ASSISTANT_PORT from the parsed JsonPath context.
     * Accepts numeric values or string values that can be parsed as integers.
     */
    fun parseHomeAssistantPort(context: ReadContext): Int? = runCatching {
        when (val portAny: Any? = context.read<Any>("$.HOME_ASSISTANT_PORT")) {
            is Number -> portAny.toInt()
            is String -> portAny.toIntOrNull()
            else -> null
        }
    }.getOrNull()

    /**
     * Convenience function that parses the content and extracts all AddonOptions properties using
     * the dedicated helpers above.
     */
    fun parseOptionsJson(content: String? = readContent()): AddonOptions? =
        content?.let {
            parseToContext(content)?.let { context ->
                AddonOptions(
                    host = parseHost(context),
                    port = parsePort(context),
                    admin = parseAdmin(context),
                    adminPassword = parseAdminPassword(context),
                    logLevel = parseLogLevel(context),
                    debugMode = parseDebugMode(context),
                    homeAssistantHost = parseHomeAssistantHost(context),
                    homeAssistantPort = parseHomeAssistantPort(context),
                )
            }
        }

    fun readContent(): String? =
        runCatching {
            listOf(
                "/data/options.json",
                "/config/options.json",
                "options.json"
            ).firstOrNull { Files.exists(Paths.get(it)) }
                ?.let { optionFile ->
                    val bytes = Files.readAllBytes(Paths.get(optionFile))
                    val content = String(bytes, StandardCharsets.UTF_8)
                    content.takeIf { it.isNotBlank() }
                } ?: null.also { LOG.warn("No options.json found") }
        }.getOrNull()
}
