package com.example.floating

import kotlin.random.Random

/**
 * 三年级上册语文必背内容（暑期预习）—— 先教后测。
 * 素材：materials/《26秋 三上语文课本必背知识汇总》.pdf（12 首古诗、《司马光》、日积月累、两篇课文段落）。
 * 都是还没学的内容：每项按掌握度出题，≤0 出教学卡（答案就在题面里，第一次是学不是考），
 * >0 出考查题；tip 一律为译文/注释，供「想一想」求助与答后解析用。
 */
object Grade3Recitation {

    private data class Poem(
        val title: String, val dynasty: String, val author: String,
        val lines: List<String>,          // 带标点的句子
        val meaning: String,              // 白话译文（解析）
        val notes: List<Triple<String, String, String>>  // 字词、意思、所在句
    )

    private data class Saying(
        val text: String, val source: String?, val meaning: String,
        val head: String?, val tail: String?   // 补全题拆分（null = 只出含义题）
    )

    private val poems = listOf(
        Poem("所见", "清", "袁枚",
            listOf("牧童骑黄牛，", "歌声振林樾。", "意欲捕鸣蝉，", "忽然闭口立。"),
            "牧童骑在黄牛背上，嘹亮的歌声在树林里回荡。忽然想捉树上鸣叫的知了，就马上停止唱歌，静悄悄地站住了。",
            listOf(Triple("欲", "想要", "意欲捕鸣蝉"), Triple("振", "回荡", "歌声振林樾"))),
        Poem("望洞庭", "唐", "刘禹锡",
            listOf("湖光秋月两相和，", "潭面无风镜未磨。", "遥望洞庭山水翠，", "白银盘里一青螺。"),
            "湖水和月光互相辉映，湖面风平浪静像没磨过的铜镜。远望洞庭山水苍翠，好像白银盘里托着一枚青螺。",
            listOf(Triple("潭面", "湖面", "潭面无风镜未磨"), Triple("和", "和谐", "湖光秋月两相和"))),
        Poem("山行", "唐", "杜牧",
            listOf("远上寒山石径斜，", "白云生处有人家。", "停车坐爱枫林晚，", "霜叶红于二月花。"),
            "沿着弯弯曲曲的小路上山，白云升起的地方还住着人家。停下车是因为喜爱枫林的晚景，经霜的枫叶比二月的春花还红。",
            listOf(Triple("坐", "因为", "停车坐爱枫林晚"), Triple("红于", "比……更红", "霜叶红于二月花"))),
        Poem("夜书所见", "宋", "叶绍翁",
            listOf("萧萧梧叶送寒声，", "江上秋风动客情。", "知有儿童挑促织，", "夜深篱落一灯明。"),
            "秋风吹动梧桐叶送来寒意，江上的秋风让人想念家乡。料想是孩子们在捉蟋蟀，深夜篱笆下还亮着一盏灯。",
            listOf(Triple("促织", "蟋蟀", "知有儿童挑促织"), Triple("篱落", "篱笆", "夜深篱落一灯明"))),
        Poem("舟夜书所见", "清", "查慎行",
            listOf("月黑见渔灯，", "孤光一点萤。", "微微风簇浪，", "散作满河星。"),
            "漆黑的夜里只见渔船的灯光，像萤火虫一样发出一点微亮。微风吹起波浪，灯光散开，好像满河的星星。",
            listOf(Triple("簇", "聚集", "微微风簇浪"), Triple("萤", "萤火虫", "孤光一点萤"))),
        Poem("早发白帝城", "唐", "李白",
            listOf("朝辞白帝彩云间，", "千里江陵一日还。", "两岸猿声啼不住，", "轻舟已过万重山。"),
            "清晨告别彩云间的白帝城，千里外的江陵一天就能到达。两岸的猿声还没停下来，轻快的小船已经驶过万重青山。",
            listOf(Triple("辞", "告别", "朝辞白帝彩云间"), Triple("还", "返回", "千里江陵一日还"))),
        Poem("鹿柴", "唐", "王维",
            listOf("空山不见人，", "但闻人语响。", "返景入深林，", "复照青苔上。"),
            "幽静的山里看不见人，只听到说话的声音。落日的光照进深林，又照在青苔上。",
            listOf(Triple("但", "只", "但闻人语响"), Triple("复", "又", "复照青苔上"))),
        Poem("望天门山", "唐", "李白",
            listOf("天门中断楚江开，", "碧水东流至此回。", "两岸青山相对出，", "孤帆一片日边来。"),
            "长江像巨斧劈开天门山，碧绿的江水东流到这里回旋。两岸青山相对耸立，一只小船从太阳升起的地方驶来。",
            listOf(Triple("楚江", "长江", "天门中断楚江开"), Triple("回", "回旋", "碧水东流至此回"))),
        Poem("饮湖上初晴后雨", "宋", "苏轼",
            listOf("水光潋滟晴方好，", "山色空蒙雨亦奇。", "欲把西湖比西子，", "淡妆浓抹总相宜。"),
            "晴天西湖波光闪动很美，雨天群山迷迷茫茫也很奇妙。要是把西湖比作美女西施，淡妆浓抹都很合适。",
            listOf(Triple("西子", "西施", "欲把西湖比西子"), Triple("潋滟", "波光闪动", "水光潋滟晴方好"))),
        Poem("采莲曲", "唐", "王昌龄",
            listOf("荷叶罗裙一色裁，", "芙蓉向脸两边开。", "乱入池中看不见，", "闻歌始觉有人来。"),
            "采莲少女的绿裙和荷叶像同一个颜色，脸庞映在盛开的荷花中间。混进莲池里分不清了，听到歌声才发觉有人来。",
            listOf(Triple("芙蓉", "荷花", "芙蓉向脸两边开"), Triple("始觉", "才发觉", "闻歌始觉有人来"))),
        Poem("司马光", "宋", "司马光（课文）",
            listOf("群儿戏于庭，", "一儿登瓮，足跌没水中。", "众皆弃去，", "光持石击瓮破之，", "水迸，儿得活。"),
            "一群小孩在庭院里玩，一个小孩爬上大缸，失足掉进水里。别的孩子都跑掉了，只有司马光拿石头砸破缸，水涌出来，小孩得救了。",
            listOf(Triple("皆", "全、都", "众皆弃去"), Triple("庭", "庭院", "群儿戏于庭"), Triple("迸", "涌出", "水迸，儿得活")))
    )

