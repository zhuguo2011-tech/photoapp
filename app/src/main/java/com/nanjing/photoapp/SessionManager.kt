package com.nanjing.photoapp

import android.content.Context
import com.nanjing.photoapp.api.ApiClient

object SessionManager {
    private const val PREF_NAME = "photoapp_session"
    private const val KEY_TOKEN = "token"
    private const val KEY_USERNAME = "username"
    private const val KEY_BASE_URL = "base_url"
    private const val VIEW_TOKEN_PREFIX = "view_token_album_"

    // ===== 管理员登录状态 =====
    fun saveLogin(context: Context, token: String, username: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TOKEN, token).putString(KEY_USERNAME, username).apply()
    }

    fun logout(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_TOKEN).remove(KEY_USERNAME).apply()
    }

    fun getToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_TOKEN, null)
    }

    fun getAuthHeader(context: Context): String? {
        val token = getToken(context) ?: return null
        return "Bearer $token"
    }

    fun getUsername(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USERNAME, null)
    }

    fun isLoggedIn(context: Context): Boolean {
        return getToken(context) != null
    }

    // ===== 服务器地址设置（可以随时改端口，不用重新编译APP） =====
    fun getBaseUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_BASE_URL, null) ?: ApiClient.DEFAULT_BASE_URL
    }

    fun setBaseUrl(context: Context, url: String) {
        var normalized = url.trim()
        if (!normalized.endsWith("/")) normalized += "/"
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_BASE_URL, normalized).apply()
    }

    fun resetBaseUrl(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_BASE_URL).apply()
    }

    // ===== 相册密码验证令牌（每个相册单独存，验证通过一次后一段时间内不用重复输密码） =====
    fun getViewToken(context: Context, albumId: Int): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(VIEW_TOKEN_PREFIX + albumId, null)
    }

    fun saveViewToken(context: Context, albumId: Int, token: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(VIEW_TOKEN_PREFIX + albumId, token).apply()
    }

    fun clearViewToken(context: Context, albumId: Int) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(VIEW_TOKEN_PREFIX + albumId).apply()
    }

    // 把本地存的所有"已解锁相册令牌"打包成JSON，传给相册列表接口，
    // 这样已经解锁过的相册在列表页也能正常显示封面
    fun getAllViewTokensJson(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val map = mutableMapOf<String, String>()
        for ((key, value) in prefs.all) {
            if (key.startsWith(VIEW_TOKEN_PREFIX) && value is String) {
                val albumId = key.removePrefix(VIEW_TOKEN_PREFIX)
                map[albumId] = value
            }
        }
        val sb = StringBuilder("{")
        map.entries.forEachIndexed { i, (k, v) ->
            if (i > 0) sb.append(",")
            sb.append("\"$k\":\"$v\"")
        }
        sb.append("}")
        return sb.toString()
    }
}
