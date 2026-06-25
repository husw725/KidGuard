package com.example.floating

import org.junit.Assert.*
import org.junit.Test

class EnglishTest {

    @Test
    fun generatesValidQuestions() {
        repeat(2000) {
            val q = EnglishGenerator.generateWeighted(emptyMap())
            assertTrue("选项数 2~4", q.options.size in 2..4)
            assertEquals("选项不重复", q.options.size, q.options.distinct().size)
            assertTrue("correctIndex 合法", q.correctIndex in q.options.indices)
            assertTrue("题干非空", q.text.isNotBlank())
        }
    }

    @Test
    fun newWordShowsTeachCard() {
        // 空 mastery：听力词都是“全新词”，应出“先教”卡（题干含该单词）
        repeat(500) {
            val q = EnglishGenerator.generateWeighted(emptyMap())
            val w = q.audioWord
            if (w != null) assertTrue("新词应出教学卡，题干应含单词: ${q.text}", q.text.contains(w))
        }
    }

    @Test
    fun knownWordHidesAnswer() {
        // 先收集词库里的单词，标为已掌握，再验证听力题不再在题干露出单词
        val mastery = mutableMapOf<String, Int>()
        repeat(400) {
            EnglishGenerator.generate()
            val w = EnglishGenerator.lastWord
            if (w.isNotEmpty()) mastery[w] = 5
        }
        assertTrue("应收集到词库单词", mastery.isNotEmpty())
        repeat(500) {
            val q = EnglishGenerator.generateWeighted(mastery)
            val w = q.audioWord
            if (w != null && mastery.containsKey(w)) {
                assertFalse("已学词不应在题干露出单词: ${q.text}", q.text.contains(w))
            }
        }
    }
}
