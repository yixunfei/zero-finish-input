package dev.zeroinput.ime.expressions

import dev.zeroinput.ime.ui.EmojiCategory
import dev.zeroinput.ime.ui.EmojiEntry
import dev.zeroinput.ime.ui.KaomojiGroup
import dev.zeroinput.ime.ui.PersonalExpressionsUi
import dev.zeroinput.userdata.PersonalExpressions
import java.util.Locale

internal fun PersonalExpressions.presentation() = PersonalExpressionsUi(
    custom = custom.map { entry ->
        EmojiEntry(entry.value, EmojiCategory.CUSTOM, entry.keywords,
            KaomojiGroup.entries.firstOrNull { it.name.lowercase(Locale.ROOT) == entry.group } ?: KaomojiGroup.OTHER,
            entry.id, entry.name)
    },
    favorites = favorites.toSet(),
)
