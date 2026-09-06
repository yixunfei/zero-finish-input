package dev.zeroinput.ime.ui

internal object AdditionalEmoji {
    val entries: List<EmojiEntry> = buildList {
        addAll(smileys())
        addAll(people())
        addAll(nature())
        addAll(food())
        addAll(activity())
        addAll(travel())
        addAll(objects())
        addAll(symbols())
    }

    private fun smileys() = listOf(
        e("🤣", "笑翻 笑哭 laugh rofl xiaofan xiaoku"),
        e("😆", "大笑 happy laugh daxiao kaixin"),
        e("😅", "汗笑 尴尬 sweat laugh gan ga ganga"),
        e("😇", "天使 angel tianshi"),
        e("🤩", "崇拜 星眼 star struck chongbai"),
        e("😋", "美味 yum delicious meiwei"),
        e("😛", "吐舌 tongue tushe"),
        e("😜", "调皮 wink tongue tiaopi"),
        e("🤪", "疯狂 zany crazy fengkuang"),
        e("😝", "鬼脸 tongue guilian"),
        e("🤑", "发财 money facai"),
        e("🤭", "捂嘴 偷笑 giggle wuzui touxiao"),
        e("🤨", "怀疑 skeptical huaiyi"),
        e("🧐", "研究 monocle curious yanjiu"),
        e("🤓", "学霸 nerd xueba"),
        e("😏", "得意 smirk deyi"),
        e("😒", "不满 unamused buman"),
        e("🙄", "白眼 eye roll baiyan"),
        e("😬", "咬牙 grimace yaoya"),
        e("🤥", "说谎 lie shuohuang"),
        e("😌", "轻松 relief qingsong"),
        e("😔", "失落 sad shiluo"),
        e("😪", "困倦 sleepy kunjuan"),
        e("🤤", "流口水 drool liukoushui"),
        e("😷", "口罩 mask kouzhao"),
        e("🤒", "发烧 fever fashao"),
        e("🤕", "受伤 hurt shoushang"),
        e("🤢", "恶心 nauseous exin"),
        e("🤮", "呕吐 vomit outu"),
        e("🤧", "喷嚏 sneeze penti"),
        e("🥵", "炎热 hot yanre"),
        e("🥶", "寒冷 cold hanleng"),
        e("🥴", "头晕 woozy touyun"),
        e("😵", "眩晕 dizzy xuanyun"),
        e("🤯", "震惊 mind blown zhenjing"),
        e("😕", "疑惑 confused yihuo"),
        e("😟", "担心 worried danxin"),
        e("🙁", "不开心 frown bukaixin"),
        e("😮", "惊讶 surprise jingya"),
        e("😯", "吃惊 hush chijing"),
        e("😲", "震惊 astonished zhenjing"),
        e("😳", "脸红 flushed lianhong"),
        e("🥺", "拜托 pleading baituo"),
        e("😨", "害怕 afraid haipa"),
        e("😰", "焦虑 anxious jiaolv"),
        e("😥", "失望 disappointed shiwang"),
        e("😱", "尖叫 scream jianjiao"),
        e("😖", "纠结 confounded jiujie"),
        e("😣", "忍耐 persevere rennai"),
        e("😞", "沮丧 sad jusang"),
        e("😩", "疲惫 weary pibei"),
        e("😫", "累 tired lei"),
        e("🥱", "哈欠 yawn haqian"),
        e("😤", "哼 triumph heng"),
        e("😠", "愤怒 angry fennu"),
        e("🤬", "骂人 swearing maren"),
        e("😈", "恶魔 devil emo"),
        e("💀", "骷髅 skull kulou"),
        e("👻", "幽灵 ghost youling"),
        e("🤖", "机器人 robot jiqiren"),
    )

