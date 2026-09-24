package com.nanjing.photoapp.api

import android.content.Context
import com.google.gson.Gson
import com.nanjing.photoapp.SessionManager
import com.nanjing.photoapp.model.SimpleResponse
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    // 默认服务器地址（末尾必须带斜杠 /）。之后可以在App里"服务器设置"随时改，不用重新编译。
    const val DEFAULT_BASE_URL = "http://146.56.204.247:51912/photoapp/"

    private var cachedBaseUrl: String? = null
    private var cachedService: ApiService? = null

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(logging)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS) // 视频上传比较大，写超时给长一点
        .build()

    // 每次调用都会检查一下当前设置的服务器地址有没有变，变了就重新构建
    fun service(context: Context): ApiService {
        val baseUrl = SessionManager.getBaseUrl(context)
        if (cachedService == null || cachedBaseUrl != baseUrl) {
            cachedBaseUrl = baseUrl
            cachedService = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)
        }
        return cachedService!!
    }

    // 从失败的响应里尝试解析出后端返回的中文错误提示
    fun <T> errorMessage(response: Response<T>): String {
        return try {
            val body = response.errorBody()?.string()
            if (body.isNullOrBlank()) {
                "请求失败(${response.code()})"
            } else {
                val parsed = Gson().fromJson(body, SimpleResponse::class.java)
                parsed.error ?: "请求失败(${response.code()})"
            }
        } catch (e: Exception) {
            "请求失败(${response.code()})"
        }
    }
}
