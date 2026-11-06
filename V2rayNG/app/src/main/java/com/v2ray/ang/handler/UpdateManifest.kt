package com.v2ray.ang.handler

import com.v2ray.ang.util.EndpointFetchResult
import java.net.URI

/** What asking the issuance host for a newer build can end in. */
sealed class UpdateCheckResult {
    /** The installed build is the one the host offers, or newer than it. */
    data object Current : UpdateCheckResult()

    /**
     * A newer build exists. [url] is where its file is; it is opened in the browser rather than
     * downloaded here, so the version number is shown nowhere and the user leaves the app to install.
     */
    data class Available(val versionCode: Int, val url: String) : UpdateCheckResult()

    /** No answer to show; [failure] is the only distinction the user is told about. */
    data class Refused(val failure: UpdateFailure) : UpdateCheckResult()
}

enum class UpdateFailure {
    /** No connection at all, or the host answered with a non-2xx status. */
    Unreachable,

    /** Nothing to read: no manifest address, an empty answer, or an answer that isn't a record. */
    Malformed,
}

/**
 * Reads the static update manifest the operator puts next to the subscription endpoint.
 *
 * There is no separate address to bake into the APK: the manifest is one file on the same host that
 * already issues subscriptions, so [manifestUrl] derives it from `BuildConfig.FREE_SUBSCRIPTION_ENDPOINT`
 * and a build without an endpoint cannot produce a request. The record is one line,
 * `<versionCode> <link to the APK file>`, which the operator can write by hand and serve as a static
 * file; anything else — an index page, a login page, an empty file — is [UpdateFailure.Malformed],
 * never a guess.
 *
 * Cleartext is refused in both directions, [manifestUrl] and the file link, with one exception that
 * mirrors the Gradle gate on `NERPA_FREE_ENDPOINT`: `http` to `127.0.0.1` or `localhost`, which is how
 * the issuance stand reaches a phone over `adb reverse`. Without that exception the three outcomes of
 * this check could only be argued from the test, never run on the stand.
 */
object UpdateManifest {

    fun manifestUrl(endpointUrl: String): String? {
        val endpoint = parseAllowedAddress(endpointUrl) ?: return null
        val port = if (endpoint.port == -1) "" else ":${endpoint.port}"
        return "${endpoint.scheme}://${endpoint.host}$port/nerpa/latest"
    }

    fun from(fetch: EndpointFetchResult, currentVersionCode: Int): UpdateCheckResult = when (fetch) {
        is EndpointFetchResult.HttpStatus -> UpdateCheckResult.Refused(UpdateFailure.Unreachable)
        EndpointFetchResult.Transport -> UpdateCheckResult.Refused(UpdateFailure.Unreachable)
        is EndpointFetchResult.Content -> parse(fetch.body, currentVersionCode)
    }

    /**
     * One whole check: derive the address, ask it, read the answer. [fetch] arrives as a parameter
     * for the same reason `FreeSubscriptionLink` takes predicates - the JVM test has to drive every
     * outcome, and `HttpUtil` would need a live host for each of them.
     */
    fun check(
        endpointUrl: String,
        currentVersionCode: Int,
        fetch: (String) -> EndpointFetchResult,
    ): UpdateCheckResult {
        val manifestUrl = manifestUrl(endpointUrl) ?: return refused()
        return from(fetch(manifestUrl), currentVersionCode)
    }

    private fun parse(body: String, currentVersionCode: Int): UpdateCheckResult {
        val line = body.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?: return refused()
        val tokens = line.split(whitespace)
        if (tokens.size != 2) return refused()
        val versionCode = tokens[0].toIntOrNull() ?: return refused()
        // A manifest naming a build that cannot be installed is as unusable as an empty one.
        if (versionCode <= 0) return refused()
        if (parseAllowedAddress(tokens[1]) == null) return refused()
        return if (versionCode <= currentVersionCode) {
            UpdateCheckResult.Current
        } else {
            UpdateCheckResult.Available(versionCode, tokens[1])
        }
    }

    private fun refused(): UpdateCheckResult = UpdateCheckResult.Refused(UpdateFailure.Malformed)

    private fun parseAllowedAddress(address: String): URI? {
        val uri = runCatching { URI(address) }.getOrNull()
            ?.takeIf { it.isAbsolute && !it.host.isNullOrEmpty() } ?: return null
        val cleartextStand = uri.scheme == "http" && (uri.host == "127.0.0.1" || uri.host == "localhost")
        return uri.takeIf { it.scheme == "https" || cleartextStand }
    }

    private val whitespace = Regex("\\s+")
}
