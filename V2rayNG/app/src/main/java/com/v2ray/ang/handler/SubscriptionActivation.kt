package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.SubscriptionUpdateResult
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.CancellationException

/** Where accepting a subscription link stopped, in terms a user can act on. */
enum class ActivationFailure {
    /** Not a usable subscription address (scheme, host or insecure-URL policy). */
    InvalidUrl,

    /** The panel did not answer, or answered with an error status other than 403 or 429. */
    Unreachable,

    /** The panel answered but sent an empty body. */
    EmptyResponse,

    /** The body carried node lines and none of them reached storage. */
    NoParsableNodes,

    /** The endpoint answered with something that is neither a subscription link nor a node package. */
    NotALink,

    /** The operator closed issuance: quota, or the free tier is paused. */
    QuotaClosed,
}

sealed class ActivationOutcome {
    /**
     * @param total node lines the panel sent.
     * @param accepted nodes that reached storage; below total when the panel mixed in lines this
     * build cannot parse, which the screen has to say out loud instead of staying silent.
     */
    data class Accepted(val subscriptionId: String, val total: Int, val accepted: Int) : ActivationOutcome()

    data class Failed(val failure: ActivationFailure, val total: Int = 0) : ActivationOutcome()
}

/**
 * Accepts one personal x-ui subscription link on first run.
 *
 * Committing goes through [AngConfigManager.updateConfigViaSub] rather than a local parser so that
 * usage headers, `lastUpdated` and the group index stay owned by upstream code. The probe is a
 * separate request on purpose: `updateConfigViaSub` folds fetch, decode, parse and store together
 * and reports every early exit as `failureCount = 1`, which cannot tell an unreachable panel from
 * a response full of unparsable lines — and a client with one Connect button has to. Only the
 * probe picks the message; the commit still decides acceptance. [reactivate] runs this same path to
 * re-issue the link into the existing free slot instead of creating a second group.
 */
object SubscriptionActivation {

    fun activate(
        url: String,
        remarks: String,
        updateIntervalMinutes: Long = SubscriptionItem().updateInterval,
    ): ActivationOutcome {
        val link = trimLink(url)?.takeIf { isUsableLink(it) }
            ?: return ActivationOutcome.Failed(ActivationFailure.InvalidUrl)
        val guid = Utils.getUuid()
        return accept(
            guid = guid,
            item = SubscriptionItem(
                remarks = remarks,
                url = link,
                autoUpdate = true,
                updateInterval = updateIntervalMinutes,
            ),
            // A link the operator's panel refused must not linger as a group the user never agreed to.
            onFailure = { MmkvManager.removeSubscription(guid) },
        )
    }

    /**
     * Re-issues the free slot: the same group keeps its remarks and gets the new link.
     *
     * Restoring the previous item whenever accepting the new link fails — a refusal or an exception
     * in the write-then-commit window — is required, not cosmetic: a group left with an empty or
     * half-written url reads as the app corrupting the user's subscription.
     */
    fun reactivate(subscriptionId: String, url: String): ActivationOutcome {
        val link = trimLink(url)?.takeIf { isUsableLink(it) }
            ?: return ActivationOutcome.Failed(ActivationFailure.InvalidUrl)
        val previous = MmkvManager.decodeSubscription(subscriptionId)
            ?: return ActivationOutcome.Failed(ActivationFailure.InvalidUrl)
        return accept(
            guid = subscriptionId,
            item = previous.copy(url = link),
            onFailure = { MmkvManager.encodeSubscription(subscriptionId, previous) },
        )
    }

    /** Trimmed link, or null when the text is blank. Pure, so JVM-testable. */
    fun trimLink(url: String): String? = url.trim().takeIf { it.isNotEmpty() }

    /** Device-only: `Utils.isValidUrl` and `isValidSubUrl` run on `android.webkit.URLUtil`. */
    private fun isUsableLink(link: String): Boolean =
        Utils.isValidUrl(link) && Utils.isValidSubUrl(link)

    private fun accept(
        guid: String,
        item: SubscriptionItem,
        onFailure: () -> Unit,
    ): ActivationOutcome {
        val body = probe(item.url)
            ?: return ActivationOutcome.Failed(ActivationFailure.Unreachable)
        if (body.isBlank()) {
            return ActivationOutcome.Failed(ActivationFailure.EmptyResponse)
        }
        val total = countNodeLines(Utils.decode(body), body)
        if (total == 0) {
            return ActivationOutcome.Failed(ActivationFailure.NoParsableNodes)
        }

        // An exception here must roll back like a refusal does: a group left holding the new link
        // with nothing committed behind it is the partial write that must never be observable.
        // `syncOne` stays outside the window — by then the record is already consistent, and undoing
        // a served link because a later refresh failed would throw away a valid subscription. Being
        // outside the window it has to carry its own guard, or a scheduler failure would surface as
        // an activation failure.
        val outcome = try {
            MmkvManager.encodeSubscription(guid, item)
            val result = AngConfigManager.updateConfigViaSub(SubscriptionCache(guid, item))
            classify(guid, total, result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onFailure()
            LogUtil.e(AppConfig.TAG, "Subscription activation commit failed for group $guid", e)
            return ActivationOutcome.Failed(ActivationFailure.NoParsableNodes, total)
        }
        if (outcome is ActivationOutcome.Failed) {
            onFailure()
            return outcome
        }

        // Scheduling the periodic refresh must not undo a served subscription: the record and its
        // nodes are committed by now, and the caller has to see `Accepted` to write the slot marker.
        try {
            SubscriptionUpdater.syncOne(subId = guid)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Subscription activation auto-update scheduling failed for group $guid", e)
        }
        return outcome
    }

    /** Null means "no usable answer": transport error or a non-2xx status. */
    private fun probe(url: String): String? {
        return try {
            HttpUtil.getUrlContentWithUserAgent(UrlContentRequest(url = url))
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Activation probe failed", e)
            null
        }
    }

    /** Pure so the stage mapping is testable without network or Android. */
    fun classify(subscriptionId: String, total: Int, result: SubscriptionUpdateResult): ActivationOutcome {
        val accepted = result.configCount
        return if (accepted <= 0) {
            ActivationOutcome.Failed(ActivationFailure.NoParsableNodes, total)
        } else {
            ActivationOutcome.Accepted(subscriptionId, total, accepted)
        }
    }

    /** Mirrors [AngConfigManager.parseBatchConfig]: distinct raw lines, blanks dropped. */
    fun countNodeLines(text: String): Int = text.lines().distinct().count { it.isNotBlank() }

    /**
     * Node lines in a panel answer. `Utils.decode` returns an empty string, not null, when the body
     * is not base64, so counting only the decoded text rejects a plain-text subscription the commit
     * would accept — hence the same raw-body fallback [AngConfigManager.parseConfigViaSub] uses.
     */
    fun countNodeLines(decoded: String, raw: String): Int =
        countNodeLines(decoded).takeIf { it > 0 } ?: countNodeLines(raw)
}
