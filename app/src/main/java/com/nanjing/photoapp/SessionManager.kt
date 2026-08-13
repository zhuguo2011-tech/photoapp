package com.nanjing.photoapp

import android.content.Context

object SessionManager {
    private const val PREF_NAME = "photoapp_session"
    private const val KEY_TOKEN = "token"
    private const val KEY_USERNAME = "username"

    fun saveLogin(context: Context, token: String, username: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TOKEN, token).putString(KEY_USERNAME, username).apply()
    }

    fun logout(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    fun getToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_TOKEN, null)
    }

    // 生成带 "Bearer " 前缀的、可以直接放进请求头的字符串
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
}
