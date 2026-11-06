package com.v2ray.ang.ui.main

import com.v2ray.ang.AppConfig.DEFAULT_SUBSCRIPTION_ID
import com.v2ray.ang.dto.GroupMapItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MainGroupVisibilityTest {

    private val defaultGroup = GroupMapItem(id = DEFAULT_SUBSCRIPTION_ID, remarks = "Default")
    private val nerpaGroup = GroupMapItem(id = NERPA_ID, remarks = "Nerpa")
    private val secondGroup = GroupMapItem(id = SECOND_ID, remarks = "Second")

    @Test
    fun emptyDefaultBucketIsNotShownAsTab() {
        val groups = listOf(defaultGroup, nerpaGroup)

        assertEquals(
            listOf(nerpaGroup),
            MainGroupVisibility.visibleGroups(groups, serverCounts = mapOf(DEFAULT_SUBSCRIPTION_ID to 0, NERPA_ID to 6)),
        )
    }

    @Test
    fun defaultBucketWithAManualNodeIsShownAgain() {
        val groups = listOf(defaultGroup, nerpaGroup)

        assertEquals(
            groups,
            MainGroupVisibility.visibleGroups(groups, serverCounts = mapOf(DEFAULT_SUBSCRIPTION_ID to 1, NERPA_ID to 6)),
        )
    }

    @Test
    fun otherGroupsStayVisibleWhileEmpty() {
        val groups = listOf(nerpaGroup, secondGroup)

        assertEquals(
            groups,
            MainGroupVisibility.visibleGroups(groups, serverCounts = mapOf(NERPA_ID to 0, SECOND_ID to 0)),
        )
    }

    @Test
    fun ungroupedBucketIsTitledWithTheSuppliedName() {
        assertEquals(
            "My nodes",
            MainGroupVisibility.tabTitle(DEFAULT_SUBSCRIPTION_ID, "Default", "My nodes"),
        )
    }

    @Test
    fun subscriptionKeepsItsStoredName() {
        assertEquals("Nerpa", MainGroupVisibility.tabTitle(NERPA_ID, "Nerpa", "My nodes"))
    }

    @Test
    fun allGroupsPseudoEntryKeepsItsOwnTitle() {
        assertEquals("All", MainGroupVisibility.tabTitle("", "All", "My nodes"))
    }

    private companion object {
        const val NERPA_ID = "6f1f2a34-0000-4000-8000-000000000001"
        const val SECOND_ID = "6f1f2a34-0000-4000-8000-000000000002"
    }
}
