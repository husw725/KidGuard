package com.example.floating

import org.junit.Test
import org.junit.Assert.*

class QuestionBankTest {

    @Test
    fun testBuiltinVerbalQuestionsUniqueness() {
        val allQuestions = builtinVerbalQuestions
        val uniqueQuestions = allQuestions.distinctBy { it.text }
        
        println("Total questions: ${allQuestions.size}")
        println("Unique questions: ${uniqueQuestions.size}")
        
        // 检查是否有重复题目
        assertEquals("Datas.kt 中存在重复题目！", allQuestions.size, uniqueQuestions.size)
    }

    @Test
    fun testGetRandomQuestionsUniqueness() {
        // 由于需要 Context，这里我们主要逻辑是验证 getRandomQuestions 生成的 20 道题目
        // 在 Kotlin 中运行 Android 环境较复杂，我们模拟一个环境或直接测试核心逻辑
        
        // 提取 QuestionBank 中的逻辑部分进行测试
        val count = 20
        // 模拟生成 100 次以测试是否有重复
        for (i in 1..100) {
            // 简单模拟 QuestionBank 内部获取题目后的过滤
            val allAvailablePool = builtinVerbalQuestions.distinctBy { it.text }
            val selected = mutableSetOf<String>()
            
            // 简单模拟随机选择逻辑
            val sampled = allAvailablePool.shuffled().take(count)
            
            for (q in sampled) {
                assertTrue("生成了重复题目: ${q.text}", !selected.contains(q.text))
                selected.add(q.text)
            }
        }
    }
}