    private fun people() = listOf(
        p("👋", "挥手 你好 wave hello huishou nihao"), p("🤚", "手背 hand shoubei"),
        p("🖐️", "五指 hand wuzhi"), p("✋", "举手 stop hand jushou"),
        p("🤟", "爱你 love you aini"), p("🤘", "摇滚 rock yaogun"),
        p("🤙", "电话 call dianhua"), p("👈", "向左 left xiangzuo"),
        p("👉", "向右 right xiangyou"), p("👆", "向上 up xiangshang"),
        p("👇", "向下 down xiangxia"), p("☝️", "注意 point zhuyi"),
        p("✊", "拳头 fist quantou"), p("👊", "击拳 fist bump jiquan"),
        p("🤛", "左拳 left fist zuoquan"), p("🤜", "右拳 right fist youquan"),
        p("🙌", "欢呼 raised hands huanhu"), p("👐", "张手 open hands zhangshou"),
        p("🤲", "捧手 palms pengshou"), p("🤝", "握手 handshake woshou"),
        p("✍️", "写字 writing xiezi"), p("💅", "美甲 nail meijia"),
        p("🦾", "机械臂 prosthetic arm jixiebi"), p("🦿", "机械腿 prosthetic leg jixietui"),
        p("🦵", "腿 leg tui"), p("🦶", "脚 foot jiao"),
        p("👂", "耳朵 ear erduo"), p("👃", "鼻子 nose bizi"),
        p("🧠", "大脑 brain danao"), p("👶", "宝宝 baby baobao"),
        p("🧒", "孩子 child haizi"), p("👩", "女人 woman nvren"),
        p("👨", "男人 man nanren"), p("🧓", "老人 elder laoren"),
        p("🙋", "举手 raise hand jushou"), p("🙆", "同意 ok tongyi"),
        p("🙅", "拒绝 no jujue"), p("🤦", "捂脸 facepalm wulian"),
        p("🤷", "摊手 shrug tanshou"), p("🙇", "鞠躬 bow jugong"),
    )

    private fun nature() = listOf(
        n("🐶", "狗 dog gou"), n("🐱", "猫 cat mao"), n("🐭", "老鼠 mouse laoshu"),
        n("🐰", "兔子 rabbit tuzi"), n("🦊", "狐狸 fox huli"), n("🐻", "熊 bear xiong"),
        n("🐼", "熊猫 panda xiongmao"), n("🐨", "考拉 koala kaola"), n("🐯", "老虎 tiger laohu"),
        n("🦁", "狮子 lion shizi"), n("🐮", "牛 cow niu"), n("🐷", "猪 pig zhu"),
        n("🐸", "青蛙 frog qingwa"), n("🐵", "猴子 monkey houzi"), n("🐧", "企鹅 penguin qie"),
        n("🐦", "鸟 bird niao"), n("🦆", "鸭 duck ya"), n("🦉", "猫头鹰 owl maotouying"),
        n("🦋", "蝴蝶 butterfly hudie"), n("🐝", "蜜蜂 bee mifeng"), n("🐢", "乌龟 turtle wugui"),
        n("🐬", "海豚 dolphin haitun"), n("🐳", "鲸鱼 whale jingyu"), n("🐟", "鱼 fish yu"),
        n("🌻", "向日葵 sunflower xiangrikui"), n("🌹", "玫瑰 rose meigui"), n("🌷", "郁金香 tulip yujinxiang"),
        n("🌲", "松树 evergreen songshu"), n("🌳", "树 tree shu"), n("🍀", "幸运 luck clover xingyun"),
        n("🍁", "枫叶 maple fengye"), n("🍂", "落叶 autumn luoye"), n("☀️", "晴天 sun qingtian"),
        n("☁️", "云 cloud yun"), n("⛈️", "雷雨 thunder leiyu"), n("🌧️", "下雨 rain xiayu"),
        n("⛄", "雪人 snowman xueren"), n("💧", "水滴 water shuidi"), n("🌊", "波浪 wave bolang"),
        n("🌍", "地球 earth diqiu"),
    )

