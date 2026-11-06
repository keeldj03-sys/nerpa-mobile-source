package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SubscriptionImportNamingTest {

    private val withHost = { host: String -> "Subscription · $host" }
    private val plain = "Subscription"

    @Test
    fun `fragment wins over host`() {
        val remarks = SubscriptionImportNaming.remarks(
            fragment = "Nerpa",
            host = "panel.example.com",
            withHost = withHost,
            plainName = plain,
        )
        assertEquals("Nerpa", remarks)
    }

    @Test
    fun `blank fragment falls back to host`() {
        val remarks = SubscriptionImportNaming.remarks(
            fragment = "   ",
            host = "panel.example.com",
            withHost = withHost,
            plainName = plain,
        )
        assertEquals("Subscription · panel.example.com", remarks)
    }

    @Test
    fun `missing host gives the plain name`() {
        val remarks = SubscriptionImportNaming.remarks(
            fragment = null,
            host = null,
            withHost = withHost,
            plainName = plain,
        )
        assertEquals(plain, remarks)
    }
}
