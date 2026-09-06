package dev.zeroinput.ime.ui

internal object KeyboardLayouts {
    fun nineKey(context: android.content.Context, languageLabel: String): List<List<KeySpec>> = listOf(
        listOf(KeySpec("'", context.getString(R.string.pinyin_separator), KeyboardAction.Text("'")),
            digit("2 ABC", "2"), digit("3 DEF", "3"),
            KeySpec("⌫", context.getString(R.string.key_backspace), KeyboardAction.Backspace, 0.8f, KeyStyle.MODIFIER)),
        listOf(digit("4 GHI", "4"), digit("5 JKL", "5"), digit("6 MNO", "6"),
            KeySpec("，", context.getString(R.string.key_comma), KeyboardAction.Text(","), 0.8f)),
        listOf(digit("7 PQRS", "7"), digit("8 TUV", "8"), digit("9 WXYZ", "9"),
            KeySpec("。", context.getString(R.string.key_period), KeyboardAction.Text("."), 0.8f)),
        listOf(KeySpec("?123", context.getString(R.string.key_symbols), KeyboardAction.ShowSymbols, 1f, KeyStyle.MODIFIER),
            KeySpec(languageLabel, context.getString(R.string.key_language), KeyboardAction.SwitchLanguage, 0.8f, KeyStyle.MODIFIER),
            KeySpec(context.getString(R.string.key_space), context.getString(R.string.key_space), KeyboardAction.Space, 2f),
            KeySpec("↵", context.getString(R.string.key_enter), KeyboardAction.Enter, 0.8f, KeyStyle.PRIMARY)),
    )

    private fun digit(label: String, code: String) = KeySpec(label, label, KeyboardAction.Text(code))

    fun letters(shifted: Boolean, languageLabel: String): List<List<KeySpec>> {
        val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        return listOf(
            characterRow(rows[0], shifted),
            characterRow(rows[1], shifted),
            listOf(
                KeySpec("⇧", "切换大写", KeyboardAction.Shift, 1.35f, KeyStyle.MODIFIER),
            ) + characterRow(rows[2], shifted) + listOf(
                KeySpec("⌫", "退格", KeyboardAction.Backspace, 1.35f, KeyStyle.MODIFIER),
            ),
            listOf(
                KeySpec("?123", "符号键盘", KeyboardAction.ShowSymbols, 1.35f, KeyStyle.MODIFIER),
                KeySpec(languageLabel, "切换中英文", KeyboardAction.SwitchLanguage, 1.1f, KeyStyle.MODIFIER),
                KeySpec("，", "逗号", KeyboardAction.Text(","), 0.9f),
                KeySpec("空格", "空格", KeyboardAction.Space, 3.8f),
                KeySpec("。", "句号", KeyboardAction.Text("."), 0.9f),
                KeySpec("↵", "回车", KeyboardAction.Enter, 1.35f, KeyStyle.PRIMARY),
            ),
        )
    }

    fun symbols(languageLabel: String): List<List<KeySpec>> = listOf(
        symbolRow("1234567890"),
        listOf("@", "#", "¥", "_", "&", "-", "+", "(", ")", "/").map(::symbolKey),
        listOf(
            KeySpec("#+=", "更多符号", KeyboardAction.ShowMoreSymbols, 1.2f, KeyStyle.MODIFIER),
        ) + listOf("*", "\"", "'", ":", ";", "!", "?").map(::symbolKey) + listOf(
            KeySpec("⌫", "退格", KeyboardAction.Backspace, 1.2f, KeyStyle.MODIFIER),
        ),
        listOf(
            KeySpec("ABC", "字母键盘", KeyboardAction.ShowLetters, 1.35f, KeyStyle.MODIFIER),
            KeySpec(languageLabel, "切换中英文", KeyboardAction.SwitchLanguage, 1.1f, KeyStyle.MODIFIER),
            KeySpec(",", "逗号", KeyboardAction.Text(","), 0.9f),
            KeySpec("空格", "空格", KeyboardAction.Space, 3.8f),
            KeySpec(".", "句号", KeyboardAction.Text("."), 0.9f),
            KeySpec("↵", "回车", KeyboardAction.Enter, 1.35f, KeyStyle.PRIMARY),
        ),
    )

    fun moreSymbols(languageLabel: String): List<List<KeySpec>> = listOf(
        symbolRow("[]{}<>|\\"),
        symbolRow("~^%*=\""),
        listOf("€", "£", "$", "¢", "©", "®", "°", "…").map(::symbolKey),
        listOf(
            KeySpec("123", "基础符号", KeyboardAction.ShowSymbols, 1.35f, KeyStyle.MODIFIER),
            KeySpec(languageLabel, "切换中英文", KeyboardAction.SwitchLanguage, 1.1f, KeyStyle.MODIFIER),
            KeySpec(",", "逗号", KeyboardAction.Text(","), 0.9f),
            KeySpec("空格", "空格", KeyboardAction.Space, 3.8f),
            KeySpec(".", "句号", KeyboardAction.Text("."), 0.9f),
            KeySpec("↵", "回车", KeyboardAction.Enter, 1.35f, KeyStyle.PRIMARY),
        ),
    )

    private fun characterRow(characters: String, shifted: Boolean): List<KeySpec> = characters.map { character ->
        val value = if (shifted) character.uppercase() else character.toString()
        KeySpec(value, value, KeyboardAction.Text(value))
    }

    private fun symbolRow(characters: String): List<KeySpec> = characters.map { symbolKey(it.toString()) }

    private fun symbolKey(value: String) = KeySpec(value, value,
        if (value.singleOrNull() in '0'..'9') KeyboardAction.LiteralText(value) else KeyboardAction.Text(value))
}
