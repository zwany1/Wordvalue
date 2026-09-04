package com.qingsheng.ime.ai

import com.qingsheng.ime.skill.StrategyContext

class PromptEngine(private val commonAvoid: List<String>, private val principles: List<String>) {

    fun systemPart(): String = buildString {
        appendLine("你是一个自然的中文聊天回复助手。")
        appendLine("目标：帮助用户生成自然、真诚、不油腻的微信回复。")
        principles.forEach { appendLine("- $it") }
        commonAvoid.forEach { appendLine("- 不要$it") }
    }

    fun userPart(message: String, context: StrategyContext): String = buildString {
        appendLine("当前关系阶段：")
        appendLine("阶段${context.stage.id} ${context.stage.name}")
        context.forcedReason?.let { appendLine("（检测到信号：$it）") }
        appendLine()
        appendLine("对方情绪：")
        appendLine(context.emotion.name)
        appendLine()
        appendLine("聊天策略：")
        appendLine(context.emotion.chain)
        appendLine(context.emotion.hint)
        context.emotion.avoid.forEach { appendLine("- 不要$it") }
        appendLine()
        appendLine("要求：")
        appendLine("1. 使用自然的微信口语")
        appendLine("2. 每条15~30字")
        appendLine("3. 不要说教")
        appendLine("4. 不要过度关心")
        appendLine("5. 不要连续提问")
        appendLine("6. 不要虚构用户经历")
        appendLine("7. 不要舔狗式表达")
        appendLine("8. 不要强行推进关系")
        appendLine()
        appendLine("生成三个版本，格式严格如下：")
        appendLine("A：自然风格回复")
        appendLine("B：温柔风格回复")
        appendLine("C：幽默风格回复")
        appendLine()
        appendLine("对方消息：")
        appendLine(message)
    }

    fun build(message: String, context: StrategyContext): String = buildChatML(systemPart(), userPart(message, context))

    private fun buildChatML(system: String, user: String): String = buildString {
        append("<|im_start|>system\n")
        append(system.trimEnd())
        append("<|im_end|>\n")
        append("<|im_start|>user\n")
        append(user.trimEnd())
        append("<|im_end|>\n")
        append("<|im_start|>assistant\n")
    }
}
