package com.nanjing.photoapp.api

import com.google.gson.Gson
import com.nanjing.photoapp.model.SimpleResponse
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    // ⚠️ 这里改成你自己后端API的地址，末尾必须带斜杠 /
    // 例如你把 photoapp-backend 文件夹里的东西放到了服务器的 /photoapp/ 目录下：
    const val BASE_URL = "http://146.56.204.247:51912/photoapp/"

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(logging)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
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
