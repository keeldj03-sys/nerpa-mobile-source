package com.v2ray.ang.handler

import com.v2ray.ang.util.EndpointFetchResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64

private const val LINK = "https://panel.example.com/sub/token"

/** What x-ui serves at a client's subscription address: base64 of that client's node links. */
private const val NODE_LINK = "vless://11112222-3333-4444-5555-666677778888@example.invalid:2053#Guest"

private fun pretendIsSubUrl(line: String) = line.startsWith("https://")

/**
 * Mirrors the production predicate: `Utils.decode` uses `Base64.NO_WRAP`, which rejects a body
 * holding anything outside the base64 alphabet, so a login page decodes to nothing.
 */
private fun pretendIsNodePackage(body: String): Boolean {
    val decoded = runCatching { String(Base64.getDecoder().decode(body)) }.getOrNull()
    return decoded != null && decoded.lineSequence().any { it.isNotBlank() }
}

private fun nodePackage(vararg links: String): String =
    Base64.getEncoder().encodeToString(links.joinToString("\n").toByteArray())

private fun parse(
    fetch: EndpointFetchResult,
    endpointUrl: String = LINK,
) = FreeSubscriptionLink.from(
    fetch,
    endpointUrl = endpointUrl,
    isValidSubUrl = ::pretendIsSubUrl,
    isNodePackage = ::pretendIsNodePackage,
)

class FreeSubscriptionLinkTest {

    @Test
    fun `first non-blank line is the link`() {
        val result = parse(EndpointFetchResult.Content("\r\n  $LINK  \r\n"))
        assertEquals(FreeLinkResult.Link(LINK), result)
    }

    @Test
    fun `the address itself is the link when the answer is the node package it serves`() {
        val result = parse(EndpointFetchResult.Content(nodePackage(NODE_LINK)))
        assertEquals(FreeLinkResult.Link(LINK), result)
    }

    @Test
    fun `a handed out link wins over the address`() {
        val other = "https://panel.example.com/sub/rotated"
        val result = parse(EndpointFetchResult.Content("$other\n$LINK"))
        assertEquals(FreeLinkResult.Link(other), result)
    }

    @Test
    fun `a node package is not used when the address is not a subscription link`() {
        val result = parse(
            EndpointFetchResult.Content(nodePackage(NODE_LINK)),
            endpointUrl = "http://intranet.example.com/login",
        )
        assertEquals(FreeLinkResult.Refused(ActivationFailure.NotALink), result)
    }

    @Test
    fun `body that is not a link is refused`() {
        val result = parse(EndpointFetchResult.Content("<html><body>login</body></html>"))
        assertEquals(FreeLinkResult.Refused(ActivationFailure.NotALink), result)
    }

    @Test
    fun `empty body is its own refusal`() {
        val result = parse(EndpointFetchResult.Content("   \n"))
        assertEquals(FreeLinkResult.Refused(ActivationFailure.EmptyResponse), result)
    }

    @Test
    fun `closed issuance is told apart from no network`() {
        for (code in listOf(403, 429)) {
            val result = parse(EndpointFetchResult.HttpStatus(code))
            assertTrue(result is FreeLinkResult.Refused)
            assertEquals(ActivationFailure.QuotaClosed, (result as FreeLinkResult.Refused).failure)
        }
    }

    @Test
    fun `other statuses and transport failures are unreachable`() {
        val statuses = listOf(500, 503, 404)
        for (code in statuses) {
            val result = parse(EndpointFetchResult.HttpStatus(code))
            assertEquals(ActivationFailure.Unreachable, (result as FreeLinkResult.Refused).failure)
        }
        val transport = parse(EndpointFetchResult.Transport)
        assertEquals(ActivationFailure.Unreachable, (transport as FreeLinkResult.Refused).failure)
    }
}
