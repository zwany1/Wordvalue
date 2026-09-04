package com.qingsheng.ime.bridge

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.qingsheng.ime.data.AppDatabase
import com.qingsheng.ime.data.SettingsStore
import com.qingsheng.ime.skill.ContextManager
import com.qingsheng.ime.skill.ReplyStyle
import com.qingsheng.ime.skill.SkillRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/**
 * 供 uni-app 原生插件模块（QingshengModule）调用的导出 API。
 * 插件壳在离线打包工程中编译，本类随主 APK 一起发布。
 */
object QingshengBridge {

    fun getImeStatus(context: Context): JSONObject {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
            as android.view.inputmethod.InputMethodManager
        val enabled = imm.enabledInputMethodList.any { it.packageName == context.packageName }
        return JSONObject().put("enabled", enabled)
    }

    fun getSettings(context: Context): JSONObject {
        val store = SettingsStore(context)
        return JSONObject()
            .put("defaultStyle", store.defaultStyle.name)
            .put("maxReplyLength", store.maxReplyLength)
            .put("replyCount", store.replyCount)
            .put("saveHistory", store.saveHistory)
    }

    fun setStyle(context: Context, styleName: String): Boolean {
        val style = runCatching { ReplyStyle.valueOf(styleName) }.getOrNull() ?: return false
        SettingsStore(context).defaultStyle = style
        return true
    }

    fun setMaxReplyLength(context: Context, length: Int) {
        SettingsStore(context).maxReplyLength = length
    }

    fun setSaveHistory(context: Context, value: Boolean) {
        SettingsStore(context).saveHistory = value
    }

    fun listTargets(context: Context): JSONArray = runBlocking {
        val manager = ContextManager(SkillRepository.get(context), AppDatabase.get(context))
        val array = JSONArray()
        manager.listTargets().forEach { target ->
            array.put(
                JSONObject()
                    .put("id", target.id)
                    .put("name", target.name)
                    .put("platform", target.platform)
                    .put("stage", target.stage)
            )
        }
        array
    }

    fun getHistory(context: Context, limit: Int = 50): JSONArray = runBlocking {
        val dao = AppDatabase.get(context).messageDao()
        val array = JSONArray()
        dao.recentLatest(limit).forEach { message ->
            array.put(
                JSONObject()
                    .put("content", message.content)
                    .put("sender", message.sender)
                    .put("emotion", message.emotion)
                    .put("stageId", message.stageId)
                    .put("mode", message.mode)
                    .put("timestamp", message.timestamp)
            )
        }
        array
    }

    fun openNativeSettings(context: Context) {
        context.startActivity(
            Intent().setClassName(context, "com.qingsheng.ime.home.SettingsActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun openImeSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
