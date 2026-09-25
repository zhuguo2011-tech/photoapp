package com.nanjing.photoapp

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.MediaController
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.nanjing.photoapp.databinding.ActivityVideoPlayerBinding

class VideoPlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideoPlayerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val url = intent.getStringExtra("video_url")
        if (url.isNullOrEmpty()) { finish(); return }

        binding.btnClose.setOnClickListener { finish() }

        val mediaController = MediaController(this)
        mediaController.setAnchorView(binding.videoView)
        binding.videoView.setMediaController(mediaController)
        binding.videoView.setVideoURI(Uri.parse(url))
        binding.videoView.requestFocus()

        binding.videoView.setOnPreparedListener { mp ->
            binding.loading.visibility = View.GONE
            mp.isLooping = false
            binding.videoView.start()
            mediaController.show(0)
        }
        binding.videoView.setOnErrorListener { _, what, extra ->
            binding.loading.visibility = View.GONE
            Toast.makeText(
                this,
                "视频无法播放（错误码 $what/$extra）。如果是老视频，去服务器跑一次视频转码脚本试试。",
                Toast.LENGTH_LONG
            ).show()
            true
        }
    }

    override fun onStop() {
        super.onStop()
        binding.videoView.stopPlayback()
    }
}