    private val sayings = listOf(
        Saying("不迁怒，不贰过。", "论语", "不把愤怒发泄到别人身上，也不犯同样的错误", "不迁怒", "不贰过"),
        Saying("爱人若爱其身。", "墨子", "爱别人要像爱自己一样", null, null),
        Saying("仁者爱人，有礼者敬人。", "孟子", "仁慈的人关爱别人，有礼貌的人尊敬别人", "仁者爱人", "有礼者敬人"),
        Saying("与人善言，暖于布帛；伤人以言，深于矛戟。", "荀子", "对人说好话比布还温暖，用话伤人比矛刺人还厉害", "与人善言", "暖于布帛"),
        Saying("人心齐，泰山移。", null, "大家一条心，就能发挥出移动泰山的巨大力量", "人心齐", "泰山移"),
        Saying("二人同心，其利断金。", null, "两个人一条心，力量大得能斩断金属，说的是团结合作", "二人同心", "其利断金"),
        Saying("三个臭皮匠，顶个诸葛亮。", null, "人多智慧多，大家一起商量就能想出好办法", "三个臭皮匠", "顶个诸葛亮"),
        Saying("士不可以不弘毅，任重而道远。", "论语", "读书人要胸怀宽广、意志坚定，因为责任重大、路途遥远", "士不可以不弘毅", "任重而道远"),
        Saying("志不强者智不达，言不信者行不果。", "墨子", "意志不坚定，智慧就发挥不出来；说话不守信，做事就没有好结果", "志不强者智不达", "言不信者行不果"),
        Saying("锲而舍之，朽木不折；锲而不舍，金石可镂。", "荀子", "刻一下就放弃，朽木也刻不断；坚持不懈地刻，金石也能雕出花纹", "锲而不舍", "金石可镂")
    )

