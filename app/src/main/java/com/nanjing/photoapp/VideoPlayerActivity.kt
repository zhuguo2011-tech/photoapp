package com.nanjing.photoapp

import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.MediaController
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.nanjing.photoapp.databinding.ActivityVideoPlayerBinding

// 全屏视频播放页
// 【新版改进】
// - 切到后台再回来（比如接了个电话、看了条微信），会从刚才的位置继续，而不是黑屏
//   （以前回来后画面是黑的，只能退出重进）
// - 旋转屏幕不会从头开始播放（配合清单文件里的 configChanges）
// - 网络慢、缓冲的时候显示转圈
class VideoPlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideoPlayerBinding
    private lateinit var mediaController: MediaController
    private var videoUri: Uri? = null
    private var resumePosition = 0   // 切到后台前播放到的位置（毫秒）
    private var wasPlaying = true    // 切到后台前是不是正在播放（暂停着的回来后也保持暂停）
    private var stopped = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val url = intent.getStringExtra("video_url")
        if (url.isNullOrEmpty()) { finish(); return }
        videoUri = Uri.parse(url)
        if (savedInstanceState != null) {
            resumePosition = savedInstanceState.getInt("pos", 0)
            wasPlaying = savedInstanceState.getBoolean("playing", true)
        }

        binding.btnClose.setOnClickListener { finish() }

        mediaController = MediaController(this)
        mediaController.setAnchorView(binding.videoView)
        binding.videoView.setMediaController(mediaController)

        binding.videoView.setOnPreparedListener { mp ->
            binding.loading.visibility = View.GONE
            mp.isLooping = false
            if (resumePosition > 0) binding.videoView.seekTo(resumePosition)
            if (wasPlaying) binding.videoView.start()
            mediaController.show(0)
        }
        // 缓冲时显示转圈，缓冲完隐藏
        binding.videoView.setOnInfoListener { _, what, _ ->
            when (what) {
                MediaPlayer.MEDIA_INFO_BUFFERING_START -> binding.loading.visibility = View.VISIBLE
                MediaPlayer.MEDIA_INFO_BUFFERING_END, MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> binding.loading.visibility = View.GONE
            }
            false
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
        binding.videoView.setVideoURI(videoUri)
        binding.videoView.requestFocus()
    }

    override fun onStart() {
        super.onStart()
        if (stopped && videoUri != null) {
            // 从后台切回来：重新加载视频，准备好后会跳到刚才的位置继续
            stopped = false
            binding.loading.visibility = View.VISIBLE
            binding.videoView.setVideoURI(videoUri)
        }
    }

    override fun onPause() {
        super.onPause()
        if (videoUri == null) return
        val pos = binding.videoView.currentPosition
        if (pos > 0) resumePosition = pos
        wasPlaying = binding.videoView.isPlaying
        binding.videoView.pause()
    }

    override fun onStop() {
        super.onStop()
        if (videoUri == null) return
        binding.videoView.stopPlayback()
        stopped = true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("pos", resumePosition)
        outState.putBoolean("playing", wasPlaying)
    }
}
