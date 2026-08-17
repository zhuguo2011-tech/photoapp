package com.nanjing.photoapp.model

import java.io.Serializable

data class Photo(
    val id: Int,
    val filename: String,
    val original_name: String?,
    val type: String = "image", // "image" 或 "video"
    val uploaded_at: String?,
    val url: String,
    val thumb_url: String? = null // 网格用的小图，为空时退回用url
) : Serializable {
    fun isVideo() = type == "video"
    fun gridThumbUrl(): String = thumb_url ?: url
}
