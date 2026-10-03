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
    val error: String?,
    val has_password: Boolean? = null // 新版服务器会返回：新相册是否带默认密码
)

// 上传成功后服务器返回的这张照片/视频的信息
// （新版服务器会返回完整信息，APP直接插到列表最前面，不用整页刷新；老服务器只返回前几项）
data class UploadResponse(
    val id: Int?,
    val filename: String?,
    val url: String?,
    val error: String?,
    val type: String? = null,
    val original_name: String? = null,
    val uploaded_at: String? = null,
    val thumb_url: String? = null
) {
    fun toPhoto(): Photo? {
        val photoId = id ?: return null
        val photoUrl = url ?: return null
        return Photo(
            id = photoId,
            filename = filename ?: "",
            original_name = original_name,
            type = type ?: "image",
            uploaded_at = uploaded_at,
            url = photoUrl,
            thumb_url = thumb_url
        )
    }
}

data class LoginRequest(val username: String, val password: String)
data class AlbumCreateRequest(val name: String)
data class IdRequest(val id: Int)
data class DeleteAlbumRequest(val id: Int, val password: String)

// 相册密码相关
data class SetPasswordRequest(val id: Int, val password: String)
data class SetPasswordResponse(val success: Boolean?, val has_password: Boolean?, val error: String?)

data class VerifyPasswordRequest(val album_id: Int, val password: String)
data class VerifyPasswordResponse(val view_token: String?, val no_password_needed: Boolean?, val error: String?)

// 批量删除（新版服务器会返回实际删除了哪些id，APP据此只移除这些格子，不用整页刷新）
data class BatchDeleteRequest(val ids: List<Int>)
data class BatchDeleteResponse(val success: Boolean?, val deleted: Int?, val error: String?, val deleted_ids: List<Int>? = null)

// 公告
data class AnnouncementResponse(val announcement: String?, val error: String?)
data class AnnouncementRequest(val announcement: String)
