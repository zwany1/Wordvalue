package com.qingsheng.ime.clipboard

import android.content.Context
import android.inputmethodservice.InputMethodService

class ClipboardReader(private val service: InputMethodService) {

    fun read(): String? {
        val manager = service.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        return manager.primaryClip
            ?.getItemAt(0)
            ?.coerceToText(service)
            ?.toString()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }
}
