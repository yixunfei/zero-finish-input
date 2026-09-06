package dev.zeroinput.userdata

import dev.zeroinput.engine.api.InputLanguage

data class UserTerm(
    val id: String,
    val shortcut: String,
    val value: String,
    val language: InputLanguage,
    val frequency: Int,
    val lastUsedEpochMillis: Long,
)

