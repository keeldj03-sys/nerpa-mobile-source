package com.v2ray.ang.handler

import com.v2ray.ang.util.EndpointFetchResult

/** What the operator endpoint gave us, reduced to either a link or one user-facing refusal. */
sealed class FreeLinkResult {
    /** A trimmed, subscription-shaped link. */
    data class Link(val url: String) : FreeLinkResult()

    /** No link; carries the message the activation screen shows. */
    data class Refused(val failure: ActivationFailure) : FreeLinkResult()
}

/**
 * Reads the free-subscription endpoint response.
 *
 * Two answers are usable. A plain-text line that is a subscription link is the issued link, and
 * that is how the operator rotates it without rebuilding the APK. Anything else has to be the node
 * package the endpoint address itself serves — that is how x-ui answers a client's subscription
 * address, so the button serves the address it was given. `isNodePackage` tells that apart from a
 * login page, and a package only counts when the address is itself a subscription link. Importing
 * an HTML page instead would create a dead group and a second tab.
 * `isValidSubUrl` and `isNodePackage` arrive as predicates because `Utils.isValidSubUrl` runs on
 * `android.webkit` and base64 decoding on `android.util.Base64`, neither of which is available to
 * JVM tests.
 */
object FreeSubscriptionLink {

    fun from(
        fetch: EndpointFetchResult,
        endpointUrl: String,
        isValidSubUrl: (String) -> Boolean,
        isNodePackage: (String) -> Boolean,
    ): FreeLinkResult = when (fetch) {
        is EndpointFetchResult.HttpStatus -> FreeLinkResult.Refused(refusalForStatus(fetch.code))
        EndpointFetchResult.Transport -> FreeLinkResult.Refused(ActivationFailure.Unreachable)
        is EndpointFetchResult.Content -> parse(fetch.body, endpointUrl, isValidSubUrl, isNodePackage)
    }

    private fun parse(
        body: String,
        endpointUrl: String,
        isValidSubUrl: (String) -> Boolean,
        isNodePackage: (String) -> Boolean,
    ): FreeLinkResult {
        val line = body.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?: return FreeLinkResult.Refused(ActivationFailure.EmptyResponse)
        if (isValidSubUrl(line)) {
            return FreeLinkResult.Link(line)
        }
        val address = endpointUrl.trim()
        return if (isNodePackage(body) && isValidSubUrl(address)) {
            FreeLinkResult.Link(address)
        } else {
            FreeLinkResult.Refused(ActivationFailure.NotALink)
        }
    }

    private fun refusalForStatus(code: Int): ActivationFailure = when (code) {
        403, 429 -> ActivationFailure.QuotaClosed
        else -> ActivationFailure.Unreachable
    }
}
