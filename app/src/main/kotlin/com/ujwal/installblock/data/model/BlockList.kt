// SPDX-License-Identifier: GPL-3.0-or-later
package com.ujwal.installblock.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire format for the blocklist JSON file hosted on GitHub. */
@Serializable
data class BlockList(
    @SerialName("version") val version: Int,
    @SerialName("apps")    val apps: List<AppEntry>,
)

