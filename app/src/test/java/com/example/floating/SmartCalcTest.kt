package com.example.floating

import org.junit.Assert.*
import org.junit.Test

class SmartCalcTest {

    @Test
    fun testSmartCalcGeneratesValidQuestions() {
        // 跑多次，确保不会因随机数边界崩溃，且每题格式合法
        repeat(2000) {
            val q = SmartCalcGenerator.generate()
            assertTrue("选项数应为 2~4", q.options.size in 2..4)
            assertEquals("选项不应重复", q.options.size, q.options.distinct().size)
            assertTrue("correctIndex 应合法", q.correctIndex in q.options.indices)
            assertTrue("题干非空", q.text.isNotBlank())
        }
    }

    @Test
    fun testWeightedAlsoValid() {
        val seen = mutableMapOf<String, Int>()
        val errors = mutableMapOf<String, Int>()
        repeat(1000) {
            val q = SmartCalcGenerator.generateWeighted(seen, errors)
            assertTrue(q.options.size in 2..4)
            assertEquals(q.options.size, q.options.distinct().size)
            assertTrue(q.correctIndex in q.options.indices)
            // 模拟轮次推进
            if (SmartCalcGenerator.lastGeneratedType.isNotEmpty()) seen[SmartCalcGenerator.lastGeneratedType] = it
        }
    }
}
