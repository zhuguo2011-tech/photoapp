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
