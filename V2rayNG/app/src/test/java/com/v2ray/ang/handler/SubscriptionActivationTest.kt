package com.v2ray.ang.handler

import com.v2ray.ang.dto.SubscriptionUpdateResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SubscriptionActivationTest {

    @Test
    fun `counts each node line once and ignores blanks`() {
        val body = """
            vless://1.2.3.4:443?uuid=aaa#Аэропорт
            vless://1.2.3.4:443?uuid=aaa#Аэропорт

            trojan://bbb@5.6.7.8:443?sni=example.com#Узел 2 с пробелами
        """.trimIndent()

        assertEquals(2, SubscriptionActivation.countNodeLines(body))
    }

    @Test
    fun `keeps a node whose name contains a second hash`() {
        // x-ui builds remarks like "Узел #1 (NL)", so the second '#' is part of the name, not a
        // second record separator.
        val line = "vless://1.2.3.4:443?uuid=aaa#Узел #1 (NL)"

        assertEquals(1, SubscriptionActivation.countNodeLines(line))
    }

    @Test
    fun `an empty body is not a node list`() {
        assertEquals(0, SubscriptionActivation.countNodeLines("   \n\n  "))
    }

    @Test
    fun `a plain-text answer is counted from the body when base64 decoding gave nothing`() {
        // `Utils.decode` returns "" for a body that is not base64; the probe must not read that as
        // "no nodes" or it rejects a subscription the commit would accept.
        val raw = "vmess://AAA\nvless://1.2.3.4:443?uuid=aaa#Нод"

        assertEquals(2, SubscriptionActivation.countNodeLines("", raw))
    }

    @Test
    fun `a decoded answer is counted from the decoded text`() {
        val decoded = "vmess://AAA\nvmess://BBB\nss://CCC"
        val raw = "dm1lc3M6Ly9BQUF2bWVzczovL0JCQnNzOi8vQ0ND"

        assertEquals(3, SubscriptionActivation.countNodeLines(decoded, raw))
    }

    @Test
    fun `an answer with no nodes either way is still zero`() {
        assertEquals(0, SubscriptionActivation.countNodeLines("", "   \n\n "))
    }

    @Test
    fun `commit with zero stored nodes reports unparsable nodes`() {
        val outcome = SubscriptionActivation.classify(
            subscriptionId = "guid",
            total = 4,
            result = SubscriptionUpdateResult(failureCount = 1),
        )

        assertEquals(ActivationOutcome.Failed(ActivationFailure.NoParsableNodes, total = 4), outcome)
    }

    @Test
    fun `a partially unparsable list stays accepted but reports the difference`() {
        val outcome = SubscriptionActivation.classify(
            subscriptionId = "guid",
            total = 5,
            result = SubscriptionUpdateResult(configCount = 4, successCount = 1),
        )

        assertEquals(ActivationOutcome.Accepted("guid", total = 5, accepted = 4), outcome)
    }

    @Test
    fun `a fully accepted list reports equal counts`() {
        val outcome = SubscriptionActivation.classify(
            subscriptionId = "guid",
            total = 5,
            result = SubscriptionUpdateResult(configCount = 5, successCount = 1),
        )

        assertEquals(ActivationOutcome.Accepted("guid", total = 5, accepted = 5), outcome)
    }

    @Test
    fun `blank link trims to nothing before any storage write`() {
        assertEquals(null, SubscriptionActivation.trimLink(""))
        assertEquals(null, SubscriptionActivation.trimLink("   "))
        assertEquals("https://panel.example.com/sub/token", SubscriptionActivation.trimLink("  https://panel.example.com/sub/token  "))
    }
}
