package com.v2ray.ang.ui.main

import com.v2ray.ang.AppConfig
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val NOW = 1_700_000_000L

private fun facts(vararg pairs: Pair<String, Pair<Boolean, Boolean>>) = pairs.map { (guid, flags) ->
    SubscriptionFacts(guid = guid, hasNodes = flags.first, expired = flags.second)
}

class ActivationGateTest {

    @Test
    fun `an app with nothing at all shows the screen`() {
        assertTrue(ActivationGate.shouldShow(emptyList(), hasManualNodes = false))
    }

    @Test
    fun `a live free slot does not`() {
        assertFalse(
            ActivationGate.shouldShow(
                facts("free-slot-guid" to (true to false)),
                hasManualNodes = false,
            )
        )
    }

    @Test
    fun `an expired slot with no other subscription shows the screen`() {
        assertTrue(
            ActivationGate.shouldShow(
                facts("free-slot-guid" to (true to true)),
                hasManualNodes = false,
            )
        )
    }

    @Test
    fun `the user's own live subscription keeps the screen away`() {
        assertFalse(
            ActivationGate.shouldShow(
                facts(
                    "free-slot-guid" to (true to true),
                    "own-sub" to (true to false),
                ),
                hasManualNodes = false,
            )
        )
    }

    @Test
    fun `a subscription shared in from outside keeps the screen away`() {
        assertFalse(
            ActivationGate.shouldShow(
                facts("imported-sub" to (true to false)),
                hasManualNodes = false,
            )
        )
    }

    @Test
    fun `a subscription without nodes is not live`() {
        assertTrue(
            ActivationGate.shouldShow(
                facts("free-slot-guid" to (false to false)),
                hasManualNodes = false,
            )
        )
    }

    @Test
    fun `manually added nodes keep the screen away`() {
        assertFalse(ActivationGate.shouldShow(emptyList(), hasManualNodes = true))
    }

    @Test
    fun `manually added nodes keep the screen away when every subscription is dead`() {
        assertFalse(
            ActivationGate.shouldShow(
                facts("free-slot-guid" to (true to true)),
                hasManualNodes = true,
            )
        )
    }

    @Test
    fun `expire of zero means the panel sent no expiry`() {
        assertFalse(ActivationGate.isExpired(0L, NOW))
        assertTrue(ActivationGate.isExpired(NOW - 1, NOW))
        assertFalse(ActivationGate.isExpired(NOW + 1, NOW))
    }

    @Test
    fun `the all-groups pseudo-entry is not a subscription`() {
        assertFalse(ActivationGate.isSubscription(""))
    }

    @Test
    fun `the manual node basket is not a subscription`() {
        assertFalse(ActivationGate.isSubscription(AppConfig.DEFAULT_SUBSCRIPTION_ID))
    }

    @Test
    fun `a stored subscription group is`() {
        assertTrue(ActivationGate.isSubscription("free-slot-guid"))
    }

    @Test
    fun `the basket and the pseudo-group reach the rule only as manual nodes`() {
        val stored = listOf(
            SubscriptionFacts(AppConfig.DEFAULT_SUBSCRIPTION_ID, hasNodes = true, expired = false),
            SubscriptionFacts("", hasNodes = true, expired = false),
        )
        val subscriptions = stored.filter { ActivationGate.isSubscription(it.guid) }

        assertTrue(ActivationGate.shouldShow(subscriptions, hasManualNodes = false))
        assertFalse(ActivationGate.shouldShow(subscriptions, hasManualNodes = true))
    }
}
