package com.qingsheng.ime.ai

import android.content.Context
import com.qingsheng.ime.data.SettingsStore
import com.qingsheng.ime.skill.ContextManager
import com.qingsheng.ime.skill.EmotionEngine
import com.qingsheng.ime.skill.EmotionResult
import com.qingsheng.ime.skill.Example
import com.qingsheng.ime.skill.GeneratorIntent
import com.qingsheng.ime.skill.IntentDetector
import com.qingsheng.ime.skill.ReplyStyle
import com.qingsheng.ime.skill.SafetyEngine
import com.qingsheng.ime.skill.SignalDetector
import com.qingsheng.ime.skill.SignalReport
import com.qingsheng.ime.skill.StageAssessment
import com.qingsheng.ime.skill.StageDetector
import com.qingsheng.ime.skill.StrategyCard
import com.qingsheng.ime.skill.StrategyLibrary
import com.qingsheng.ime.skill.TargetProfile
import com.qingsheng.ime.skill.TemplateReplyEngine
import com.qingsheng.ime.data.AppDatabase
import com.qingsheng.ime.skill.SkillRepository

enum class GenerationMode { API, RULE_BASED }

data class Reply(val style: ReplyStyle, val text: String)

data class GenerationOutput(
    val replies: List<Reply>,
    val emotion: String,
    val stage: StageAssessment,
    val signalSummary: String,
    val intent: GeneratorIntent,
    val mode: GenerationMode,
    val notice: String? = null
)

class ReplyGenerator(context: Context) {

    private val repo = SkillRepository.get(context)
    private val settings = SettingsStore(context)
    private val apiConfig = ApiConfig(context)
    private val templateEngine = TemplateReplyEngine(repo.templates)
    private val emotionEngine = EmotionEngine(repo.strategies)
    private val stageDetector = StageDetector(repo.stages, repo.signals)
    private val signalDetector = SignalDetector(repo.signals)
    private val intentDetector = IntentDetector(listOf("分手", "挽回", "不回我", "已读不回", "拉黑", "冷了", "不联系"))
    private val safetyEngine = SafetyEngine(repo.signals)
    private val contextManager = ContextManager(repo, AppDatabase.get(context))
    private val strategyLibrary: StrategyLibrary get() = repo.strategyLibrary

    val mode: GenerationMode get() = if (apiConfig.isConfigured) GenerationMode.API else GenerationMode.RULE_BASED

    suspend fun generate(
        message: String,
        intent: GeneratorIntent = GeneratorIntent.REPLY,
        onRetry: ((attempt: Int) -> Unit)? = null
    ): GenerationOutput {
        val text = message.trim()
        require(text.isNotEmpty()) { "消息内容为空" }

        val effectiveIntent = if (intent == GeneratorIntent.REPLY) intentDetector.detectFromText(text) else intent

        val inputCheck = safetyEngine.checkInput(text)
        if (inputCheck is SafetyEngine.InputCheck.Blocked) {
            return GenerationOutput(
                replies = listOf(Reply(settings.defaultStyle, inputCheck.suggestedReply)),
                emotion = "边界信号",
                stage = StageAssessment(repo.stages.defaultStage, repo.stages.stageOf(repo.stages.defaultStage).name, 1f, listOf("对方明确表达了拒绝")),
                signalSummary = "触发安全边界",
                intent = effectiveIntent,
                mode = GenerationMode.RULE_BASED,
                notice = inputCheck.suggestedReply
            )
        }

        val target = contextManager.currentTarget()
        val history = contextManager.recentMessages(target.id, 10)
        val emotionResult = emotionEngine.analyze(text)
        val stage = stageDetector.assess(text, history, target.stage)
        val signal = signalDetector.analyze(text, history)
        val strategies = strategyLibrary.forStage(stage.stageId)
        val examples = retrieveExamples(stage.stageId, emotionResult)

        contextManager.commitStage(target.id, stage.stageId)

        val profile = contextManager.profileOf(target)

        return if (apiConfig.isConfigured) {
            generateByApi(text, effectiveIntent, emotionResult, stage, signal, strategies, examples, profile, onRetry)
        } else {
            generateByTemplate(emotionResult, stage, signal, effectiveIntent)
        }
    }

    private suspend fun generateByApi(
        message: String,
        intent: GeneratorIntent,
        emotion: EmotionResult,
        stage: StageAssessment,
        signal: SignalReport,
        strategies: List<StrategyCard>,
        examples: List<Example>,
        profile: TargetProfile,
        onRetry: ((attempt: Int) -> Unit)? = null
    ): GenerationOutput {
        val system = systemPrompt(intent)
        val user = buildUserPrompt(message, intent, emotion, stage, signal, strategies, examples, profile)

        return try {
            val api = ApiLLM(apiConfig).also { it.onRetry = onRetry }
            val raw = api.generate(system, user)
            val replies = ReplyParser.parse(raw, intent).let { parsed ->
                if (parsed.isEmpty()) throw RuntimeException("云端返回无法解析")
                parsed.entries.sortedBy { it.key.ordinal }.map { Reply(it.key, it.value) }
            }
            GenerationOutput(replies, emotion.emotion, stage, signal.summary, intent, GenerationMode.API)
        } catch (t: RateLimitedException) {
            val fallback = generateByTemplate(emotion, stage, signal, intent)
            fallback.copy(
                notice = "云端限流中，已重试仍 429；本次使用内置策略，稍等十几秒再试",
                mode = GenerationMode.RULE_BASED
            )
        } catch (t: Throwable) {
            val fallback = generateByTemplate(emotion, stage, signal, intent)
            fallback.copy(
                notice = "云端请求失败（${t.message?.take(50)}），已切换内置策略",
                mode = GenerationMode.RULE_BASED
            )
        }
    }

