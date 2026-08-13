package com.nanjing.photoapp.model

data class Photo(
    val id: Int,
    val filename: String,
    val original_name: String?,
    val uploaded_at: String?,
    val url: String
)
