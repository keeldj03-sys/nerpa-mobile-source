package com.v2ray.ang.ui.main

import com.v2ray.ang.AppConfig

/**
 * Which of the upstream surfaces the distributed Nerpa Mobile build shows.
 *
 * The screens stay in the tree and in the manifest — a hand-added node still opens the same editors —
 * and only their entry points close here. They are not reachable from outside either: every one of
 * them is `exported = false`, so `adb shell am start -n <package>/<activity>` is refused with a
 * SecurityException, and an operator who has to look at one builds with these allowlists opened.
 * Deleting them instead would fork the configuration pipeline for a build that is distributed, not
 * developed, and would make the GPL source mirror disagree with the APK it describes.
 *
 * The lists are allowlists, so a destination or menu item added by a later upstream merge starts out
 * hidden and has to be opened on purpose. `NerpaSurfacePolicyTest` asserts the complement exactly for
 * that reason.
 */
object NerpaSurfacePolicy {

    val visibleDrawerDestinations: Set<MainDestination> = setOf(
        MainDestination.Settings,
        MainDestination.About,
    )

    val visibleMoreMenuActions: Set<MainMoreMenuAction> = setOf(
        MainMoreMenuAction.RestartService,
        MainMoreMenuAction.DeleteAll,
        MainMoreMenuAction.LocateSelected,
        MainMoreMenuAction.SortByTestResults,
        MainMoreMenuAction.TestAll,
        MainMoreMenuAction.TestAllRealPing,
        MainMoreMenuAction.UpdateSubscriptions,
    )

    /**
     * The entries of the «+» menu. A hand-added node is the one power-user path a subscriber left without
     * a working subscription needs, so it keeps its entry, and `vless://` is the protocol this fork issues.
     * Every other hidden protocol form except HTTP is still reachable by pasting its link into the
     * clipboard entry, which parses the same set of schemes (`AngConfigManager.configFmtParsers`); an
     * `http://` outbound and the two composite profiles have no link form, so hiding their forms closes
     * them for the distributed build.
     */
    internal val visibleImportActions: Set<ImportMenuAction> = setOf(
        ImportMenuAction.QRCode,
        ImportMenuAction.Clipboard,
        ImportMenuAction.Vless,
    )

    /** The tuning groups in `SettingsScreen`; the UI, Advanced and free-access rows stay visible. */
    val advancedTuningVisible = false

    /**
     * The settings this build offers no editor for, in the order the groups appear on screen.
     *
     * Hiding a group does not undo what an older install already wrote into it, and the core reads
     * these keys on every start — a saved root mode or fake-DNS pair keeps applying with no switch
     * left to turn it back. An absent key and a key holding the shipped default are the same read for
     * `MmkvManager`, so removing the keys is both the reset and the proof that nothing else has to be
     * rewritten. Routing and the two subscription markers are deliberately absent: the routing mode
     * row stays on the main screen and owns those slots, and the markers are what the activation gate
     * and the free-issuance button are keyed by.
     */
    val hiddenTuningKeys: List<String> = listOf(
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
}
