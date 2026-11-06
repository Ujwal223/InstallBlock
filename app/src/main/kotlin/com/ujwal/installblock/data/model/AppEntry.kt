// SPDX-License-Identifier: GPL-3.0-or-later
package com.ujwal.installblock.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One entry in the blocklist JSON.
 *
 * Icon URLs come from untrusted third-party hosts :  they are ONLY loaded through the
 * sanitizing icon pipeline ([com.ujwal.installblock.icons.IconRepository]). Never render
 * the raw URL in a WebView or pass it to a generic image loader.
 */
@Serializable
data class AppEntry(
    @SerialName("name")    val name: String,
    @SerialName("package") val packageName: String,
    @SerialName("icon")    val iconUrl: String,
    @SerialName("category") val category: String = "Other",
    @SerialName("installCount") val installCount: Long = 0L,
)
