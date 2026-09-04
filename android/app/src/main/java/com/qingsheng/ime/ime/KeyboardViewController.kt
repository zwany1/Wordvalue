package com.qingsheng.ime.ime

import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.qingsheng.ime.R

class KeyboardViewController(
    service: InputMethodService,
    private val input: InputController,
    private val onBackToAi: () -> Unit
) {

    /** 输入目标拦截：非空时键盘动作改发 AI 面板输入框，而非宿主输入框 */
    var interceptTarget: InterceptTarget? = null

    interface InterceptTarget {
        fun commit(text: String)
        fun delete()
    }

    val root: View = LayoutInflater.from(service).inflate(R.layout.view_letters, null)

    private val candidateRow = root.findViewById<LinearLayout>(R.id.candidate_row)
    private val rowsContainer = root.findViewById<LinearLayout>(R.id.rows_container)
    private val modeToggle = root.findViewById<TextView>(R.id.key_mode_toggle)

    private val pinyin = PinyinEngine(service)
    private var pinyinMode = true
    private var composing = StringBuilder()
    private var shiftOn = false
    private var symbolsMode = false
    private val handler = Handler(Looper.getMainLooper())

    private val letterRows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    private val symbolRows = listOf(
        listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '0'),
        listOf('@', '#', '¥', '%', '&', '-', '+', '(', ')', '/'),
        listOf('！', '？', '：', '；', '“', '”', '、', '~', '…', '·')
    )

    init {
        root.findViewById<TextView>(R.id.key_back_to_ai).setOnClickListener { onBackToAi() }
        modeToggle.setOnClickListener { togglePinyin() }
        buildRows()
    }

    fun clearState() {
        composing.clear()
        shiftOn = false
        candidateRow.removeAllViews()
        val ic = input.connection
        ic?.finishComposingText()
        if (symbolsMode) {
            symbolsMode = false
            buildRows()
        }
    }

    private fun togglePinyin() {
        pinyinMode = !pinyinMode
        composing.clear()
        candidateRow.removeAllViews()
        modeToggle.text = if (pinyinMode) "中" else "英"
        buildRows()
    }

    private fun buildRows() {
        rowsContainer.removeAllViews()
        if (symbolsMode) {
            rowsContainer.addView(charRow(symbolRows[0]))
            rowsContainer.addView(charRow(symbolRows[1]))
            rowsContainer.addView(charRow(symbolRows[2]))
        } else {
            rowsContainer.addView(charRow(letterRows[0].map { it }))
            rowsContainer.addView(centeredRow(letterRows[1].map { it }))
            rowsContainer.addView(sideRow())
        }
        rowsContainer.addView(bottomRow())
    }

    private fun charRow(chars: List<Char>): LinearLayout {
        val row = newRow()
        chars.forEach { c -> row.addKey(displayOf(c), weight = 1f) { onChar(c) } }
        return row
    }

    private fun centeredRow(chars: List<Char>): LinearLayout {
        val row = newRow()
        row.addSpacer(24)
        chars.forEach { c -> row.addKey(displayOf(c), weight = 1f) { onChar(c) } }
        row.addSpacer(24)
        return row
    }

    private fun sideRow(): LinearLayout {
        val row = newRow()
        row.addKey("⇧", weight = 1.4f, special = true, highlighted = shiftOn) { onShift() }
        letterRows[2].forEach { c -> row.addKey(displayOf(c), weight = 1f) { onChar(c) } }
        row.addKey("⌫", weight = 1.4f, special = true).also { bindDelete(it) }
        return row
    }

    private fun bottomRow(): LinearLayout {
        val row = newRow()
        row.addKey(if (symbolsMode) "ABC" else "？123", weight = 1.4f, special = true) { toggleSymbols() }
        row.addKey(if (symbolsMode) "、" else "，", weight = 1.2f) { commitText(if (symbolsMode) "、" else "，") }
        row.addKey(if (pinyinMode) "空格" else "space", weight = 3.6f, special = true) { onSpace() }
        row.addKey("。", weight = 1.2f) { commitText("。") }
        row.addKey("↵", weight = 1.4f, special = true) { onEnter() }
        return row
    }

    private fun commitText(text: String) {
        val target = interceptTarget
        if (target != null) target.commit(text) else input.commit(text)
    }

    private fun deleteChar(): Boolean {
        val target = interceptTarget
        if (target != null) {
            target.delete()
            return true
        }
        return false
    }

    private fun sendEnter() {
        if (interceptTarget == null) input.sendEnter()
    }

    private fun newRow(): LinearLayout = LinearLayout(root.context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(0, 2, 0, 2)
    }

    private fun displayOf(c: Char): String =
        if (shiftOn && !pinyinMode && !symbolsMode) c.uppercaseChar().toString() else c.toString()

    private fun onChar(c: Char) {
        if (pinyinMode) {
            composing.append(c.lowercaseChar())
            refreshComposing()
        } else {
            commitText(displayOf(c))
            if (shiftOn) {
                shiftOn = false
                buildRows()
            }
        }
    }

    private fun onShift() {
        shiftOn = !shiftOn
        buildRows()
    }

    private fun onSpace() {
        if (pinyinMode && composing.isNotEmpty()) {
            val first = pinyin.candidates(composing.toString()).firstOrNull()
            pickCandidate(first ?: composing.toString())
        } else {
            commitText(" ")
        }
    }

    private fun onEnter() {
        if (pinyinMode && composing.isNotEmpty()) {
            pickCandidate(composing.toString())
            return
        }
        sendEnter()
    }

    private fun toggleSymbols() {
        symbolsMode = !symbolsMode
        buildRows()
    }

    private fun bindDelete(key: TextView) {
        var repeating = false
        key.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    repeating = false
                    v.isPressed = true
                    handler.postDelayed({
                        repeating = true
                        repeatDelete()
                    }, 380)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacksAndMessages(null)
                    v.isPressed = false
                    if (!repeating) onDeleteSingle()
                }
            }
            true
        }
    }

    private fun repeatDelete() {
        onDeleteSingle()
        handler.postDelayed({ repeatDelete() }, 60)
    }

    private fun onDeleteSingle() {
        if (pinyinMode && interceptTarget == null && composing.isNotEmpty()) {
            composing.deleteCharAt(composing.length - 1)
            refreshComposing()
        } else if (!deleteChar()) {
            input.deleteBackward()
        }
    }

    private fun refreshComposing() {
        val text = composing.toString()
        if (text.isEmpty()) {
            if (interceptTarget == null) input.connection?.finishComposingText()
            candidateRow.removeAllViews()
            return
        }
        if (interceptTarget == null) {
            input.connection?.setComposingText(text, 1)
        }
        renderCandidates(pinyin.candidates(text))
    }

    private fun renderCandidates(candidates: List<String>) {
        candidateRow.removeAllViews()
        candidateRow.addCandidate("[${composing}]", composingColor) { pickCandidate(composing.toString()) }
        candidates.forEach { word ->
            candidateRow.addCandidate(word, candidateColor) { pickCandidate(word) }
        }
    }

    private fun pickCandidate(word: String) {
        val target = interceptTarget
        if (target != null) {
            target.commit(word)
        } else {
            val ic = input.connection ?: return
            ic.setComposingText(word, 1)
            ic.finishComposingText()
        }
        composing.clear()
        candidateRow.removeAllViews()
    }

    private fun LinearLayout.addKey(
        text: String,
        weight: Float,
        special: Boolean = false,
        highlighted: Boolean = false,
        onClick: () -> Unit = {}
    ): TextView {
        val dp = resources.displayMetrics.density
        val key = TextView(context).apply {
            this.text = text
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor(KEY_TEXT))
            background = context.getDrawable(
                when {
                    highlighted -> R.drawable.bg_button_accent
                    special -> R.drawable.bg_key_special
                    else -> R.drawable.bg_key
                }
            )
            setOnClickListener { onClick() }
        }
        val lp = LinearLayout.LayoutParams(0, (44 * dp).toInt(), weight)
        lp.setMargins((3 * dp).toInt(), 0, (3 * dp).toInt(), 0)
        key.layoutParams = lp
        addView(key)
        return key
    }

    private fun LinearLayout.addSpacer(widthDp: Int): View {
        val dp = resources.displayMetrics.density
        val v = View(context)
        v.layoutParams = LinearLayout.LayoutParams((widthDp * dp).toInt(), (44 * dp).toInt())
        addView(v)
        return v
    }

    private fun LinearLayout.addCandidate(text: String, color: String, onClick: () -> Unit): TextView {
        val dp = resources.displayMetrics.density
        val chip = TextView(context).apply {
            this.text = text
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor(color))
            minWidth = (30 * dp).toInt()
            setPadding((10 * dp).toInt(), 0, (10 * dp).toInt(), 0)
            setOnClickListener { onClick() }
        }
        addView(chip, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ))
        return chip
    }

    private companion object {
        const val KEY_TEXT = "#F2F2F7"
        const val composingColor = "#98989F"
        const val candidateColor = "#F2F2F7"
    }
}
