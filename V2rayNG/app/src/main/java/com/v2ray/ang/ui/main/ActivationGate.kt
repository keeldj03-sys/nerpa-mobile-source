package com.v2ray.ang.ui.main

import com.v2ray.ang.AppConfig

/** One subscription reduced to the two things the first-run gate asks about. */
data class SubscriptionFacts(
    val guid: String,
    val hasNodes: Boolean,
    val expired: Boolean,
)

/**
 * Decides whether the activation screen has to be on top.
 *
 * Expiry is only as fresh as the last attempt to update, so a dead free subscription next to a live
 * one the user added does not pull them back to this screen — that is the whole point of checking
 * every subscription rather than the free slot alone.
 */
object ActivationGate {

    /** Panels publish expiry in the userinfo header; `0` means the header was absent. */
    fun isExpired(expireSeconds: Long, nowSeconds: Long): Boolean =
        expireSeconds > 0L && expireSeconds < nowSeconds

    /**
     * The stored group list is wider than the subscriptions this rule asks about. The basket holding
     * manually added nodes (`AppConfig.DEFAULT_SUBSCRIPTION_ID`) is always there, and the "All
     * groups" pseudo-entry (`guid = ""`) joins it whenever `pref_group_all_display` is on. Neither is
     * a subscription: the basket's nodes reach the rule as `hasManualNodes`, and the pseudo-group,
     * which only mirrors the other groups, reaches it as nothing at all.
     */
    fun isSubscription(groupId: String): Boolean =
        groupId.isNotEmpty() && groupId != AppConfig.DEFAULT_SUBSCRIPTION_ID

    /**
     * Access is whatever the person can actually connect with: a subscription that has nodes and
     * whose expiry the panel has not passed, or the manually added nodes the operator handed over.
     * The stored activation marker is deliberately not consulted — a subscription shared in from
     * outside this app works before any screen has been seen, and setup on top of it would only hide
     * a working connection.
     */
    fun shouldShow(subscriptions: List<SubscriptionFacts>, hasManualNodes: Boolean): Boolean =
        !hasManualNodes && subscriptions.none { it.hasNodes && !it.expired }
}
