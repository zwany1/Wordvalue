package com.yuyan.imemodule.skill

enum class GeneratorIntent {
    REPLY,          // 常规三条回复（AI 生成 / 换一个）
    FAST,           // 急速模式
    RECOVERY,       // 挽回/冷激活
    AUTOPILOT,      // 自动规划对话树
    AUDIT,          // 展示面诊断
    CONSULT         // 顾问分析
}

class IntentDetector(private val recoveryKeywords: List<String>) {

    fun detectFromText(text: String): GeneratorIntent {
        val message = text.trim()
        return when {
            recoveryKeywords.any { it in message } -> GeneratorIntent.RECOVERY
            else -> GeneratorIntent.REPLY
        }
    }

    fun detectFromAction(action: String): GeneratorIntent =
        runCatching { GeneratorIntent.valueOf(action) }.getOrDefault(GeneratorIntent.REPLY)
}
