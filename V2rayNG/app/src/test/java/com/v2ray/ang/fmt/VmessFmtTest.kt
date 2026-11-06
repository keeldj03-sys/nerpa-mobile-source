package com.v2ray.ang.fmt

import android.text.TextUtils
import android.util.Base64
import android.util.Log
import com.v2ray.ang.AppConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.Mockito.mockStatic
import java.util.Base64 as JavaBase64

/**
 * Unit tests for the legacy Vmess format.
 *
 * The legacy format is a base64 body of the node JSON. A shared link may carry a remark after
 * `#`, and `#` is not in the base64 alphabet, so the body must be cut before decoding — otherwise
 * decoding fails with `bad base-64` and the node is dropped without a trace.
 *
 * Tests cover:
 * - A link with a remark: the body decodes and the remark becomes the node name
 * - A link without a remark: the `ps` field of the JSON is the node name
 * - A percent-encoded remark: it is decoded
 * - A `+` inside the body: it survives decoding and is not turned into a space
 */
class VmessFmtTest {

    companion object {
        private const val VMESS_SCHEME = "vmess://"

        private fun legacyBody(
            ps: String = "Node From Json",
            add: String = "example.com",
            port: String = "443",
            id: String = "b831381d-6324-4d53-ad4f-8cda48b30811",
            net: String = "tcp"
        ): String {
            val json = "{\"v\":\"2\",\"ps\":\"$ps\",\"add\":\"$add\",\"port\":\"$port\"," +
                "\"id\":\"$id\",\"scy\":\"auto\",\"aid\":\"0\",\"net\":\"$net\",\"type\":\"none\"," +
                "\"host\":\"\",\"path\":\"\",\"tls\":\"\",\"sni\":\"\",\"alpn\":\"\"}"
            return JavaBase64.getEncoder().encodeToString(json.toByteArray())
        }
    }

    private lateinit var mockBase64: MockedStatic<Base64>
    private lateinit var mockLog: MockedStatic<Log>
    private lateinit var mockTextUtils: MockedStatic<TextUtils>

    @BeforeEach
    fun setUp() {
        mockLog = mockStatic(Log::class.java, Mockito.RETURNS_DEFAULTS)

        mockBase64 = mockStatic(Base64::class.java)
        mockBase64.`when`<ByteArray> {
            Base64.decode(Mockito.anyString(), Mockito.anyInt())
        }.thenAnswer { invocation ->
            val input = invocation.arguments[0] as String
            val flags = invocation.arguments[1] as Int
            // Android accepts both alphabets when the URL-safe flag is set.
            val normalized = if ((flags and Base64.URL_SAFE) != 0) {
                input.replace('-', '+').replace('_', '/')
            } else {
                input
            }
            // Throws on invalid input, like the platform decoder does.
            JavaBase64.getDecoder().decode(normalized)
        }

        mockTextUtils = mockStatic(TextUtils::class.java)
        mockTextUtils.`when`<Boolean> {
            TextUtils.isEmpty(Mockito.any())
        }.thenAnswer { invocation ->
            val value = invocation.arguments[0] as CharSequence?
            value.isNullOrEmpty()
        }
    }

    @AfterEach
    fun tearDown() {
        mockTextUtils.close()
        mockBase64.close()
        mockLog.close()
    }

    @Test
    fun test_parse_remarkAfterHashBecomesNodeName() {
        val link = "$VMESS_SCHEME${legacyBody()}#My%20Node"

        val result = VmessFmt.parse(link)

        assertNotNull(result)
        assertEquals("My Node", result?.remarks)
        assertEquals("example.com", result?.server)
        assertEquals("443", result?.serverPort)
        assertEquals("b831381d-6324-4d53-ad4f-8cda48b30811", result?.password)
        assertEquals(AppConfig.DEFAULT_SECURITY, result?.method)
        assertEquals("tcp", result?.network)
    }

    @Test
    fun test_parse_withoutRemarkUsesPsField() {
        val link = "$VMESS_SCHEME${legacyBody(ps = "Node From Json")}"

        val result = VmessFmt.parse(link)

        assertNotNull(result)
        assertEquals("Node From Json", result?.remarks)
    }

    @Test
    fun test_parse_remarkWithPlusKeepsPlus() {
        val link = "$VMESS_SCHEME${legacyBody()}#a+b"

        val result = VmessFmt.parse(link)

        assertNotNull(result)
        assertEquals("a+b", result?.remarks)
    }

    @Test
    fun test_parse_bodyWithPlusKeepsPlusInPassword() {
        val link = "$VMESS_SCHEME${legacyBody(id = "abc+def")}#Node"

        val result = VmessFmt.parse(link)

        assertNotNull(result)
        assertEquals("abc+def", result?.password)
    }
}
