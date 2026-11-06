package com.v2ray.ang.handler

import android.content.Context
import androidx.annotation.StringRes
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.enums.NerpaRoutingMode

/** One routing rule of a [NerpaRoutingMode], before it is turned into a stored [RulesetItem]. */
private data class ModeRule(
    val id: String,
    @StringRes val remarkRes: Int,
    val outboundTag: String,
    val domains: List<String>? = null,
    val ips: List<String>? = null,
    val port: String? = null,
)

/**
 * Maps the three consumer routing modes onto the ruleset list the core reads.
 *
 * The rules are written in the syntax upstream already uses: `geosite:`/`geoip:` group names, and
 * `CoreConfigManager.appendRoutingUserRule` rewrites `geoip:private` to the `ext:` file the core
 * ships, so no path handling belongs here. Group names were checked against the `geosite.dat` and
 * `geoip.dat` copies inside `libv2ray.aar` (see FORK.md) — an unknown group is not an error, the
 * core silently matches nothing, and the mode would quietly stop doing what its label promises.
 *
 * [applyMode] replaces the whole ruleset list instead of going through
 * [SettingsManager.resetRoutingRulesetsFromPresets]: that helper keeps every rule the user marked
 * `locked` and appends the preset, so switching modes repeatedly would accumulate the rules of
 * all three modes. Locked rules belong to the power-user ruleset editor, which the consumer client
 * does not offer.
 */
object NerpaRouting {

    /**
     * `AsIs` (the upstream default) only lets `geosite:` rules match connections that still carry
     * a domain, so the ad block and the RU bypass would depend on what the resolver happened to
     * return. `IPIfNonMatch` resolves the name and then lets the geo rules work on the address.
     */
    private const val DOMAIN_STRATEGY = "IPIfNonMatch"

    private val privateNetworkRule = ModeRule(
        id = "nerpa_private",
        remarkRes = R.string.nerpa_rule_private_direct,
        outboundTag = AppConfig.TAG_DIRECT,
        domains = listOf(AppConfig.GEOSITE_PRIVATE),
        ips = listOf(AppConfig.GEOIP_PRIVATE),
    )

    private val adsRule = ModeRule(
        id = "nerpa_ads",
        remarkRes = R.string.nerpa_rule_ads_block,
        outboundTag = AppConfig.TAG_BLOCKED,
        domains = listOf(AppConfig.GEOSITE_ADS_ALL),
    )

    private val russiaRule = ModeRule(
        id = "nerpa_russia",
        remarkRes = R.string.nerpa_rule_ru_direct,
        outboundTag = AppConfig.TAG_DIRECT,
        domains = listOf(AppConfig.GEOSITE_CATEGORY_RU),
        ips = listOf(AppConfig.GEOIP_RU),
    )

    private val fallbackRule = ModeRule(
        id = "nerpa_fallback",
        remarkRes = R.string.nerpa_rule_proxy_fallback,
        outboundTag = AppConfig.TAG_PROXY,
        port = "0-65535",
    )

    private val rulesByMode: Map<NerpaRoutingMode, List<ModeRule>> = mapOf(
        NerpaRoutingMode.Minimal to listOf(privateNetworkRule, fallbackRule),
        NerpaRoutingMode.Auto to listOf(adsRule, privateNetworkRule, russiaRule, fallbackRule),
        NerpaRoutingMode.MaxProtection to listOf(adsRule, privateNetworkRule, fallbackRule),
    )

    /** Rule order is match order, so the list is stored exactly as returned here. */
    private fun rulesOf(mode: NerpaRoutingMode): List<ModeRule> = rulesByMode.getValue(mode)

    /** [remark] resolves a rule name from its string resource, which keeps the mapping testable. */
    fun rulesets(mode: NerpaRoutingMode, remark: (resId: Int) -> String): List<RulesetItem> {
        return rulesOf(mode).map {
            RulesetItem(
                id = it.id,
                remarks = remark(it.remarkRes),
                domain = it.domains,
                ip = it.ips,
                outboundTag = it.outboundTag,
                port = it.port,
                enabled = true,
                locked = false,
            )
        }
    }

    /**
     * Writes the mode and the rules it implies. Blocking MMKV work belongs to the caller's IO
     * dispatcher; the core picks the new rules up when the next config is built, so a live
     * connection keeps running on the previous mode until it is reconnected.
     */
    fun applyMode(context: Context, mode: NerpaRoutingMode) {
        val rulesetList = rulesets(mode) { context.getString(it) }.toMutableList()
        MmkvManager.encodeRoutingRulesets(rulesetList)
        MmkvManager.encodeSettings(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY, DOMAIN_STRATEGY)
        SettingsManager.setNerpaRoutingMode(mode)
    }
}
