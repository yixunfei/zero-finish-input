package dev.zeroinput.ime.ui

enum class EmojiCategory(val marker: String, val description: String) {
    RECENT("◷", "最近使用"),
    SMILEYS("☺", "表情"),
    PEOPLE("☝", "人物"),
    NATURE("♣", "自然"),
    FOOD("●", "食物"),
    ACTIVITY("★", "活动"),
    OBJECTS("◆", "物品"),
    SYMBOLS("♥", "符号"),
}

data class EmojiEntry(
    val value: String,
    val category: EmojiCategory,
    val keywords: String,
)

object EmojiCatalog {
    val entries = listOf(
        entry("😀", EmojiCategory.SMILEYS, "笑 开心 smile happy grin"),
        entry("😃", EmojiCategory.SMILEYS, "开心 笑 face smile"),
        entry("😄", EmojiCategory.SMILEYS, "大笑 开心 laugh happy"),
        entry("😁", EmojiCategory.SMILEYS, "露齿笑 grin"),
        entry("😂", EmojiCategory.SMILEYS, "笑哭 tears joy laugh"),
        entry("🥹", EmojiCategory.SMILEYS, "感动 忍泪 moved"),
        entry("😊", EmojiCategory.SMILEYS, "微笑 害羞 blush smile"),
        entry("🙂", EmojiCategory.SMILEYS, "微笑 smile"),
        entry("🙃", EmojiCategory.SMILEYS, "倒脸 反讽 upside"),
        entry("😉", EmojiCategory.SMILEYS, "眨眼 wink"),
        entry("😍", EmojiCategory.SMILEYS, "喜欢 爱 love heart eyes"),
        entry("🥰", EmojiCategory.SMILEYS, "爱 喜欢 hearts"),
        entry("😘", EmojiCategory.SMILEYS, "亲吻 kiss"),
        entry("😎", EmojiCategory.SMILEYS, "酷 墨镜 cool"),
        entry("🤔", EmojiCategory.SMILEYS, "思考 think"),
        entry("😐", EmojiCategory.SMILEYS, "无语 neutral"),
        entry("😢", EmojiCategory.SMILEYS, "哭 难过 sad cry"),
        entry("😭", EmojiCategory.SMILEYS, "大哭 sad cry"),
        entry("😡", EmojiCategory.SMILEYS, "生气 angry"),
        entry("😴", EmojiCategory.SMILEYS, "睡觉 困 sleep"),
        entry("🤗", EmojiCategory.PEOPLE, "拥抱 hug"),
        entry("🤫", EmojiCategory.PEOPLE, "安静 嘘 quiet"),
        entry("🫡", EmojiCategory.PEOPLE, "敬礼 salute"),
        entry("👍", EmojiCategory.PEOPLE, "赞 同意 thumbs up yes"),
        entry("👎", EmojiCategory.PEOPLE, "踩 不同意 thumbs down no"),
        entry("👌", EmojiCategory.PEOPLE, "好 可以 ok"),
        entry("✌️", EmojiCategory.PEOPLE, "胜利 victory peace"),
        entry("🤞", EmojiCategory.PEOPLE, "好运 祝愿 luck"),
        entry("👏", EmojiCategory.PEOPLE, "鼓掌 applause clap"),
        entry("🙏", EmojiCategory.PEOPLE, "感谢 拜托 thanks pray"),
        entry("💪", EmojiCategory.PEOPLE, "加油 力量 strong"),
        entry("👀", EmojiCategory.PEOPLE, "看 眼睛 eyes look"),
        entry("🌞", EmojiCategory.NATURE, "太阳 晴 sun"),
        entry("🌙", EmojiCategory.NATURE, "月亮 晚安 moon night"),
        entry("⭐", EmojiCategory.NATURE, "星星 star"),
        entry("🌈", EmojiCategory.NATURE, "彩虹 rainbow"),
        entry("🔥", EmojiCategory.NATURE, "火 热 fire hot"),
        entry("❄️", EmojiCategory.NATURE, "雪 冷 snow cold"),
        entry("🌸", EmojiCategory.NATURE, "花 春天 flower"),
        entry("🌱", EmojiCategory.NATURE, "幼苗 生长 plant grow"),
        entry("🍎", EmojiCategory.FOOD, "苹果 apple fruit"),
        entry("🍉", EmojiCategory.FOOD, "西瓜 watermelon fruit"),
        entry("🍓", EmojiCategory.FOOD, "草莓 strawberry"),
        entry("🍜", EmojiCategory.FOOD, "面条 noodles food"),
        entry("🍚", EmojiCategory.FOOD, "米饭 rice food"),
        entry("🍰", EmojiCategory.FOOD, "蛋糕 生日 cake birthday"),
        entry("☕", EmojiCategory.FOOD, "咖啡 coffee drink"),
        entry("🍻", EmojiCategory.FOOD, "干杯 啤酒 cheers beer"),
        entry("⚽", EmojiCategory.ACTIVITY, "足球 football soccer"),
        entry("🏀", EmojiCategory.ACTIVITY, "篮球 basketball"),
        entry("🎮", EmojiCategory.ACTIVITY, "游戏 game"),
        entry("🎵", EmojiCategory.ACTIVITY, "音乐 music"),
        entry("🎉", EmojiCategory.ACTIVITY, "庆祝 party celebrate"),
        entry("🎁", EmojiCategory.ACTIVITY, "礼物 gift"),
        entry("🏆", EmojiCategory.ACTIVITY, "冠军 奖杯 trophy win"),
        entry("🚀", EmojiCategory.ACTIVITY, "火箭 发布 rocket launch"),
        entry("💡", EmojiCategory.OBJECTS, "想法 灯 idea light"),
        entry("📱", EmojiCategory.OBJECTS, "手机 phone"),
        entry("💻", EmojiCategory.OBJECTS, "电脑 computer laptop"),
        entry("⌨️", EmojiCategory.OBJECTS, "键盘 keyboard input"),
        entry("🔒", EmojiCategory.OBJECTS, "锁 安全 lock security"),
        entry("🔑", EmojiCategory.OBJECTS, "钥匙 key"),
        entry("📌", EmojiCategory.OBJECTS, "图钉 pin"),
        entry("📝", EmojiCategory.OBJECTS, "笔记 note write"),
        entry("❤️", EmojiCategory.SYMBOLS, "爱 红心 love heart"),
        entry("🧡", EmojiCategory.SYMBOLS, "橙心 heart"),
        entry("💛", EmojiCategory.SYMBOLS, "黄心 heart"),
        entry("💚", EmojiCategory.SYMBOLS, "绿心 heart"),
        entry("💙", EmojiCategory.SYMBOLS, "蓝心 heart"),
        entry("💜", EmojiCategory.SYMBOLS, "紫心 heart"),
        entry("💯", EmojiCategory.SYMBOLS, "满分 一百 perfect hundred"),
        entry("✅", EmojiCategory.SYMBOLS, "完成 正确 done check"),
        entry("❌", EmojiCategory.SYMBOLS, "错误 取消 error cross"),
        entry("⚠️", EmojiCategory.SYMBOLS, "警告 warning"),
        entry("❓", EmojiCategory.SYMBOLS, "问题 question"),
        entry("‼️", EmojiCategory.SYMBOLS, "注意 感叹 important"),
        entry("✨", EmojiCategory.SYMBOLS, "闪亮 sparkles"),
    )

    fun search(query: String): List<EmojiEntry> {
        val normalized = query.trim().lowercase()
        return if (normalized.isEmpty()) entries else entries.filter { normalized in it.keywords.lowercase() }
    }

    fun recent(values: List<String>): List<EmojiEntry> = values.map { value ->
        entries.firstOrNull { it.value == value }?.copy(category = EmojiCategory.RECENT)
            ?: EmojiEntry(value, EmojiCategory.RECENT, "recent 最近")
    }

    private fun entry(value: String, category: EmojiCategory, keywords: String) =
        EmojiEntry(value, category, keywords)
}

