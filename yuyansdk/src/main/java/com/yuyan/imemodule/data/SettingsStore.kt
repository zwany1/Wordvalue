package com.yuyan.imemodule.data

import android.content.Context
import com.yuyan.imemodule.skill.ReplyStyle

class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("qingsheng_settings", Context.MODE_PRIVATE)

    var defaultStyle: ReplyStyle
        get() = runCatching { ReplyStyle.valueOf(prefs.getString(KEY_STYLE, ReplyStyle.NATURAL.name)!!) }
            .getOrDefault(ReplyStyle.NATURAL)
        set(value) = prefs.edit().putString(KEY_STYLE, value.name).apply()

    var maxReplyLength: Int
        get() = prefs.getInt(KEY_MAX_LEN, 30)
        set(value) = prefs.edit().putInt(KEY_MAX_LEN, value.coerceIn(15, 60)).apply()

    var replyCount: Int
        get() = prefs.getInt(KEY_REPLY_COUNT, 3)
        set(value) = prefs.edit().putInt(KEY_REPLY_COUNT, value.coerceIn(2, 3)).apply()

    var saveHistory: Boolean
        get() = prefs.getBoolean(KEY_SAVE_HISTORY, true)
        set(value) = prefs.edit().putBoolean(KEY_SAVE_HISTORY, value).apply()

    companion object {
        private const val KEY_STYLE = "default_style"
        private const val KEY_MAX_LEN = "max_reply_length"
        private const val KEY_REPLY_COUNT = "reply_count"
        private const val KEY_SAVE_HISTORY = "save_history"
    }
}
