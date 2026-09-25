package com.nanjing.photoapp

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.github.chrisbanes.photoview.PhotoView
import com.nanjing.photoapp.databinding.ActivityPhotoViewerBinding
import com.nanjing.photoapp.model.Photo

class PhotoViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhotoViewerBinding
    private var photos: List<Photo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 从共享内存拿列表（不走Intent，避免大列表超限闪退）
        photos = PhotoStore.photos
        if (photos.isEmpty()) { finish(); return }

        val startIndex = intent.getIntExtra("start_index", 0).coerceIn(0, photos.size - 1)

        val pagerAdapter = ViewerPagerAdapter(photos) { photo -> openVideo(photo) }
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.setCurrentItem(startIndex, false)

        updateCounter(startIndex)
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) { updateCounter(position) }
        })

        binding.btnClose.setOnClickListener { finish() }
    }

    private fun updateCounter(position: Int) {
        binding.textCounter.text = "${position + 1} / ${photos.size}"
    }

    private fun openVideo(photo: Photo) {
        val intent = Intent(this, VideoPlayerActivity::class.java)
        intent.putExtra("video_url", photo.url)
        startActivity(intent)
    }

    // ================= ViewPager2 适配器 =================
    private class ViewerPagerAdapter(
        val photos: List<Photo>,
        val onVideoTap: (Photo) -> Unit
    ) : RecyclerView.Adapter<ViewerPagerAdapter.PageVH>() {

        class PageVH(view: View) : RecyclerView.ViewHolder(view) {
            val image: PhotoView = view.findViewById(R.id.pageImage)
            val playIcon: ImageView = view.findViewById(R.id.pagePlayIcon)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageVH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_viewer_page, parent, false)
            return PageVH(v)
        }

        override fun onBindViewHolder(holder: PageVH, position: Int) {
            val photo = photos[position]
            holder.image.setScale(1f, false) // 复用页面时重置缩放，避免残留上一张的放大状态
            if (photo.isVideo()) {
                // 视频：显示封面帧（有的话）+ 大播放按钮，点击进入全屏播放
                holder.playIcon.visibility = View.VISIBLE
                holder.image.isZoomable = false // 视频页不缩放，让播放按钮点击正常
                if (photo.thumb_url != null) {
                    Glide.with(holder.image).load(photo.thumb_url).into(holder.image)
                } else {
                    holder.image.setImageDrawable(null)
                    holder.image.setBackgroundColor(0xFF222222.toInt())
                }
                holder.playIcon.setOnClickListener { onVideoTap(photo) }
            } else {
                // 图片：PhotoView 自带双指缩放（以两指中点为锚点）、双击、拖动
                holder.playIcon.visibility = View.GONE
                holder.image.isZoomable = true
                val builder = Glide.with(holder.image).load(photo.url)
                if (photo.thumb_url != null) {
                    builder.thumbnail(Glide.with(holder.image).load(photo.thumb_url)).into(holder.image)
                } else {
                    builder.into(holder.image)
                }
            }
        }

        override fun getItemCount() = photos.size
    }
}
