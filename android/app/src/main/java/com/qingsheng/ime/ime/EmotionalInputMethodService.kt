package com.qingsheng.ime.ime

import android.inputmethodservice.InputMethodService
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import com.qingsheng.ime.R
import com.qingsheng.ime.ai.GenerationMode
import com.qingsheng.ime.ai.ReplyGenerator
import com.qingsheng.ime.clipboard.ClipboardReader
import com.qingsheng.ime.data.HistoryStore
import com.qingsheng.ime.skill.GeneratorIntent
import com.qingsheng.ime.skill.ReplyStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class EmotionalInputMethodService : InputMethodService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var generator: ReplyGenerator
    private lateinit var clipboard: ClipboardReader
    private lateinit var history: HistoryStore
    private lateinit var keyboardController: KeyboardViewController

    private var aiPanel: View? = null
    private var generating = false
    private var targetId: Long = 0L
    private val lastReplies = mutableMapOf<ReplyStyle, String>()

    override fun onCreate() {
        super.onCreate()
        generator = ReplyGenerator(this)
        clipboard = ClipboardReader(this)
        history = HistoryStore(this)
        serviceScope.launch {
            targetId = history.ensureDefaultTarget().id
        }
    }

    override fun onCreateInputView(): View {
        val root = FrameLayout(this)

        val panel = LayoutInflater.from(this).inflate(R.layout.view_ai_panel, root, false)
        root.addView(panel)
        aiPanel = panel

        keyboardController = KeyboardViewController(this, InputController(this)) { showPanel(true) }
        root.addView(keyboardController.root)
        keyboardController.root.visibility = View.GONE

        bindAiPanel(panel)
        return root
    }

    override fun onStartInputView(editorInfo: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        keyboardController.interceptTarget = null
        showPanel(ai = true)
        keyboardController.clearState()
        refreshClipboardPreview()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        generator.release()
        super.onDestroy()
    }

    private fun showPanel(ai: Boolean) {
        aiPanel?.visibility = if (ai) View.VISIBLE else View.GONE
        keyboardController.root.visibility = if (ai) View.GONE else View.VISIBLE
        if (!ai) keyboardController.clearState()
    }

    private fun bindAiPanel(panel: View) {
        panel.findViewById<TextView>(R.id.ai_btn_generate).setOnClickListener { generate(GeneratorIntent.REPLY) }
        panel.findViewById<TextView>(R.id.ai_btn_refresh).setOnClickListener { generate(GeneratorIntent.FAST) }
        panel.findViewById<TextView>(R.id.ai_btn_keyboard).setOnClickListener {
            keyboardController.interceptTarget = null
            showPanel(ai = false)
        }
        panel.findViewById<TextView>(R.id.ai_clipboard_preview).setOnClickListener { refreshClipboardPreview() }
        panel.findViewById<TextView>(R.id.ai_btn_recovery).setOnClickListener { generate(GeneratorIntent.RECOVERY) }
        panel.findViewById<TextView>(R.id.ai_btn_autopilot).setOnClickListener { generate(GeneratorIntent.AUTOPILOT) }
        panel.findViewById<TextView>(R.id.ai_btn_consult).setOnClickListener { generate(GeneratorIntent.CONSULT) }

        bindManualInput(panel)
        bindUseAction(panel, ReplyStyle.NATURAL, R.id.ai_use_natural)
        bindUseAction(panel, ReplyStyle.GENTLE, R.id.ai_use_gentle)
        bindUseAction(panel, ReplyStyle.FUNNY, R.id.ai_use_funny)

        clearReplyCards()
        refreshModeTag()
    }

    private fun bindManualInput(panel: View) {
        val input = panel.findViewById<EditText>(R.id.ai_manual_input)
        input.setShowSoftInputOnFocus(false)
        input.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                keyboardController.interceptTarget = manualInputTarget
                showPanel(ai = false)
            }
        }
    }

    private val manualInputTarget = object : KeyboardViewController.InterceptTarget {
        override fun commit(text: String) {
            aiPanel?.findViewById<EditText>(R.id.ai_manual_input)?.append(text)
        }

        override fun delete() {
            val input = aiPanel?.findViewById<EditText>(R.id.ai_manual_input) ?: return
            val text = input.text
            if (text.isNotEmpty()) input.text.delete(text.length - 1, text.length)
        }
    }

    private fun bindUseAction(panel: View, style: ReplyStyle, useId: Int) {
        panel.findViewById<TextView>(useId).setOnClickListener {
            lastReplies[style]?.let { text ->
                InputController(this).commit(text)
                history.recordReply(targetId, text, GenerationMode.API.name)
                setStatus(getString(R.string.ai_inserted))
            }
        }
    }

    private fun refreshModeTag() {
        val tag = aiPanel?.findViewById<TextView>(R.id.ai_mode_tag) ?: return
        tag.text = if (generator.mode == GenerationMode.API) "云端模式" else "规则模式"
    }

    private fun refreshClipboardPreview() {
        val preview = aiPanel?.findViewById<TextView>(R.id.ai_clipboard_preview) ?: return
        preview.text = clipboard.read() ?: getString(R.string.ai_clipboard_hint)
    }

    private fun generate(intent: GeneratorIntent) {
        if (generating) return
        val manual = aiPanel?.findViewById<EditText>(R.id.ai_manual_input)?.text?.toString()?.trim().orEmpty()
        val message = manual.ifEmpty { clipboard.read() ?: "" }
        if (message.isEmpty()) {
            setStatus(getString(R.string.ai_clipboard_hint))
            return
        }
        keyboardController.interceptTarget = null
        keyboardController.clearState()
        showPanel(ai = true)
        generating = true
        setStatus(getString(R.string.ai_generating))
        clearReplyCards()

        serviceScope.launch {
            val output = try {
                generator.generate(message, intent) { setStatus(getString(R.string.ai_generating_retry)) }
            } catch (t: Throwable) {
                setStatus(t.message ?: "生成失败，请重试")
                generating = false
                return@launch
            }
            render(output)
            history.recordIncoming(targetId, message, output.emotion, output.stage.stageId, output.mode.name)
            generating = false
        }
    }

    private fun render(output: com.qingsheng.ime.ai.GenerationOutput) {
        val panel = aiPanel ?: return
        val statusBase = "阶段${output.stage.stageId} ${output.stage.stageName} · 置信度${(output.stage.confidence * 100).toInt()}% · ${output.signalSummary} · " +
            if (output.mode == GenerationMode.API) "云端" else "规则"
        setStatus(output.notice ?: statusBase)

        if (output.intent == GeneratorIntent.RECOVERY || output.intent == GeneratorIntent.AUTOPILOT ||
            output.intent == GeneratorIntent.CONSULT || output.intent == GeneratorIntent.AUDIT
        ) {
            renderLongOutput(panel, output)
            return
        }

        val cards = listOf(
            ReplyStyle.NATURAL to (R.id.ai_text_natural to R.id.ai_card_natural),
            ReplyStyle.GENTLE to (R.id.ai_text_gentle to R.id.ai_card_gentle),
            ReplyStyle.FUNNY to (R.id.ai_text_funny to R.id.ai_card_funny)
        )

        var visibleCards = 0
        for ((style, ids) in cards) {
            val (textId, cardId) = ids
            val card = panel.findViewById<View>(cardId) ?: continue
            val reply = output.replies.firstOrNull { it.style == style && it.text.isNotBlank() }
            if (reply == null) {
                card.visibility = View.GONE
            } else {
                card.visibility = View.VISIBLE
                panel.findViewById<TextView>(textId).text = reply.text
                lastReplies[style] = reply.text
                visibleCards++
            }
        }
        if (visibleCards == 0) renderLongOutput(panel, output)
    }

    private fun renderLongOutput(panel: View, output: com.qingsheng.ime.ai.GenerationOutput) {
        val text = output.replies.joinToString("\n\n") { it.text }
        val tagLabel = when (output.intent) {
            GeneratorIntent.RECOVERY -> "💔 挽回 · 重新联系消息"
            GeneratorIntent.AUTOPILOT -> "🤖 自动规划 · 对话树"
            GeneratorIntent.CONSULT -> "🧠 顾问分析"
            else -> "✨ 生成结果"
        }
        val isGentle = output.replies.firstOrNull()?.style == ReplyStyle.GENTLE
        val tagId = if (isGentle) R.id.ai_tag_gentle else R.id.ai_tag_natural
        val cardId = if (isGentle) R.id.ai_card_gentle else R.id.ai_card_natural
        val textId = if (isGentle) R.id.ai_text_gentle else R.id.ai_text_natural

        panel.findViewById<View>(R.id.ai_card_gentle)?.visibility = View.GONE
        panel.findViewById<View>(R.id.ai_card_funny)?.visibility = View.GONE
        panel.findViewById<View>(cardId).visibility = View.VISIBLE
        panel.findViewById<TextView>(tagId).text = tagLabel
        panel.findViewById<TextView>(textId).apply {
            maxLines = Int.MAX_VALUE
            this.text = text
        }
        lastReplies.clear()
        lastReplies[output.replies.firstOrNull()?.style ?: ReplyStyle.NATURAL] = text
    }

    private fun setStatus(text: String) {
        aiPanel?.findViewById<TextView>(R.id.ai_status)?.let {
            it.text = text
            it.visibility = View.VISIBLE
        }
    }

    private fun clearReplyCards() {
        val panel = aiPanel ?: return
        listOf(R.id.ai_card_natural, R.id.ai_card_gentle, R.id.ai_card_funny).forEach {
            panel.findViewById<View>(it)?.visibility = View.GONE
        }
    }
}