    private fun generateByTemplate(emotion: EmotionResult, stage: StageAssessment, signal: SignalReport, intent: GeneratorIntent): GenerationOutput {
        if (intent != GeneratorIntent.REPLY && intent != GeneratorIntent.FAST) {
            val text = when (intent) {
                GeneratorIntent.RECOVERY -> "上次你说的那个地方我昨天路过了，突然想起你。最近怎么样？"
                GeneratorIntent.AUTOPILOT -> "[发送] 最近发现一家特别有意思的店\n[等她回复]\n她热情 → 5分钟后接话推进\n她敷衍 → 2小时后换轻松话题\n24小时不回 → 48小时后新话题，不提上条"
                GeneratorIntent.CONSULT -> "先接住情绪再谈事实；给对方说话空间；这条先不回，冷半天再发一条轻松的。"
                else -> ""
            }
            if (text.isNotEmpty()) {
                return GenerationOutput(
                    replies = listOf(Reply(ReplyStyle.NATURAL, text)),
                    emotion = emotion.emotion,
                    stage = stage,
                    signalSummary = signal.summary,
                    intent = intent,
                    mode = GenerationMode.RULE_BASED,
                    notice = "云端不可用，已使用内置策略"
                )
            }
        }
        val templates = templateEngine.generate(emotion.emotion)
        val replies = templates.entries
            .filter { it.value.isNotEmpty() }
            .map { Reply(it.key, it.value) }
        return GenerationOutput(
            replies = replies,
            emotion = emotion.emotion,
            stage = stage,
            signalSummary = signal.summary,
            intent = GeneratorIntent.REPLY,
            mode = GenerationMode.RULE_BASED
        )
    }

    private fun systemPrompt(intent: GeneratorIntent): String = buildString {
        appendLine("你是一个自然的中文聊天回复军师，风格像兄弟聊天：直接、真诚、偶尔幽默，绝不油腻。")
        appendLine("铁律：话术必须基于对话中已有的信息，绝不虚构用户的经历。")
        when (intent) {
            GeneratorIntent.REPLY, GeneratorIntent.FAST -> appendLine("输出三条回复，格式严格为：A：自然风格 B：温柔风格 C：幽默风格，每条15~30字。")
            GeneratorIntent.RECOVERY -> appendLine("输出一条可直接发送的重新联系消息（15~40字），格式：R：消息内容")
            GeneratorIntent.AUTOPILOT -> appendLine("输出3轮对话树，第一行必须是[发送]开头的消息，之后按分支给出[等待 X时间]/[发送]序列，纯文本。")
            GeneratorIntent.AUDIT -> appendLine("输出展示面诊断：总分、各维度分、三大问题、具体改法。")
            GeneratorIntent.CONSULT -> appendLine("输出2-3段分析+1条可直接发送的建议消息，不写报告腔。")
        }
    }

    private fun buildUserPrompt(
        message: String,
        intent: GeneratorIntent,
        emotion: EmotionResult,
        stage: StageAssessment,
        signal: SignalReport,
        strategies: List<StrategyCard>,
        examples: List<Example>,
        profile: TargetProfile
    ): String = buildString {
        appendLine("对方档案：")
        appendLine("- 平台：${profile.platform}")
        profile.facts.take(6).forEach { appendLine("- 已知信息：$it") }
        profile.events.take(4).forEach { appendLine("- 时间线：$it") }
        if (profile.facts.isEmpty() && profile.events.isEmpty()) appendLine("- 暂无档案信息，本次为初次分析")
        appendLine()
        appendLine("信号分析：")
        appendLine("- IOI ${signal.ioiCount} 条 / IOD ${signal.iodCount} 条，${signal.trend}")
        appendLine("- 关系阶段：阶段${stage.stageId} ${stage.stageName}（置信度 ${(stage.confidence * 100).toInt()}%）")
        stage.evidence.forEach { appendLine("- 依据：$it") }
        appendLine()
        appendLine("情绪识别：${emotion.emotion}")
        appendLine("策略链：${repo.strategies.emotionOf(emotion.emotion).chain}")
        if (strategies.isNotEmpty()) {
            appendLine("适用策略：")
            strategies.forEach { appendLine("- ${it.name}：${it.principle}") }
        }
        if (examples.isNotEmpty()) {
            appendLine("参考示例（学风格，不照抄）：")
            examples.forEach { appendLine("- 场景[${it.scenario}] 她：${it.her} → 回：${it.reply}") }
        }
        appendLine()
        appendLine("要求：自然微信口语；不说教；不过度关心；不连续提问；不舔狗；不强行推进。")
        appendLine()
        if (intent == GeneratorIntent.RECOVERY) {
            appendLine("场景：重新激活对话。规则：发一条有趣大于有意义、留有好奇心的消息；绝不发在吗/最近怎么样/解释道歉。")
        }
        if (intent == GeneratorIntent.AUTOPILOT) {
            appendLine("场景：规划3轮消息节奏。第一条必须[发送]；她热情→5分钟后接话；敷衍→2小时后换话题；24小时不回→48小时后新话题；同天不超2条；她明确拒绝立即停。")
        }
        appendLine("对方消息：")
        appendLine(message)
    }

    private fun retrieveExamples(stageId: Int, emotion: EmotionResult): List<Example> {
        val byStage = repo.examples.examples.filter { it.stage == stageId }
        val byEmotion = repo.examples.examples.filter { it.scenario.contains(emotion.emotion) }
        return (byStage + byEmotion).distinctBy { it.reply }.take(3)
    }

    fun release() {}
}
