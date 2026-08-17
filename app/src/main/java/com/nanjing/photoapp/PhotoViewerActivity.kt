package com.nanjing.photoapp

import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nanjing.photoapp.databinding.ActivityPhotoViewerBinding
import com.nanjing.photoapp.model.Photo
import kotlin.math.abs

class PhotoViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhotoViewerBinding
    private lateinit var photos: List<Photo>
    private var index = 0
    private lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val json = intent.getStringExtra("photos_json") ?: "[]"
        val type = object : TypeToken<List<Photo>>() {}.type
        photos = Gson().fromJson(json, type)
        index = intent.getIntExtra("start_index", 0).coerceIn(0, (photos.size - 1).coerceAtLeast(0))

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                if (abs(dx) > 120 && abs(velocityX) > 200) {
                    if (dx > 0) showPrev() else showNext()
                    return true
                }
                return false
            }
        })

        binding.imageView.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event); true }
        binding.btnClose.setOnClickListener { finish() }
        binding.btnPrev.setOnClickListener { showPrev() }
        binding.btnNext.setOnClickListener { showNext() }

        render()
    }

    private fun showPrev() {
        if (index > 0) { index--; render() }
    }

    private fun showNext() {
        if (index < photos.size - 1) { index++; render() }
    }

    private fun render() {
        if (photos.isEmpty()) { finish(); return }
        val photo = photos[index]
        binding.textCounter.text = "${index + 1} / ${photos.size}"
        binding.btnPrev.visibility = if (index > 0) View.VISIBLE else View.INVISIBLE
        binding.btnNext.visibility = if (index < photos.size - 1) View.VISIBLE else View.INVISIBLE

        binding.videoView.stopPlayback()

        if (photo.isVideo()) {
            binding.imageView.visibility = View.GONE
            binding.videoView.visibility = View.VISIBLE

            val mediaController = android.widget.MediaController(this)
            mediaController.setAnchorView(binding.videoView)
            binding.videoView.setMediaController(mediaController)
            binding.videoView.setVideoURI(android.net.Uri.parse(photo.url))
            binding.videoView.requestFocus()
            binding.videoView.setOnPreparedListener { mp ->
                mp.isLooping = false
                binding.videoView.start()
                mediaController.show(0) // 0 表示一直显示控制条直到用户点别处
            }
            binding.videoView.setOnErrorListener { _, what, extra ->
                android.widget.Toast.makeText(
                    this,
                    "视频无法播放（错误码 $what/$extra）。如果是老视频，去服务器跑一次视频转码脚本试试。",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                true
            }
            binding.videoView.setOnClickListener { mediaController.show(0) }
        } else {
            binding.videoView.visibility = View.GONE
            binding.imageView.visibility = View.VISIBLE
            Glide.with(this).load(photo.url).into(binding.imageView)
        }
    }

    override fun onStop() {
        super.onStop()
        binding.videoView.stopPlayback()
    }
}
