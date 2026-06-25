package com.example.floating

import com.example.floating.FeishuClient.Command
import org.junit.Assert.*
import org.junit.Test

class FeishuCommandTest {

    @Test
    fun parsesUnlock() {
        assertEquals(Command.UnlockNow(30), FeishuClient.parseCommand("解锁30"))
        assertEquals(Command.UnlockNow(30), FeishuClient.parseCommand("解锁"))          // 默认 30
        assertEquals(Command.UnlockNow(15), FeishuClient.parseCommand("@_user_1 解锁15")) // 去掉@提及
        assertEquals(Command.UnlockNow(240), FeishuClient.parseCommand("解锁9999"))      // 上限钳制
    }

    @Test
    fun parsesLock() {
        assertEquals(Command.LockNow, FeishuClient.parseCommand("锁定"))
        assertEquals(Command.LockNow, FeishuClient.parseCommand("马上锁"))
    }

    @Test
    fun parsesPause() {
        assertEquals(Command.PauseLock(60), FeishuClient.parseCommand("停用60"))
        assertEquals(Command.PauseLock(60), FeishuClient.parseCommand("暂停"))
        assertEquals(Command.PauseLock(60), FeishuClient.parseCommand("今晚不锁"))
    }

    @Test
    fun fallsBackToMessage() {
        assertEquals(Command.MessageToChild("宝贝加油"), FeishuClient.parseCommand("宝贝加油"))
        assertEquals(Command.MessageToChild("做完去吃饭啦"), FeishuClient.parseCommand("@_user_1 做完去吃饭啦"))
    }
}
