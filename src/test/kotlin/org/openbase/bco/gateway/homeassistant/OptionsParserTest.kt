package org.openbase.bco.gateway.homeassistant

import org.openbase.bco.gateway.homeassistant.option.OptionsParser
import kotlin.test.Test
import kotlin.test.assertNull
import org.amshove.kluent.shouldBeEqualTo

class OptionsParserTest {

    @Test
    fun `happy path parses host and numeric port`() {
        val json = """
            {
              "BCO_MIDDLEWARE_HOST": "host.example",
              "BCO_MIDDLEWARE_PORT": 4242,
              "BCO_ADMIN": "adminUser",
              "BCO_ADMIN_PASSWORD": "s3cr3t"
            }
        """.trimIndent()

        val options = OptionsParser.parseOptionsJson(json)
        options?.host shouldBeEqualTo "host.example"
        options?.port shouldBeEqualTo 4242
        options?.admin shouldBeEqualTo "adminUser"
        options?.adminPassword shouldBeEqualTo "s3cr3t"
    }

    @Test
    fun `ignore blank admin credentials`() {
        val json = """
            {
              "BCO_MIDDLEWARE_HOST": "host.example",
              "BCO_MIDDLEWARE_PORT": 4242,
              "BCO_ADMIN": "",
              "BCO_ADMIN_PASSWORD": ""
            }
        """.trimIndent()

        val options = OptionsParser.parseOptionsJson(json)
        options?.host shouldBeEqualTo "host.example"
        options?.port shouldBeEqualTo 4242
        options?.admin shouldBeEqualTo null
        options?.adminPassword shouldBeEqualTo null
    }

    @Test
    fun `port as string is parsed`() {
        val json = """
            {
              "BCO_MIDDLEWARE_HOST": "host.example",
              "BCO_MIDDLEWARE_PORT": "4242",
              "BCO_ADMIN": "adminUser",
              "BCO_ADMIN_PASSWORD": "s3cr3t"
            }
        """.trimIndent()

        val options = OptionsParser.parseOptionsJson(json)
        options?.host shouldBeEqualTo "host.example"
        options?.port shouldBeEqualTo 4242
        options?.admin shouldBeEqualTo "adminUser"
        options?.adminPassword shouldBeEqualTo "s3cr3t"
    }

    @Test
    fun `missing keys return nulls`() {
        val json = """
            {
              "OTHER": "value"
            }
        """.trimIndent()

        val options = OptionsParser.parseOptionsJson(json)
        assertNull(options?.host)
        assertNull(options?.port)
        assertNull(options?.admin)
        assertNull(options?.adminPassword)
    }

    @Test
    fun `malformed json returns nulls and does not throw`() {
        val json = "{ this is not valid json: }"
        val options = OptionsParser.parseOptionsJson(json)
        assertNull(options?.host)
        assertNull(options?.port)
        assertNull(options?.admin)
        assertNull(options?.adminPassword)
    }

    @Test
    fun `happy path parses all options`() {
        val json = """
            {
              "BCO_MIDDLEWARE_HOST": "host.example",
              "BCO_MIDDLEWARE_PORT": 4242,
              "BCO_ADMIN": "adminUser",
              "BCO_ADMIN_PASSWORD": "s3cr3t",
              "LOG_LEVEL": "DEBUG",
              "DEBUG_MODE": true,
              "HOME_ASSISTANT_HOST": "hass.local",
              "HOME_ASSISTANT_PORT": 8123
            }
        """.trimIndent()

        val options = OptionsParser.parseOptionsJson(json)
        options?.host shouldBeEqualTo "host.example"
        options?.port shouldBeEqualTo 4242
        options?.admin shouldBeEqualTo "adminUser"
        options?.adminPassword shouldBeEqualTo "s3cr3t"
        options?.logLevel shouldBeEqualTo "DEBUG"
        options?.debugMode shouldBeEqualTo true
        options?.homeAssistantHost shouldBeEqualTo "hass.local"
        options?.homeAssistantPort shouldBeEqualTo 8123
    }

    @Test
    fun `missing new keys return nulls`() {
        val json = """
            {
              "BCO_MIDDLEWARE_HOST": "host.example",
              "BCO_MIDDLEWARE_PORT": 4242
            }
        """.trimIndent()
        val options = OptionsParser.parseOptionsJson(json)
        assertNull(options?.logLevel)
        assertNull(options?.debugMode)
        assertNull(options?.homeAssistantHost)
        assertNull(options?.homeAssistantPort)
    }

    @Test
    fun `malformed values for new options are handled gracefully`() {
        val json = """
            {
              "LOG_LEVEL": 123,
              "DEBUG_MODE": "notabool",
              "HOME_ASSISTANT_HOST": 456,
              "HOME_ASSISTANT_PORT": "notanint"
            }
        """.trimIndent()
        val options = OptionsParser.parseOptionsJson(json)
        assertNull(options?.logLevel)
        assertNull(options?.debugMode)
        assertNull(options?.homeAssistantHost)
        assertNull(options?.homeAssistantPort)
    }

    @Test
    fun `string values for debugMode and homeAssistantPort are parsed if valid`() {
        val json = """
            {
              "DEBUG_MODE": "true",
              "HOME_ASSISTANT_PORT": "8123"
            }
        """.trimIndent()
        val options = OptionsParser.parseOptionsJson(json)
        options?.debugMode shouldBeEqualTo true
        options?.homeAssistantPort shouldBeEqualTo 8123
    }
}
