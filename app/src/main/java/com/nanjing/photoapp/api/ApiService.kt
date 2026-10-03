package com.nanjing.photoapp.api

import com.nanjing.photoapp.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

// 所有后端接口的定义（和服务器上的 .php 文件一一对应）
// 新版说明：参数值为 null 的 @Query/@Header 会被自动省略，所以老服务器也能正常使用
interface ApiService {

    @POST("login.php")
    suspend fun login(@Body body: LoginRequest): Response<LoginResponse>

    // 新版：管理员登录后带上登录令牌，服务器会把带密码的相册也当成“已解锁”（显示封面）
    @GET("albums_list.php")
    suspend fun getAlbums(
        @Query("tokens") tokens: String? = null,
        @Header("Authorization") auth: String? = null
    ): Response<List<Album>>

    @POST("album_create.php")
    suspend fun createAlbum(
        @Header("Authorization") token: String,
        @Body body: AlbumCreateRequest
    ): Response<AlbumCreateResponse>

    @POST("album_delete.php")
    suspend fun deleteAlbum(
        @Header("Authorization") token: String,
        @Body body: DeleteAlbumRequest
    ): Response<SimpleResponse>

    @POST("album_set_password.php")
    suspend fun setAlbumPassword(
        @Header("Authorization") token: String,
        @Body body: SetPasswordRequest
    ): Response<SetPasswordResponse>

    @POST("album_verify_password.php")
    suspend fun verifyAlbumPassword(@Body body: VerifyPasswordRequest): Response<VerifyPasswordResponse>

    // 新版：
    // - beforeId：用“上一页最后一张的id”翻页（翻页中途有人上传/删除也不会重复或漏掉）；老服务器会忽略它，继续用offset
    // - auth：管理员登录令牌，管理员看带密码的相册不用输密码
    @GET("photos_list.php")
    suspend fun getPhotos(
        @Query("album_id") albumId: Int,
        @Header("X-View-Token") viewToken: String?,
        @Query("offset") offset: Int,
        @Query("limit") limit: Int,
        @Query("before_id") beforeId: Int? = null,
        @Header("Authorization") auth: String? = null
    ): Response<List<Photo>>

    @Multipart
    @POST("photo_upload.php")
    suspend fun uploadPhoto(
        @Header("Authorization") token: String,
        @Part("album_id") albumId: RequestBody,
        @Part photo: MultipartBody.Part
    ): Response<UploadResponse>

    @POST("photo_delete.php")
    suspend fun deletePhoto(
        @Header("Authorization") token: String,
        @Body body: IdRequest
    ): Response<SimpleResponse>

    @POST("photo_delete_batch.php")
    suspend fun deletePhotosBatch(
        @Header("Authorization") token: String,
        @Body body: BatchDeleteRequest
    ): Response<BatchDeleteResponse>

    @GET("announcement_get.php")
    suspend fun getAnnouncement(): Response<AnnouncementResponse>

    @POST("announcement_set.php")
    suspend fun setAnnouncement(
        @Header("Authorization") token: String,
        @Body body: AnnouncementRequest
    ): Response<AnnouncementResponse>
}
