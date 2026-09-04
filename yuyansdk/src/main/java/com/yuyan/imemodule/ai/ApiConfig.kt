package com.yuyan.imemodule.ai

import android.content.Context

class ApiConfig(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("qingsheng_api", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, "").orEmpty().trim().trimEnd('/')
        set(value) = prefs.edit().putString(KEY_BASE_URL, value.trim().trimEnd('/')).apply()

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_API_KEY, value.trim()).apply()

    var model: String
        get() = prefs.getString(KEY_MODEL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_MODEL, value.trim()).apply()

    val isConfigured: Boolean
        get() = enabled && baseUrl.startsWith("http") && apiKey.isNotEmpty() && model.isNotEmpty()

    companion object {
        private const val KEY_ENABLED = "api_enabled"
        private const val KEY_BASE_URL = "api_base_url"
        private const val KEY_API_KEY = "api_api_key"
        private const val KEY_MODEL = "api_model"
    }
}