    // 课文段落：key 后缀、教学卡（节选+问题+答案）、填空题组（题面、答案、干扰项）
    private data class Passage(
        val name: String, val teachText: String, val teachAnswer: String, val teachWrongs: List<String>,
        val blanks: List<Triple<String, String, List<String>>>
    )

    private val passages = listOf(
        Passage("秋天的雨",
            "📜 读一段课文 ——《秋天的雨》\n\n秋天的雨，有一盒五彩缤纷的颜料。它把黄色给了银杏树，黄黄的叶子像一把把小扇子，扇走了夏天的炎热。它把红色给了枫树，红红的枫叶像一枚枚邮票，邮来了秋天的凉爽。田野像金色的海洋。\n\n读一读，问题：这段话写的是哪个季节的雨？",
            "秋天", listOf("春天", "夏天", "冬天"),
            listOf(
                Triple("📜《秋天的雨》：黄黄的叶子像一把把（ ）", "小扇子", listOf("邮票", "小雨伞", "金币")),
                Triple("📜《秋天的雨》：红红的枫叶像一枚枚（ ）", "邮票", listOf("小扇子", "贴纸", "小手掌")),
                Triple("📜《秋天的雨》：田野像金色的（ ）", "海洋", listOf("沙滩", "地毯", "森林"))
            )),
        Passage("大自然的声音",
            "📜 读一段课文 ——《大自然的声音》\n\n风，是大自然的音乐家。他会在森林里演奏他的手风琴。水，也是大自然的音乐家。下雨的时候，他喜欢玩打击乐器，小雨滴敲敲打打，一场热闹的音乐会便开始了。\n\n读一读，问题：课文里说「大自然的音乐家」有谁？",
            "风和水", listOf("鸟和虫", "雷和电", "山和树"),
            listOf(
                Triple("📜《大自然的声音》：风，是大自然的（ ）", "音乐家", listOf("画家", "邮递员", "魔术师")),
                Triple("📜《大自然的声音》：下雨的时候，水喜欢玩（ ）", "打击乐器", listOf("手风琴", "小提琴", "口琴")),
                Triple("📜《大自然的声音》：当微风拂过，那声音轻轻柔柔的，好像（ ）", "呢喃细语", listOf("雷声轰鸣", "锣鼓喧天", "汹涌澎湃"))
            ))
    )

    // 掌握度键：poem-诗名 / saying-前4字 / passage-课文名
    private val allKeys: List<String> by lazy {
        poems.map { "poem-${it.title}" } + sayings.map { "saying-${it.text.take(4)}" } + passages.map { "passage-${it.name}" }
    }

    var lastKey: String = ""

    // 图鉴用：必背内容总数
    val totalItems: Int get() = allKeys.size

    // 掌握度加权：全新 3（缓慢引入）、学习中 5（反复出现）、已掌握 1（偶尔复现）
    fun generateWeighted(mastery: Map<String, Int>): Question {
        val weights = allKeys.map { k ->
            when (mastery[k]) { null -> 3; in 0..2 -> 5; else -> 1 }
        }
        var r = Random.nextInt(weights.sum())
        var idx = 0
        for (w in weights) { r -= w; if (r < 0) break; idx++ }
        if (idx >= allKeys.size) idx = allKeys.size - 1
        lastKey = allKeys[idx]
        val teach = (mastery[lastKey] ?: 0) <= 0   // 第一次（或答错遗忘后）：教学卡
        return when {
            idx < poems.size -> poemQuestion(poems[idx], teach)
            idx < poems.size + sayings.size -> sayingQuestion(sayings[idx - poems.size], teach)
            else -> passageQuestion(passages[idx - poems.size - sayings.size], teach)
        }
    }

    // 供「次数用完屏」教学卡使用：随机一道考查题（带答案展示）
    fun generate(): Question {
        val idx = Random.nextInt(allKeys.size)
        lastKey = allKeys[idx]
        return when {
            idx < poems.size -> poemQuestion(poems[idx], false)
            idx < poems.size + sayings.size -> sayingQuestion(sayings[idx - poems.size], false)
            else -> passageQuestion(passages[idx - poems.size - sayings.size], false)
        }
    }

