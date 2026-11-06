package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.enums.NerpaRoutingMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NerpaRoutingTest {

    private fun rules(mode: NerpaRoutingMode) = NerpaRouting.rulesets(mode) { "rule-$it" }

    @Test
    fun `minimal only bypasses the local network and never blocks`() {
        val rules = rules(NerpaRoutingMode.Minimal)

        assertEquals(listOf("nerpa_private", "nerpa_fallback"), rules.map { it.id })
        assertFalse(rules.any { it.outboundTag == AppConfig.TAG_BLOCKED })
        assertFalse(rules.any { it.domain?.contains(AppConfig.GEOSITE_CATEGORY_RU) == true })
    }

    @Test
    fun `auto blocks ads and keeps russian services outside the tunnel`() {
        val rules = rules(NerpaRoutingMode.Auto)

        assertEquals(
            listOf("nerpa_ads", "nerpa_private", "nerpa_russia", "nerpa_fallback"),
            rules.map { it.id },
        )
        assertEquals(
            listOf(AppConfig.GEOSITE_ADS_ALL),
            rules.first { it.id == "nerpa_ads" }.domain,
        )

        val russia = rules.first { it.id == "nerpa_russia" }
        assertEquals(listOf(AppConfig.GEOSITE_CATEGORY_RU), russia.domain)
        assertEquals(listOf(AppConfig.GEOIP_RU), russia.ip)
        assertEquals(AppConfig.TAG_DIRECT, russia.outboundTag)
    }

    @Test
    fun `max protection keeps russian sites inside the tunnel`() {
        val rules = rules(NerpaRoutingMode.MaxProtection)

        assertEquals(listOf("nerpa_ads", "nerpa_private", "nerpa_fallback"), rules.map { it.id })
        assertTrue(rules.none { it.domain?.contains(AppConfig.GEOSITE_CATEGORY_RU) == true })
        assertTrue(rules.none { it.ip?.contains(AppConfig.GEOIP_RU) == true })
    }

    @Test
    fun `every mode ends with one catch-all rule to the proxy`() {
        NerpaRoutingMode.entries.forEach { mode ->
            val last = rules(mode).last()

            assertEquals(AppConfig.TAG_PROXY, last.outboundTag, mode.name)
            assertEquals("0-65535", last.port, mode.name)
            assertTrue(last.domain.isNullOrEmpty(), mode.name)
            assertTrue(last.ip.isNullOrEmpty(), mode.name)
        }
    }

    @Test
    fun `local network bypass uses the private groups the core rewrites`() {
        NerpaRoutingMode.entries.forEach { mode ->
            val private = rules(mode).first { it.id == "nerpa_private" }

            assertEquals(listOf(AppConfig.GEOSITE_PRIVATE), private.domain, mode.name)
            assertEquals(listOf(AppConfig.GEOIP_PRIVATE), private.ip, mode.name)
            assertEquals(AppConfig.TAG_DIRECT, private.outboundTag, mode.name)
        }
    }

    @Test
    fun `stored rules carry a name, an id and stay unlocked`() {
        NerpaRoutingMode.entries.forEach { mode ->
            val rules = rules(mode)

            assertEquals(rules.map { it.id }.toSet().size, rules.size, mode.name)
            rules.forEach {
                assertTrue(it.remarks?.isNotBlank() == true, "${mode.name}/${it.id}")
                assertTrue(it.enabled, "${mode.name}/${it.id}")
                assertFalse(it.locked == true, "${mode.name}/${it.id}")
            }
        }
    }

    @Test
    fun `an unknown or absent stored key falls back to the default mode`() {
        assertEquals(NerpaRoutingMode.Auto, NerpaRoutingMode.DEFAULT)
        assertEquals(NerpaRoutingMode.Auto, NerpaRoutingMode.fromStorageKey(null))
        assertEquals(NerpaRoutingMode.Auto, NerpaRoutingMode.fromStorageKey(""))
        assertEquals(NerpaRoutingMode.Auto, NerpaRoutingMode.fromStorageKey("white_russia"))

        NerpaRoutingMode.entries.forEach {
            assertEquals(it, NerpaRoutingMode.fromStorageKey(it.storageKey), it.name)
        }
    }
}
