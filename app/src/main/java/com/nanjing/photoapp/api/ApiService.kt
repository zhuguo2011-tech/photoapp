package com.nanjing.photoapp.api

import com.nanjing.photoapp.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @POST("login.php")
    suspend fun login(@Body body: LoginRequest): Response<LoginResponse>

    @GET("albums_list.php")
    suspend fun getAlbums(): Response<List<Album>>

    @POST("album_create.php")
    suspend fun createAlbum(
        @Header("Authorization") token: String,
        @Body body: AlbumCreateRequest
    ): Response<AlbumCreateResponse>

    @POST("album_delete.php")
    suspend fun deleteAlbum(
        @Header("Authorization") token: String,
        @Body body: IdRequest
    ): Response<SimpleResponse>

    @GET("photos_list.php")
    suspend fun getPhotos(@Query("album_id") albumId: Int): Response<List<Photo>>

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
}
