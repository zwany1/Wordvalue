package com.yuyan.imemodule.keyboard.container

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.yuyan.imemodule.R
import com.yuyan.imemodule.ai.GenerationMode
import com.yuyan.imemodule.ai.GenerationOutput
import com.yuyan.imemodule.ai.ReplyGenerator
import com.yuyan.imemodule.data.HistoryStore
import com.yuyan.imemodule.keyboard.InputView
import com.yuyan.imemodule.keyboard.KeyboardManager
import com.yuyan.imemodule.skill.GeneratorIntent
import com.yuyan.imemodule.skill.ReplyStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 言值 AI 回复面板容器
 * 读取剪贴板或手动输入对方消息，云端/规则双引擎生成三条风格回复，一键上屏。
 */
@SuppressLint("ViewConstructor")
class AiContainer(context: Context, inputView: InputView) : BaseContainer(context, inputView) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val generator = ReplyGenerator(context)
    private val history = HistoryStore(context)

    private lateinit var statusView: TextView
    private lateinit var modeTag: TextView
    private lateinit var manualInput: EditText
    private lateinit var cardsContainer: LinearLayout
    private val lastReplies = LinkedHashMap<ReplyStyle, String>()
    private var generating = false
    private var clipboardText: String = ""

    init {
        initView()
    }

    override fun onDetachedFromWindow() {
        scope.cancel()
        super.onDetachedFromWindow()
    }

    private fun initView() {
        val pad = (8 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        addView(root, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT
        ))

        // 标题行
        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(TextView(context).apply {
            text = context.getString(R.string.ai_panel_title)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(activeKeyTextColor())
            setOnLongClickListener {
                showApiConfigDialog()
                true
            }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        modeTag = TextView(context).apply {
            textSize = 11f
            setTextColor(activeKeyTextColor())
            text = if (generator.mode == GenerationMode.API) "云端模式" else "规则模式"
        }
        titleRow.addView(modeTag)
        root.addView(titleRow)

        // 消息输入区
        manualInput = EditText(context).apply {
            hint = context.getString(R.string.ai_manual_hint)
            textSize = 13f
            setHintTextColor(dimTextColor())
            setTextColor(activeKeyTextColor())
            background = null
            maxLines = 2
            setShowSoftInputOnFocus(false)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        root.addView(manualInput, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = pad })

        // 状态行
        statusView = TextView(context).apply {
            text = context.getString(R.string.ai_clipboard_hint)
            textSize = 12f
            setTextColor(dimTextColor())
        }
        statusView.setOnClickListener { refreshClipboard() }
        root.addView(statusView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = pad / 2 })

        // 回复卡片区
        val scroll = ScrollView(context)
        cardsContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(cardsContainer)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ).apply { topMargin = pad })

        // 模式按钮行
        val modeRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        for ((label, intent) in listOf(
            context.getString(R.string.ai_action_recovery) to GeneratorIntent.RECOVERY,
            context.getString(R.string.ai_action_autopilot) to GeneratorIntent.AUTOPILOT,
            context.getString(R.string.ai_action_consult) to GeneratorIntent.CONSULT
        )) {
            modeRow.addView(TextView(context).apply {
                text = label
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(activeKeyTextColor())
                background = context.getDrawable(R.drawable.bg_ai_btn_soft)
                setOnClickListener { generate(intent) }
            }, LinearLayout.LayoutParams(0, dp(38f), 1f).apply {
                marginEnd = dp(4f)
            })
        }
        root.addView(modeRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = pad })

        // 主操作行
        val actionRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        actionRow.addView(TextView(context).apply {
            text = context.getString(R.string.ai_action_refresh)
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(activeKeyTextColor())
            background = context.getDrawable(R.drawable.bg_ai_btn_soft)
            setOnClickListener { generate(GeneratorIntent.FAST) }
        }, LinearLayout.LayoutParams(0, dp(42f), 1f).apply { marginEnd = dp(4f) })
        actionRow.addView(TextView(context).apply {
            text = context.getString(R.string.ai_action_generate)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = context.getDrawable(R.drawable.bg_ai_btn_accent)
            setOnClickListener { generate(GeneratorIntent.REPLY) }
        }, LinearLayout.LayoutParams(0, dp(42f), 1.2f).apply { marginEnd = dp(4f) })
        actionRow.addView(TextView(context).apply {
            text = context.getString(R.string.ai_back_keyboard)
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(activeKeyTextColor())
            background = context.getDrawable(R.drawable.bg_ai_btn_soft)
            setOnClickListener { KeyboardManager.instance.switchKeyboard() }
        }, LinearLayout.LayoutParams(0, dp(42f), 1f))
        root.addView(actionRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = pad })
    }

    fun refreshClipboard() {
        clipboardText = readClipboard().orEmpty()
        if (manualInput.text.isBlank() && clipboardText.isNotEmpty()) {
            statusView.text = clipboardText
        }
    }

    private fun readClipboard(): String? = try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        null
    }

    private fun generate(intent: GeneratorIntent) {
        if (generating) return
        val message = manualInput.text.toString().trim().ifEmpty { clipboardText }
        if (message.isEmpty()) {
            statusView.text = context.getString(R.string.ai_clipboard_hint)
            return
        }
        generating = true
        statusView.text = context.getString(R.string.ai_generating)
        cardsContainer.removeAllViews()
        lastReplies.clear()

        scope.launch {
            val output = try {
                generator.generate(message, intent) {
                    statusView.text = context.getString(R.string.ai_generating_retry)
                }
            } catch (t: Throwable) {
                statusView.text = t.message ?: "生成失败，请重试"
                generating = false
                return@launch
            }
            render(output)
            runCatching {
                val targetId = history.ensureDefaultTarget().id
                history.recordIncoming(targetId, message, output.emotion, output.stage.stageId, output.mode.name)
            }
            generating = false
        }
    }

    private fun render(output: GenerationOutput) {
        val notice = output.notice
            ?: "阶段${output.stage.stageId} ${output.stage.stageName} · 置信度${(output.stage.confidence * 100).toInt()}% · " +
                if (output.mode == GenerationMode.API) "云端" else "规则"
        statusView.text = notice

        val longMode = output.intent != GeneratorIntent.REPLY && output.intent != GeneratorIntent.FAST
        cardsContainer.removeAllViews()

        if (longMode) {
            val text = output.replies.joinToString("\n\n") { it.text }
            cardsContainer.addView(replyCard(
                when (output.intent) {
                    GeneratorIntent.RECOVERY -> "💔 挽回 · 重新联系消息"
                    GeneratorIntent.AUTOPILOT -> "🤖 自动规划 · 对话树"
                    else -> "🧠 顾问分析"
                },
                text, text
            ))
            lastReplies[ReplyStyle.NATURAL] = text
        } else {
            for (style in ReplyStyle.entries) {
                val reply = output.replies.firstOrNull { it.style == style && it.text.isNotBlank() } ?: continue
                cardsContainer.addView(replyCard(styleLabel(style), reply.text, reply.text))
                lastReplies[style] = reply.text
            }
        }
    }

    private fun styleLabel(style: ReplyStyle): String = when (style) {
        ReplyStyle.NATURAL -> context.getString(R.string.ai_style_natural)
        ReplyStyle.GENTLE -> context.getString(R.string.ai_style_gentle)
        ReplyStyle.FUNNY -> context.getString(R.string.ai_style_funny)
    }

    private fun replyCard(label: String, text: String, commitText: String): View {
        val cardPad = (10 * resources.displayMetrics.density).toInt()
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(cardPad, cardPad, cardPad, cardPad)
            background = context.getDrawable(R.drawable.bg_ai_card)
        }
        val textCol = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        textCol.addView(TextView(context).apply {
            this.text = label
            textSize = 12f
            setTextColor(accentColor())
        })
        textCol.addView(TextView(context).apply {
            this.text = text
            textSize = 15f
            setTextColor(activeKeyTextColor())
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = cardPad / 3
        })
        card.addView(textCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(TextView(context).apply {
            this.text = context.getString(R.string.ai_action_use)
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(cardPad, cardPad * 2 / 3, cardPad, cardPad * 2 / 3)
            setTextColor(Color.WHITE)
            background = context.getDrawable(R.drawable.bg_ai_btn_accent)
            setOnClickListener {
                inputView.aiCommitText(commitText)
                statusView.text = context.getString(R.string.ai_inserted)
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginStart = cardPad
        })
        return card
    }

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density).toInt()

    /** 长按标题唤出的云端配置 */
    private fun showApiConfigDialog() {
        val config = com.yuyan.imemodule.ai.ApiConfig(context)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        val baseUrl = EditText(context).apply {
            hint = "https://open.bigmodel.cn/api/paas/v4"
            setText(config.baseUrl)
            textSize = 14f
        }
        val apiKey = EditText(context).apply {
            hint = "API Key"
            setText(config.apiKey)
            textSize = 14f
        }
        val model = EditText(context).apply {
            hint = "glm-4.7-flash"
            setText(config.model)
            textSize = 14f
        }
        val enabled = android.widget.Switch(context).apply {
            text = "启用云端生成"
            isChecked = config.enabled
        }
        val save = TextView(context).apply {
            text = "保存"
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, pad, 0, pad)
        }
        root.addView(baseUrl)
        root.addView(apiKey)
        root.addView(model)
        root.addView(enabled)
        root.addView(save)

        val dialog = android.app.AlertDialog.Builder(context)
            .setTitle("后台生成配置")
            .setView(root)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        save.setOnClickListener {
            config.baseUrl = baseUrl.text.toString().trim()
            config.apiKey = apiKey.text.toString().trim()
            config.model = model.text.toString().trim()
            config.enabled = enabled.isChecked
            modeTag.text = if (config.isConfigured) "云端模式" else "规则模式"
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun activeKeyTextColor(): Int = com.yuyan.imemodule.data.theme.ThemeManager.activeTheme.keyTextColor

    private fun dimTextColor(): Int = runCatching {
        Color.parseColor("#98989F")
    }.getOrDefault(Color.GRAY)

    private fun accentColor(): Int = runCatching {
        Color.parseColor("#7C5CFF")
    }.getOrDefault(Color.MAGENTA)
}
