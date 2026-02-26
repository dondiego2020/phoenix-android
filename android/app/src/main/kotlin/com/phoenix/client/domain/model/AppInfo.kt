package com.phoenix.client.domain.model

/** Represents an installed app shown in the split-tunnel picker. */
data class AppInfo(
    val packageName: String,
    val name: String,
)
