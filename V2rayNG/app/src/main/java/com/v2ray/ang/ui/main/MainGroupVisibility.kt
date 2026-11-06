package com.v2ray.ang.ui.main

import com.v2ray.ang.AppConfig.DEFAULT_SUBSCRIPTION_ID
import com.v2ray.ang.dto.GroupMapItem

/**
 * Which of the stored groups get a tab.
 *
 * The default bucket is where a hand-added node goes — a profile with an empty `subscriptionId`
 * resolves to [DEFAULT_SUBSCRIPTION_ID] in `MmkvManager`, and the import paths name that bucket
 * outright. Upstream creates it during the first app run whether or not anything is ever added to
 * it, so on a client whose nodes come from one activated link it stayed visible as a permanently
 * empty tab next to the real group. Hiding it, rather than not creating it, keeps manual imports
 * landing somewhere that exists: the tab returns as soon as the bucket holds a server.
 */
object MainGroupVisibility {

    fun visibleGroups(groups: List<GroupMapItem>, serverCounts: Map<String, Int>): List<GroupMapItem> =
        groups.filterNot { it.id == DEFAULT_SUBSCRIPTION_ID && (serverCounts[it.id] ?: 0) == 0 }

    /**
     * The tab title. The bucket is named at display time because installs already store the
     * literal `Default` it was created with, and rewriting that persisted remark to relabel one tab
     * would be a migration of serialized data.
     */
    fun tabTitle(groupId: String, storedRemarks: String, manualBucketName: String): String =
        if (groupId == DEFAULT_SUBSCRIPTION_ID) manualBucketName else storedRemarks
}
