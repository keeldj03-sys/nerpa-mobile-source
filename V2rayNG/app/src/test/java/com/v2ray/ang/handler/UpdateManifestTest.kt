package com.v2ray.ang.handler

import com.v2ray.ang.util.EndpointFetchResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/** The address baked into the distributed build; the manifest URL has to come out of its origin. */
private const val ENDPOINT = "https://panel.example.com/sub/token"

private const val APK = "https://panel.example.com/nerpa/NerpaMobile_arm64-v8a.apk"

private const val CURRENT = 5000755

private fun check(body: String, current: Int = CURRENT) =
    UpdateManifest.from(EndpointFetchResult.Content(body), current)

class UpdateManifestTest {

    @Test
    fun `the manifest lives at the issuance host, not at the subscription path`() {
        assertEquals("https://panel.example.com/nerpa/latest", UpdateManifest.manifestUrl(ENDPOINT))
    }

    @Test
    fun `the port of the issuance address is kept`() {
        assertEquals(
            "https://panel.example.com:8443/nerpa/latest",
            UpdateManifest.manifestUrl("https://panel.example.com:8443/sub/token"),
        )
        assertEquals(
            "http://127.0.0.1:18080/nerpa/latest",
            UpdateManifest.manifestUrl("http://127.0.0.1:18080/sub/token"),
        )
    }

    @Test
    fun `the stand may answer over cleartext loopback`() {
        assertEquals(
            "http://localhost:18080/nerpa/latest",
            UpdateManifest.manifestUrl("http://localhost:18080/sub/token"),
        )
    }

    @Test
    fun `a cleartext address outside the loopback has no manifest`() {
        assertEquals(null, UpdateManifest.manifestUrl("http://panel.example.com/sub/token"))
    }

    @Test
    fun `a unusable address yields no manifest url`() {
        for (address in listOf("", "   ", "not a url", "https://", "ftp://panel.example.com/sub")) {
            assertEquals(null, UpdateManifest.manifestUrl(address), address)
        }
    }

    @Test
    fun `path query and fragment of the issuance address are dropped`() {
        assertEquals(
            "https://panel.example.com/nerpa/latest",
            UpdateManifest.manifestUrl("https://panel.example.com/sub/token?rotate=1#frag"),
        )
    }

    @Test
    fun `a newer build is offered with its file link`() {
        assertEquals(
            UpdateCheckResult.Available(CURRENT + 1, APK),
            check("${CURRENT + 1} $APK"),
        )
    }

    @Test
    fun `the installed build and an older one both mean nothing to install`() {
        assertEquals(UpdateCheckResult.Current, check("$CURRENT $APK"))
        assertEquals(UpdateCheckResult.Current, check("4000700 $APK"))
    }

    @Test
    fun `wrapping and spaces around the record do not change it`() {
        assertEquals(UpdateCheckResult.Available(CURRENT + 1, APK), check("\r\n  ${CURRENT + 1} $APK \r\n"))
        assertEquals(UpdateCheckResult.Available(CURRENT + 1, APK), check("${CURRENT + 1}    $APK\n"))
    }

    @Test
    fun `a version that is not a number is a broken manifest`() {
        for (body in listOf("2.3.16 $APK", "abc $APK", "-1 $APK", "0 $APK", "4000756x $APK")) {
            assertEquals(UpdateCheckResult.Refused(UpdateFailure.Malformed), check(body), body)
        }
    }

    @Test
    fun `a record without a file link is a broken manifest`() {
        assertEquals(UpdateCheckResult.Refused(UpdateFailure.Malformed), check("4000756"))
        assertEquals(UpdateCheckResult.Refused(UpdateFailure.Malformed), check("4000756 4000757 $APK"))
        assertEquals(UpdateCheckResult.Refused(UpdateFailure.Malformed), check("https://panel.example.com/nerpa/latest"))
    }

    @Test
    fun `a file link has to be https, the loopback stand excepted`() {
        assertEquals(
            UpdateCheckResult.Refused(UpdateFailure.Malformed),
            check("${CURRENT + 1} http://panel.example.com/nerpa/app.apk"),
        )
        assertEquals(
            UpdateCheckResult.Refused(UpdateFailure.Malformed),
            check("${CURRENT + 1} ftp://panel.example.com/nerpa/app.apk"),
        )
        assertEquals(
            UpdateCheckResult.Available(CURRENT + 1, "http://127.0.0.1:18080/nerpa/app.apk"),
            check("${CURRENT + 1} http://127.0.0.1:18080/nerpa/app.apk"),
        )
    }

    @Test
    fun `an empty or html answer is a broken manifest`() {
        for (body in listOf("", "   \n", "<html><body><a href=\"#\">Download</a></body></html>")) {
            assertEquals(UpdateCheckResult.Refused(UpdateFailure.Malformed), check(body), body)
        }
    }

    @Test
    fun `no network and any non-2xx are the same refusal`() {
        assertEquals(
            UpdateCheckResult.Refused(UpdateFailure.Unreachable),
            UpdateManifest.from(EndpointFetchResult.Transport, CURRENT),
        )
        for (code in listOf(404, 403, 500, 503)) {
            assertEquals(
                UpdateCheckResult.Refused(UpdateFailure.Unreachable),
                UpdateManifest.from(EndpointFetchResult.HttpStatus(code), CURRENT),
                code.toString(),
            )
        }
    }

    @Test
    fun `a whole check asks the address derived from the build`() {
        var asked = ""
        val result = UpdateManifest.check(ENDPOINT, CURRENT) { url ->
            asked = url
            EndpointFetchResult.Content("${CURRENT + 1} $APK")
        }
        assertEquals("https://panel.example.com/nerpa/latest", asked)
        assertEquals(UpdateCheckResult.Available(CURRENT + 1, APK), result)
    }

    @Test
    fun `an address that names no manifest is refused without asking`() {
        for (endpoint in listOf("", "http://panel.example.com/sub/token")) {
            var asked = false
            val result = UpdateManifest.check(endpoint, CURRENT) {
                asked = true
                EndpointFetchResult.Content("4000756 $APK")
            }
            assertEquals(UpdateCheckResult.Refused(UpdateFailure.Malformed), result, endpoint)
            assertFalse(asked, endpoint)
        }
    }

    @Test
    fun `the stand is asked over cleartext loopback and its file link is kept`() {
        var asked = ""
        val result = UpdateManifest.check("http://127.0.0.1:18080/sub/token", CURRENT) { url ->
            asked = url
            EndpointFetchResult.Content("${CURRENT + 1} http://127.0.0.1:18080/nerpa/app.apk")
        }
        assertEquals("http://127.0.0.1:18080/nerpa/latest", asked)
        assertEquals(UpdateCheckResult.Available(CURRENT + 1, "http://127.0.0.1:18080/nerpa/app.apk"), result)
    }
}