    private fun clean(line: String) = line.trimEnd('，', '。', '；', '？', '！')

    private fun poemQuestion(p: Poem, teach: Boolean): Question {
        val key = "poem-${p.title}"
        if (teach) {
            val text = "📜 跟我学一首新诗\n\n《${p.title}》 [${p.dynasty}] ${p.author}\n${p.lines.joinToString("\n")}\n\n💬 意思：${p.meaning}\n\n读一读，问题：这首诗的作者是谁？"
            return createQ(text, p.author, otherAuthors(p.author), "答案就在上面的诗里，找一找作者的名字", key)
        }
        return when (Random.nextInt(3)) {
            // 补下句
            0 -> {
                val i = Random.nextInt(p.lines.size - 1)
                val wrongs = poems.filter { it.title != p.title }.flatMap { it.lines }.map { clean(it) }.shuffled().take(3)
                createQ("📜《${p.title}》\n「${clean(p.lines[i])}」的下一句是？", clean(p.lines[i + 1]), wrongs,
                    "回忆这首诗的意思：${p.meaning}", key)
            }
            // 作者
            1 -> createQ("📜《${p.title}》的作者是？", p.author, otherAuthors(p.author),
                "提示：他是 [${p.dynasty}] 代的", key)
            // 字词意思
            else -> {
                val n = p.notes.random()
                val wrongs = poems.flatMap { it.notes }.map { it.second }.filter { it != n.second }.shuffled().take(3)
                createQ("📜《${p.title}》「${n.third}」中「${n.first}」的意思是？", n.second, wrongs,
                    "想想整首诗的意思：${p.meaning}", key)
            }
        }
    }

    private fun sayingQuestion(s: Saying, teach: Boolean): Question {
        val key = "saying-${s.text.take(4)}"
        val src = if (s.source != null) "——《${s.source}》" else ""
        if (teach) {
            val ask = if (s.head != null) "「${s.head}」的下半句是？" else "这句话是什么意思？"
            val correct = if (s.head != null) s.tail!! else s.meaning
            val wrongs = if (s.head != null) sayings.mapNotNull { it.tail }.filter { it != s.tail }.shuffled().take(3)
                else otherMeanings(s.meaning)
            val text = "📜 跟我学一句名言\n\n「${s.text}」$src\n\n💬 意思：${s.meaning}\n\n读一读，问题：$ask"
            return createQ(text, correct, wrongs, "答案就在上面这句名言里", key)
        }
        return if (s.head != null && Random.nextBoolean()) {
            val wrongs = sayings.mapNotNull { it.tail }.filter { it != s.tail }.shuffled().take(3)
            createQ("📜 名句补全：${s.head}，（ ）$src", s.tail!!, wrongs, "意思是：${s.meaning}", key)
        } else {
            createQ("📜「${s.text}」这句话告诉我们什么？", s.meaning, otherMeanings(s.meaning),
                "一个字一个字读：「${s.text}」，把它拆成两半各说什么？", key)
        }
    }

    private fun passageQuestion(p: Passage, teach: Boolean): Question {
        val key = "passage-${p.name}"
        if (teach) return createQ(p.teachText, p.teachAnswer, p.teachWrongs, "答案就在上面的课文里", key)
        val b = p.blanks.random()
        return createQ(b.first, b.second, b.third, "回忆课文《${p.name}》，想想它把这个东西比作了什么", key)
    }

    private fun otherAuthors(correct: String): List<String> =
        (poems.map { it.author } + "李绅").filter { it != correct }.distinct().shuffled().take(3)

    private fun otherMeanings(correct: String): List<String> =
        sayings.map { it.meaning }.filter { it != correct }.shuffled().take(3)

    private fun createQ(text: String, correct: String, wrongs: List<String>, tip: String?, key: String): Question {
        val opts = (wrongs.filter { it != correct }.distinct().take(3) + correct).shuffled()
        return Question(text, opts, opts.indexOf(correct), tip = tip, masteryKey = key)
    }
}