    private fun food() = listOf(
        f("🍊", "橘子 orange juzi"), f("🍋", "柠檬 lemon ningmeng"), f("🍌", "香蕉 banana xiangjiao"),
        f("🍇", "葡萄 grape putao"), f("🍒", "樱桃 cherry yingtao"), f("🍑", "桃子 peach taozi"),
        f("🥭", "芒果 mango mangguo"), f("🍍", "菠萝 pineapple boluo"), f("🥝", "猕猴桃 kiwi mihoutao"),
        f("🥑", "牛油果 avocado niuyouguo"), f("🍅", "番茄 tomato fanqie"), f("🌽", "玉米 corn yumi"),
        f("🥕", "胡萝卜 carrot huluobo"), f("🥦", "西兰花 broccoli xilanhua"), f("🍞", "面包 bread mianbao"),
        f("🥐", "可颂 croissant kesong"), f("🥚", "鸡蛋 egg jidan"), f("🥞", "煎饼 pancake jianbing"),
        f("🍔", "汉堡 burger hanbao"), f("🍟", "薯条 fries shutiao"), f("🍕", "披萨 pizza pisa"),
        f("🌭", "热狗 hotdog regou"), f("🥪", "三明治 sandwich sanmingzhi"), f("🥗", "沙拉 salad shala"),
        f("🍲", "火锅 hotpot huoguo"), f("🥟", "饺子 dumpling jiaozi"), f("🍣", "寿司 sushi shousi"),
        f("🍤", "炸虾 shrimp zhaxia"), f("🍦", "冰淇淋 ice cream bingqilin"), f("🍩", "甜甜圈 donut tiantianquan"),
        f("🍪", "饼干 cookie binggan"), f("🎂", "生日蛋糕 birthday cake shengri dangao"), f("🍫", "巧克力 chocolate qiaokeli"),
        f("🍬", "糖果 candy tangguo"), f("🍵", "茶 tea cha"), f("🧋", "奶茶 bubble tea naicha"),
        f("🧃", "果汁 juice guozhi"), f("🥛", "牛奶 milk niunai"), f("🍷", "红酒 wine hongjiu"),
        f("🥂", "庆祝 干杯 toast cheers qingzhu ganbei"),
    )

    private fun activity() = listOf(
        a("🏈", "橄榄球 football ganlanqiu"), a("⚾", "棒球 baseball bangqiu"), a("🎾", "网球 tennis wangqiu"),
        a("🏐", "排球 volleyball paiqiu"), a("🏓", "乒乓球 ping pong pingpangqiu"), a("🏸", "羽毛球 badminton yumaoqiu"),
        a("🥊", "拳击 boxing quanji"), a("🎯", "目标 target mubiao"), a("🎲", "骰子 dice touzi"),
        a("🧩", "拼图 puzzle pintu"), a("♟️", "国际象棋 chess xiangqi"), a("🎭", "戏剧 theater xiju"),
        a("🎨", "绘画 art painting huihua"), a("🎤", "唱歌 microphone changge"), a("🎧", "耳机 headphones erji"),
        a("🎸", "吉他 guitar jita"), a("🎹", "钢琴 piano gangqin"), a("🎻", "小提琴 violin xiaotiqin"),
        a("🎬", "电影 movie dianying"), a("🎫", "门票 ticket menpiao"), a("🎈", "气球 balloon qiqiu"),
        a("🎊", "彩纸 庆祝 confetti celebrate caizhi"), a("🎆", "烟花 fireworks yanhua"), a("🧧", "红包 red envelope hongbao"),
    )

    private fun travel() = listOf(
        t("🚗", "汽车 car qiche"), t("🚕", "出租车 taxi chuzuche"), t("🚌", "公交 bus gongjiao"),
        t("🚎", "电车 trolley dianche"), t("🏎️", "赛车 race car saiche"), t("🚓", "警车 police jingche"),
        t("🚑", "救护车 ambulance jiuhuche"), t("🚒", "消防车 fire engine xiaofangche"), t("🚚", "货车 truck huoche"),
        t("🚲", "自行车 bicycle zixingche"), t("🛵", "摩托 scooter motuo"), t("🚆", "火车 train huoche"),
        t("🚄", "高铁 train gaotie"), t("🚇", "地铁 subway ditie"), t("✈️", "飞机 plane feiji"),
        t("🚁", "直升机 helicopter zhishengji"), t("🚢", "轮船 ship lunchuan"), t("⛵", "帆船 sailboat fanchuan"),
        t("🏠", "家 home jia"), t("🏢", "办公楼 office bangonglou"), t("🏫", "学校 school xuexiao"),
        t("🏥", "医院 hospital yiyuan"), t("🏖️", "海滩 beach haitan"), t("🏕️", "露营 camping luying"),
        t("⛰️", "山 mountain shan"), t("🗺️", "地图 map ditu"), t("🧳", "行李 luggage xingli"),
        t("⛽", "加油站 fuel jiayouzhan"), t("🚦", "红绿灯 traffic honglvdeng"), t("🌃", "夜景 night yejing"),
    )

