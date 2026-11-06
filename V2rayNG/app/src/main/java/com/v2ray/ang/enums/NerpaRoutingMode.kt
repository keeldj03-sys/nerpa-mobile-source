package com.v2ray.ang.enums

import androidx.annotation.StringRes
import com.v2ray.ang.R

/**
 * The three routing presets the consumer UI offers, ordered from least to most intervention.
 *
 * [storageKey] is what lands in MMKV, so it is a serialized value: renaming one needs a migration
 * and a regression test against the previously released key, exactly like any other persisted field.
 */
enum class NerpaRoutingMode(
    val storageKey: String,
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
) {
    Minimal(
        storageKey = "minimal",
        labelRes = R.string.nerpa_routing_minimal,
        descriptionRes = R.string.nerpa_routing_minimal_desc,
    ),

    Auto(
        storageKey = "auto",
        labelRes = R.string.nerpa_routing_auto,
        descriptionRes = R.string.nerpa_routing_auto_desc,
    ),

    MaxProtection(
        storageKey = "max_protection",
        labelRes = R.string.nerpa_routing_max_protection,
        descriptionRes = R.string.nerpa_routing_max_protection_desc,
    );

    companion object {
        /** Unknown, absent or removed keys fall back to [Auto]; a stored key is never a crash source. */
        val DEFAULT: NerpaRoutingMode = Auto

        fun fromStorageKey(key: String?): NerpaRoutingMode {
            return entries.firstOrNull { it.storageKey == key } ?: DEFAULT
        }
    }
}
