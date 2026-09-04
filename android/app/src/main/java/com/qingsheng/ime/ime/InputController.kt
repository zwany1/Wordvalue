package com.qingsheng.ime.ime

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent

class InputController(private val service: InputMethodService) {

    val connection get() = service.currentInputConnection

    fun commit(text: String) {
        connection?.commitText(text, 1)
    }

    fun deleteBackward() {
        connection?.deleteSurroundingText(1, 0)
    }

    fun sendEnter() {
        connection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        connection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }
}