    private fun objects() = listOf(
        o("⌚", "手表 watch shoubiao"), o("📷", "相机 camera xiangji"), o("📺", "电视 television dianshi"),
        o("📻", "收音机 radio shouyinji"), o("🔋", "电池 battery dianchi"), o("🔌", "插头 plug chatou"),
        o("🕯️", "蜡烛 candle lazhu"), o("📚", "书 books shu"), o("📖", "阅读 book reading yuedu"),
        o("✏️", "铅笔 pencil qianbi"), o("🖊️", "钢笔 pen gangbi"), o("📎", "回形针 paperclip huixingzhen"),
        o("✂️", "剪刀 scissors jiandao"), o("📅", "日历 calendar rili"), o("📦", "包裹 package baoguo"),
        o("✉️", "信件 letter xinjian"), o("📮", "邮箱 postbox youxiang"), o("⏰", "闹钟 alarm naozhong"),
        o("⏳", "沙漏 hourglass shalou"), o("🔍", "搜索 search sousuo"), o("🛠️", "工具 tools gongju"),
        o("⚙️", "设置 settings shezhi"), o("🧲", "磁铁 magnet citie"), o("💊", "药 pill yao"),
        o("🧸", "玩具 teddy toy wanju"), o("🧹", "扫帚 broom saozhou"), o("🧼", "肥皂 soap feizao"),
        o("☂️", "雨伞 umbrella yusan"), o("🛒", "购物 shopping gouwu"), o("💰", "钱 money qian"),
    )

    private fun symbols() = listOf(
        s("🖤", "黑心 black heart heixin"), s("🤍", "白心 white heart baixin"), s("🤎", "棕心 brown heart zongxin"),
        s("💔", "心碎 broken heart xinsui"), s("💕", "双心 two hearts shuangxin"), s("💞", "爱心 revolving hearts aixin"),
        s("💓", "心跳 beating heart xintiao"), s("💗", "心动 growing heart xindong"), s("💖", "闪亮心 sparkling heart shanliang"),
        s("💘", "丘比特 cupid qiubite"), s("💝", "礼物心 heart gift liwu"), s("💟", "心形 heart xin"),
        s("☮️", "和平 peace heping"), s("☯️", "阴阳 yin yang yinyang"), s("♻️", "回收 recycling huishou"),
        s("🔔", "铃铛 bell lingdang"), s("🔕", "静音 mute jingyin"), s("💤", "睡眠 sleep shuimian"),
        s("💢", "生气 anger shengqi"), s("💥", "爆炸 boom baozha"), s("💫", "眩晕 dizzy xuanyun"),
        s("💬", "聊天 speech liaotian"), s("💭", "思考 thought sikao"), s("✔️", "对勾 check duigou"),
        s("➕", "加 plus jia"), s("➖", "减 minus jian"), s("➗", "除 divide chu"),
        s("∞", "无限 infinity wuxian"), s("©️", "版权 copyright banquan"), s("®️", "注册 registered zhuce"),
    )

    private fun e(value: String, words: String) = EmojiEntry(value, EmojiCategory.SMILEYS, words)
    private fun p(value: String, words: String) = EmojiEntry(value, EmojiCategory.PEOPLE, words)
    private fun n(value: String, words: String) = EmojiEntry(value, EmojiCategory.NATURE, words)
    private fun f(value: String, words: String) = EmojiEntry(value, EmojiCategory.FOOD, words)
    private fun a(value: String, words: String) = EmojiEntry(value, EmojiCategory.ACTIVITY, words)
    private fun t(value: String, words: String) = EmojiEntry(value, EmojiCategory.TRAVEL, words)
    private fun o(value: String, words: String) = EmojiEntry(value, EmojiCategory.OBJECTS, words)
    private fun s(value: String, words: String) = EmojiEntry(value, EmojiCategory.SYMBOLS, words)
}
