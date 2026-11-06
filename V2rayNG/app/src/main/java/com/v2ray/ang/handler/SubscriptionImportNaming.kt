package com.v2ray.ang.handler

/**
 * Name for a subscription group created from an imported link.
 *
 * Upstream falls back to the literal `import sub`, which is neither localizable nor something a
 * consumer can read as their own group. The fragment wins because panel links carry `#Name` when
 * the operator set one; the host is the next thing a person actually recognizes. The host case is
 * a lambda because the localized pattern is a resource, and resources belong to the caller.
 */
object SubscriptionImportNaming {

    fun remarks(
        fragment: String?,
        host: String?,
        withHost: (String) -> String,
        plainName: String,
    ): String {
        val title = fragment?.trim()?.takeIf { it.isNotEmpty() }
        if (title != null) return title
        val server = host?.trim()?.takeIf { it.isNotEmpty() }
        return if (server != null) withHost(server) else plainName
    }
}
