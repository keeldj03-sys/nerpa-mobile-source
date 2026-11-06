package com.v2ray.ang.ui.main

import com.v2ray.ang.AppConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NerpaSurfacePolicyTest {

    @Test
    fun drawerKeepsOnlySettingsAndAbout() {
        assertEquals(
            setOf(MainDestination.Settings, MainDestination.About),
            NerpaSurfacePolicy.visibleDrawerDestinations,
        )
    }

    /**
     * An upstream destination added later closes by default, so this assertion has to fail loudly
     * rather than let the new entry reach the distributed build unreviewed.
     */
    @Test
    fun hiddenDrawerDestinationsAreExactlyThePowerUserScreens() {
        assertEquals(
            setOf(
                MainDestination.Subscriptions,
                MainDestination.PerAppProxy,
                MainDestination.Routing,
                MainDestination.UserAssets,
                MainDestination.Logcat,
                MainDestination.BackupRestore,
            ),
            MainDestination.entries.toSet() - NerpaSurfacePolicy.visibleDrawerDestinations,
        )
    }

    @Test
    fun moreMenuKeepsSubscriptionRefreshAndWholeListDelete() {
        assertTrue(MainMoreMenuAction.UpdateSubscriptions in NerpaSurfacePolicy.visibleMoreMenuActions)
        assertTrue(MainMoreMenuAction.DeleteAll in NerpaSurfacePolicy.visibleMoreMenuActions)
        assertTrue(MainMoreMenuAction.RestartService in NerpaSurfacePolicy.visibleMoreMenuActions)
        assertEquals(
            setOf(
                MainMoreMenuAction.DeleteDuplicate,
                MainMoreMenuAction.DeleteInvalid,
                MainMoreMenuAction.ExportAll,
            ),
            MainMoreMenuAction.entries.toSet() - NerpaSurfacePolicy.visibleMoreMenuActions,
        )
    }

    @Test
    fun importMenuKeepsQRCodeClipboardAndOneManualProtocol() {
        assertEquals(
            setOf(
                ImportMenuAction.QRCode,
                ImportMenuAction.Clipboard,
                ImportMenuAction.Vless,
            ),
            NerpaSurfacePolicy.visibleImportActions,
        )
    }

    /**
     * The clipboard and QR entries parse a link of every protocol except HTTP, so the per-protocol forms,
     * the file import and the two composite profiles have no place in the menu a subscriber opens; a form
     * added by a later upstream merge starts hidden and has to be opened on purpose.
     */
    @Test
    fun hiddenImportMenuActionsAreExactlyThePowerUserForms() {
        assertEquals(
            setOf(
                ImportMenuAction.LocalFile,
                ImportMenuAction.PolicyGroup,
                ImportMenuAction.ProxyChain,
                ImportMenuAction.Vmess,
                ImportMenuAction.Shadowsocks,
                ImportMenuAction.Socks,
                ImportMenuAction.Http,
                ImportMenuAction.Trojan,
                ImportMenuAction.WireGuard,
                ImportMenuAction.Hysteria2,
            ),
            ImportMenuAction.entries.toSet() - NerpaSurfacePolicy.visibleImportActions,
        )
    }

    /**
     * Hiding a tunable without naming its key would leave the value applied by the core with no way
     * to switch it off on a device that already has it set, so every hidden group has to be listed.
     */
    @Test
    fun resetCoversEveryHiddenTunable() {
        val keys = NerpaSurfacePolicy.hiddenTuningKeys.toSet()
        val expected = setOf(
            AppConfig.PREF_IPV6_ENABLED,
            AppConfig.PREF_PREFER_IPV6,
            AppConfig.PREF_LOCAL_DNS_ENABLED,
            AppConfig.PREF_FAKE_DNS_ENABLED,
            AppConfig.PREF_VPN_DNS,
            AppConfig.PREF_APPEND_HTTP_PROXY,
            AppConfig.PREF_VPN_BYPASS_LAN,
            AppConfig.PREF_VPN_INTERFACE_ADDRESS_CONFIG_INDEX,
            AppConfig.PREF_VPN_MTU,
            AppConfig.PREF_USE_HEV_TUNNEL,
            AppConfig.PREF_HEV_TUNNEL_LOGLEVEL,
            AppConfig.PREF_HEV_TUNNEL_RW_TIMEOUT,
            AppConfig.PREF_SNIFFING_ENABLED,
            AppConfig.PREF_ROUTE_ONLY_ENABLED,
            AppConfig.PREF_ENABLE_LOCAL_PROXY,
            AppConfig.PREF_PROXY_SHARING,
            AppConfig.PREF_SOCKS_PORT,
            AppConfig.PREF_DYNAMIC_SOCKS_PORT,
            AppConfig.PREF_SOCKS_USERNAME,
            AppConfig.PREF_SOCKS_PASSWORD,
            AppConfig.PREF_SOCKS_ENABLE_UDP,
            AppConfig.PREF_REMOTE_DNS,
            AppConfig.PREF_DOMESTIC_DNS,
            AppConfig.PREF_DNS_HOSTS,
            AppConfig.PREF_LOGLEVEL,
            AppConfig.PREF_OUTBOUND_DOMAIN_RESOLVE_METHOD,
            AppConfig.PREF_MUX_ENABLED,
            AppConfig.PREF_MUX_CONCURRENCY,
            AppConfig.PREF_MUX_XUDP_CONCURRENCY,
            AppConfig.PREF_MUX_XUDP_QUIC,
            AppConfig.PREF_FRAGMENT_ENABLED,
            AppConfig.PREF_FRAGMENT_PACKETS,
            AppConfig.PREF_FRAGMENT_LENGTH,
            AppConfig.PREF_FRAGMENT_INTERVAL,
            AppConfig.PREF_FRAGMENT_MAXSPLIT,
            AppConfig.PREF_OBSERVATORY_LEAST_PING_INTERVAL,
            AppConfig.PREF_OBSERVATORY_LEAST_LOAD_INTERVAL,
            AppConfig.PREF_OBSERVATORY_LEAST_LOAD_METHOD,
            AppConfig.PREF_OBSERVATORY_LEAST_LOAD_SAMPLING,
            AppConfig.PREF_OBSERVATORY_LEAST_LOAD_TIMEOUT,
            AppConfig.PREF_MODE,
            AppConfig.PREF_ROOT_MODE_ENABLE,
            AppConfig.PREF_ROOT_LAN_SHARING,
            AppConfig.PREF_BYPASS_APPS,
            AppConfig.PREF_GEO_FILES_SOURCES,
        )
        assertEquals(expected, keys)
        assertEquals(NerpaSurfacePolicy.hiddenTuningKeys.size, keys.size, "duplicate key in the reset list")
    }

    /**
     * The reset must not reach anything the distributed build still edits or needs: appearance and
     * list options live in the visible groups, auto-start and the test URLs in the visible Advanced
     * group, routing belongs to the visible mode row, and the two subscription markers are what the
     * activation gate and the free-issuance slot are keyed by.
     */
    @Test
    fun resetLeavesVisibleAndActivationStateAlone() {
        val keys = NerpaSurfacePolicy.hiddenTuningKeys.toSet()
        val untouched = listOf(
            AppConfig.PREF_LANGUAGE,
            AppConfig.PREF_UI_MODE_NIGHT,
            AppConfig.PREF_DYNAMIC_COLOR,
            AppConfig.PREF_SPEED_ENABLED,
            AppConfig.PREF_CONFIRM_REMOVE,
            AppConfig.PREF_DOUBLE_COLUMN_DISPLAY,
            AppConfig.PREF_GROUP_ALL_DISPLAY,
            AppConfig.PREF_IS_BOOTED,
            AppConfig.PREF_DELAY_TEST_URL,
            AppConfig.PREF_REAL_PING_CONCURRENCY,
            AppConfig.PREF_IP_API_URL,
            AppConfig.PREF_ROUTING_RULESET,
            AppConfig.PREF_ROUTING_DOMAIN_STRATEGY,
            AppConfig.PREF_NERPA_ROUTING_MODE,
            AppConfig.PREF_ACTIVATION_SUBSCRIPTION_ID,
            AppConfig.PREF_FREE_SUBSCRIPTION_ID,
        )
        untouched.forEach { key ->
            assertFalse(key in keys, "the reset must not clear $key")
        }
    }

    @Test
    fun advancedTuningGroupsAreHidden() {
        assertFalse(NerpaSurfacePolicy.advancedTuningVisible)
    }
}
