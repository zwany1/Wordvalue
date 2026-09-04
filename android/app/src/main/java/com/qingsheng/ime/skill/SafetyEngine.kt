package com.qingsheng.ime.skill

class SafetyEngine(private val signals: SignalsData) {

    private val stopKeywords: List<String> = signals.stop.keywords
    private val closingLine: String = signals.stop.action

    sealed class InputCheck {
        data object Allowed : InputCheck()
        data class Blocked(val suggestedReply: String) : InputCheck()
    }

    fun checkInput(message: String): InputCheck {
        val hit = stopKeywords.firstOrNull { it in message }
        return if (hit != null) {
            InputCheck.Blocked(closingLine)
        } else {
            InputCheck.Allowed
        }
    }

    fun filterOutput(replies: List<String>): List<String> =
        replies.map { text ->
            if (stopKeywords.any { it in text }) closingLine else text
        }
}
