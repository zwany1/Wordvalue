package com.qingsheng.ime.home

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.qingsheng.ime.R
import com.qingsheng.ime.ai.ApiConfig
import com.qingsheng.ime.data.SettingsStore
import com.qingsheng.ime.skill.ReplyStyle

class SettingsActivity : AppCompatActivity() {

    private lateinit var settings: SettingsStore

    private val imeStatus by lazy { findViewById<TextView>(R.id.ime_status) }
    private val styleButtons by lazy {
        listOf(
            ReplyStyle.NATURAL to findViewById<TextView>(R.id.style_natural),
            ReplyStyle.GENTLE to findViewById<TextView>(R.id.style_gentle),
            ReplyStyle.FUNNY to findViewById<TextView>(R.id.style_funny)
        )
    }

    private val inputMethodManager by lazy {
        getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        settings = SettingsStore(this)

        findViewById<TextView>(R.id.btn_enable_ime).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        findViewById<TextView>(R.id.btn_switch_ime).setOnClickListener {
            inputMethodManager.showInputMethodPicker()
        }
        findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switch_history).apply {
            isChecked = settings.saveHistory
            setOnCheckedChangeListener { _, checked -> settings.saveHistory = checked }
        }
        styleButtons.forEach { (style, button) ->
            button.setOnClickListener {
                settings.defaultStyle = style
                renderStyles()
            }
        }
        bindHiddenApiEntry()
    }

    private fun bindHiddenApiEntry() {
        val target = findViewById<TextView>(R.id.usage_steps_card)
        val taps = mutableListOf<Long>()

        target.setOnClickListener {
            val now = SystemClock.elapsedRealtime()
            taps.removeAll { now - it > UNLOCK_WINDOW_MS }
            taps.add(now)
            if (taps.size >= UNLOCK_TAP_COUNT) {
                taps.clear()
                showApiConfigDialog()
            }
        }
    }

    private fun showApiConfigDialog() {
        val config = ApiConfig(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_api_config, null)

        val baseUrl = view.findViewById<EditText>(R.id.api_base_url)
        val apiKey = view.findViewById<EditText>(R.id.api_key)
        val model = view.findViewById<EditText>(R.id.api_model)
        val enabled = view.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.api_enabled)

        baseUrl.setText(config.baseUrl)
        apiKey.setText(config.apiKey)
        model.setText(config.model)
        enabled.isChecked = config.enabled

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        view.findViewById<TextView>(R.id.btn_api_save).setOnClickListener {
            config.baseUrl = baseUrl.text.toString()
            config.apiKey = apiKey.text.toString()
            config.model = model.text.toString()
            config.enabled = enabled.isChecked
            Toast.makeText(this, R.string.api_saved_toast, Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        refreshImeStatus()
        renderStyles()
    }

    private fun refreshImeStatus() {
        val enabled = enabledImeIds.contains(imeId)
        imeStatus.text = getString(if (enabled) R.string.ime_enabled else R.string.ime_not_enabled)
        imeStatus.setTextColor(getColor(if (enabled) R.color.status_ok else R.color.status_warn))
    }

    private val enabledImeIds: List<String>
        get() = inputMethodManager.enabledInputMethodList.map { it.packageName }

    private val imeId: String get() = packageName

    private fun renderStyles() {
        styleButtons.forEach { (style, button) ->
            button.background = getDrawable(
                if (settings.defaultStyle == style) R.drawable.bg_button_accent else R.drawable.bg_button_soft
            )
            button.setTextColor(getColor(if (settings.defaultStyle == style) R.color.white else R.color.key_text))
        }
    }

    companion object {
        private const val UNLOCK_TAP_COUNT = 7
        private const val UNLOCK_WINDOW_MS = 5000L
    }
}
