package org.openbase.bco.gateway.homeassistant

import org.openbase.bco.gateway.homeassistant.option.OptionsParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OptionsParserTest {

    @Test
    fun `happy path parses host and numeric port`() {
        val json = """
            {
              "BCO_MIDDLEWARE_HOST": "host.example",
              "BCO_MIDDLEWARE_PORT": 4242,
              "BCO_ADMIN": "adminUser",o
              "BCO_ADMIN_PASSWORD": "s3cr3t"
            }
        """.trimIndent()

        val options = OptionsParser.parseOptionsJson(json)
        assertEquals("host.example", options?.host)
        assertEquals(4242, options?.port)
        assertEquals("adminUser", options?.admin)
        assertEquals("s3cr3t", options?.adminPassword)
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
        assertEquals("host.example", options?.host)
        assertEquals(4242, options?.port)
        assertEquals("adminUser", options?.admin)
        assertEquals("s3cr3t", options?.adminPassword)
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
}
