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
            val image: ImageView = view.findViewById(R.id.pageImage)
            val playIcon: ImageView = view.findViewById(R.id.pagePlayIcon)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageVH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_viewer_page, parent, false)
            return PageVH(v)
        }

        override fun onBindViewHolder(holder: PageVH, position: Int) {
            val photo = photos[position]
            if (photo.isVideo()) {
                // 视频：显示封面帧（有的话）+ 大播放按钮，点击进入全屏播放
                holder.playIcon.visibility = View.VISIBLE
                if (photo.thumb_url != null) {
                    Glide.with(holder.image).load(photo.thumb_url).fitCenter().into(holder.image)
                } else {
                    holder.image.setImageDrawable(null)
                    holder.image.setBackgroundColor(0xFF222222.toInt())
                }
                holder.itemView.setOnClickListener { onVideoTap(photo) }
                holder.playIcon.setOnClickListener { onVideoTap(photo) }
            } else {
                // 图片：先加载缩略图占位，再加载原图，网速慢时不会长时间空白
                holder.playIcon.visibility = View.GONE
                holder.itemView.setOnClickListener(null)
                val builder = Glide.with(holder.image).load(photo.url)
                if (photo.thumb_url != null) {
                    builder.thumbnail(Glide.with(holder.image).load(photo.thumb_url)).fitCenter().into(holder.image)
                } else {
                    builder.fitCenter().into(holder.image)
                }
            }
        }

        override fun getItemCount() = photos.size
    }
}
