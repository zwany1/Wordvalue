package com.qingsheng.ime.bridge

import android.content.Context
import android.content.Intent
import android.provider.Settings
import io.dcloud.feature.uniapp.annotation.UniJSMethod
import io.dcloud.feature.uniapp.common.UniSDKInstance
import io.dcloud.feature.uniapp.bridge.UniModule
import org.json.JSONArray
import org.json.JSONObject

/**
 * uni-app 原生插件模块（source 型插件，随离线打包工程编译）。
 * JS 端通过 uni.requireNativePlugin('QingshengIME') 同步调用，
 * 数据与主 APK 的 SharedPreferences、Room 数据库共享。
 */
class QingshengModule : UniModule() {

    private val appContext get() = mUniSDKInstance.context.applicationContext

    @UniJSMethod(uiThread = false)
    fun getImeStatus(): JSONObject = QingshengBridge.getImeStatus(appContext)

    @UniJSMethod(uiThread = false)
    fun getSettings(): JSONObject = QingshengBridge.getSettings(appContext)

    @UniJSMethod(uiThread = false)
    fun setStyle(styleName: String): JSONObject =
        JSONObject().put("ok", QingshengBridge.setStyle(appContext, styleName))

    @UniJSMethod(uiThread = false)
    fun setMaxReplyLength(length: Int): JSONObject {
        QingshengBridge.setMaxReplyLength(appContext, length)
        return JSONObject().put("ok", true)
    }

    @UniJSMethod(uiThread = false)
    fun setSaveHistory(value: Boolean): JSONObject {
        QingshengBridge.setSaveHistory(appContext, value)
        return JSONObject().put("ok", true)
    }

    @UniJSMethod(uiThread = false)
    fun listTargets(): JSONArray = QingshengBridge.listTargets(appContext)

    @UniJSMethod(uiThread = false)
    fun getHistory(): JSONArray = QingshengBridge.getHistory(appContext)

    @UniJSMethod(uiThread = true)
    fun openNativeSettings() {
        QingshengBridge.openNativeSettings(appContext)
    }

    @UniJSMethod(uiThread = true)
    fun openImeSettings() {
        QingshengBridge.openImeSettings(appContext)
    }
}
