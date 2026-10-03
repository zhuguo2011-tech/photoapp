package com.nanjing.photoapp.api

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.nanjing.photoapp.SessionManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.EOFException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import java.util.concurrent.TimeUnit

object ApiClient {

    // 默认服务器地址（末尾必须带斜杠 /）。之后可以在App里"服务器设置"随时改，不用重新编译。
    const val DEFAULT_BASE_URL = "http://146.56.204.247:62225/photoapp/"

    private var cachedBaseUrl: String? = null
    private var cachedService: ApiService? = null

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    // 新版：上传接口单独把“等服务器回复”的时间放宽到10分钟。
    // 大视频传完后服务器还要转码（可能要好几分钟），普通接口60秒就够了。
    private val uploadTimeoutInterceptor = Interceptor { chain ->
        val request = chain.request()
        if (request.url.encodedPath.endsWith("photo_upload.php")) {
            chain.withReadTimeout(10, TimeUnit.MINUTES)
                .withWriteTimeout(10, TimeUnit.MINUTES)
                .proceed(request)
        } else {
            chain.proceed(request)
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(uploadTimeoutInterceptor)
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
            cachedService = serviceFor(baseUrl)
        }
        return cachedService!!
    }

    // 用指定的地址建一个接口对象（“服务器设置”里点“测试连接”时也用它，不影响当前保存的地址）
    fun serviceFor(baseUrl: String): ApiService {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    // 服务器返回的错误详情（新版）
    // needLogin：管理员登录失效了（APP会自动退出登录状态）
    // needPassword：这个相册需要输入查看密码
    // authExpired：请求里带着的管理员登录令牌已经过期
    data class ApiError(
        val message: String,
        val code: Int,
        val needLogin: Boolean = false,
        val needPassword: Boolean = false,
        val authExpired: Boolean = false
    )

    private data class ErrorBody(
        val error: String?,
        val need_login: Boolean?,
        val need_password: Boolean?,
        val auth_expired: Boolean?
    )

    // 从失败的响应里解析出后端返回的中文错误提示和标记（注意：一个响应只能解析一次）
    fun <T> parseError(response: Response<T>): ApiError {
        val code = response.code()
        var parsed: ErrorBody? = null
        try {
            val body = response.errorBody()?.string()
            if (!body.isNullOrBlank()) {
                parsed = Gson().fromJson(body, ErrorBody::class.java)
            }
        } catch (e: Exception) {
            parsed = null // 不是JSON（比如Nginx返回的网页），下面按状态码给提示
        }
        return ApiError(
            message = parsed?.error ?: httpErrorText(code),
            code = code,
            needLogin = parsed?.need_login == true,
            needPassword = parsed?.need_password == true,
            authExpired = parsed?.auth_expired == true
        )
    }

    // 从失败的响应里尝试解析出后端返回的中文错误提示（保留原来的函数，内部改用 parseError）
    fun <T> errorMessage(response: Response<T>): String {
        return parseError(response).message
    }

    // 新版：常见的HTTP状态码给出看得懂的提示（以前一律显示“请求失败(状态码)”）
    fun httpErrorText(code: Int): String = when (code) {
        413 -> "文件太大，超过了服务器允许的上传大小（需要把服务器Nginx/PHP的上传限制调大）"
        502, 504 -> "服务器处理超时（视频可能还在转码），请稍后下拉刷新看看是否已经成功，不要重复上传"
        404 -> "找不到接口（404），请检查“服务器设置”里的地址是否正确"
        429 -> "操作太频繁，请稍后再试"
        in 500..599 -> "服务器出错了（$code）"
        else -> "请求失败($code)"
    }

    // 新版：网络异常给出具体原因（以前一律是“网络请求失败”）
    fun networkErrorMessage(e: Throwable): String = when (e) {
        is UnknownHostException -> "找不到服务器，请检查网络，或“服务器设置”里的地址是否正确"
        is ConnectException -> "连不上服务器，请检查“服务器设置”里的地址和端口（端口是不是换了？）"
        is SocketTimeoutException -> "连接超时：网络太慢或服务器没有响应，请稍后再试"
        is UnknownServiceException -> "系统不允许连接这个地址（明文http被禁止）"
        is JsonParseException, is EOFException -> "服务器返回的内容无法识别，请检查“服务器设置”里的地址是否正确"
        is IllegalArgumentException -> "服务器地址格式不对，请到“服务器设置”里检查地址"
        else -> "网络请求失败，请检查服务器地址和网络连接"
    }
}
