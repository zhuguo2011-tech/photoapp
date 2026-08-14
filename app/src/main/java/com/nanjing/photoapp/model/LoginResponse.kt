package com.nanjing.photoapp.model

data class LoginResponse(
    val token: String?,
    val username: String?,
    val error: String?
)

data class SimpleResponse(
    val success: Boolean?,
    val error: String?
)

data class AlbumCreateResponse(
    val id: Int?,
    val name: String?,
    val error: String?
)

data class UploadResponse(
    val id: Int?,
    val filename: String?,
    val url: String?,
    val error: String?
)

data class LoginRequest(val username: String, val password: String)
data class AlbumCreateRequest(val name: String)
data class IdRequest(val id: Int)

// 相册密码相关
data class SetPasswordRequest(val id: Int, val password: String)
data class SetPasswordResponse(val success: Boolean?, val has_password: Boolean?, val error: String?)

data class VerifyPasswordRequest(val album_id: Int, val password: String)
data class VerifyPasswordResponse(val view_token: String?, val no_password_needed: Boolean?, val error: String?)

// 批量删除
data class BatchDeleteRequest(val ids: List<Int>)
data class BatchDeleteResponse(val success: Boolean?, val deleted: Int?, val error: String?)
