package com.nanjing.photoapp.model

data class Album(
    val id: Int,
    val name: String,
    val created_at: String?,
    val photo_count: Int,
    val cover_url: String?
)
