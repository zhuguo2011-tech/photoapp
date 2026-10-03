package com.nanjing.photoapp

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.github.chrisbanes.photoview.PhotoView
import com.nanjing.photoapp.api.ApiClient
import com.nanjing.photoapp.databinding.ActivityPhotoViewerBinding
import com.nanjing.photoapp.model.Photo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// 大图/视频查看页：左右滑动切换；图片用 PhotoView 支持双指缩放、双击放大、拖动
// 【新版改进】
// - 计数显示相册的总数（以前只显示已加载的数量，比如900张的相册显示“1 / 100”）
// - 翻到已加载的最后几张时，自动从服务器接着加载下一批，可以一直往后翻（以前到第100张就翻不动了）；
//   回到相册页时，这里新加载的也会同步过去
// - 原图按屏幕2倍的清晰度加载（最多2560像素），放大看更清楚；小图不会被放大浪费内存
// - 提前准备好左右相邻的一张，翻页更顺
class PhotoViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhotoViewerBinding
    private val photos = ArrayList<Photo>()
    private lateinit var pagerAdapter: ViewerPagerAdapter
    private var albumId = -1
    private var totalCount = 0
    private var hasMore = false
    private var loadingMore = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 从共享内存拿列表（不走Intent，避免大列表超限闪退）
        photos.addAll(PhotoStore.photos)
        if (photos.isEmpty()) { finish(); return }
        albumId = intent.getIntExtra("album_id", PhotoStore.albumId)
        hasMore = PhotoStore.hasMore && PhotoStore.albumId == albumId
        totalCount = maxOf(PhotoStore.totalCount, photos.size)

        val startIndex = intent.getIntExtra("start_index", 0).coerceIn(0, photos.size - 1)

        // 原图最大加载尺寸：屏幕的2倍，但不超过2560像素（再大手机内存吃不消）
        val dm = resources.displayMetrics
        val maxW = minOf(dm.widthPixels * 2, 2560)
        val maxH = minOf(dm.heightPixels * 2, 2560)

        pagerAdapter = ViewerPagerAdapter(photos, maxW, maxH) { photo -> openVideo(photo) }
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.offscreenPageLimit = 1
        binding.viewPager.setCurrentItem(startIndex, false)

        updateCounter(startIndex)
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateCounter(position)
                PhotoStore.lastViewedIndex = position
                if (position >= photos.size - 5) loadMore()
            }
        })

        binding.btnClose.setOnClickListener { finish() }
        if (startIndex >= photos.size - 5) loadMore()
    }

    private fun updateCounter(position: Int) {
        val total = if (hasMore) maxOf(totalCount, photos.size) else photos.size
        binding.textCounter.text = "${position + 1} / $total"
    }

    // 新版：快翻到已加载的最后几张时，接着从服务器加载下一批
    private fun loadMore() {
        if (loadingMore || !hasMore || albumId <= 0 || photos.isEmpty()) return
        loadingMore = true
        val lastId = photos.last().id
        val offset = photos.size
        lifecycleScope.launch {
            try {
                val response = ApiClient.service(this@PhotoViewerActivity).getPhotos(
                    albumId,
                    SessionManager.getViewToken(this@PhotoViewerActivity, albumId),
                    offset, PAGE_SIZE, lastId,
                    SessionManager.getAuthHeader(this@PhotoViewerActivity)
                )
                if (response.isSuccessful) {
                    val page = response.body() ?: emptyList()
                    val known = HashSet<Int>()
                    photos.forEach { known.add(it.id) }
                    val fresh = page.filter { known.add(it.id) }
                    hasMore = page.size >= PAGE_SIZE
                    if (fresh.isNotEmpty()) {
                        val start = photos.size
                        photos.addAll(fresh)
                        pagerAdapter.notifyItemRangeInserted(start, fresh.size)
                    }
                    if (!hasMore) totalCount = photos.size
                    // 同步给相册页（回去时格子列表里也有这些）
                    PhotoStore.photos = ArrayList(photos)
                    PhotoStore.hasMore = hasMore
                    PhotoStore.totalCount = totalCount
                    PhotoStore.version++
                    updateCounter(binding.viewPager.currentItem)
                }
                // 加载失败（比如密码过期）就先不加载了，回到相册页会处理
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 网络不好时先不加载，用户再往后翻时会再试
            } finally {
                loadingMore = false
            }
        }
    }

    private fun openVideo(photo: Photo) {
        val intent = Intent(this, VideoPlayerActivity::class.java)
        intent.putExtra("video_url", photo.url)
        startActivity(intent)
    }

    // ================= ViewPager2 适配器 =================
    private class ViewerPagerAdapter(
        val photos: List<Photo>,
        val maxW: Int,
        val maxH: Int,
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
            Glide.with(holder.image).clear(holder.image) // 新版：先取消这个页面上一次还没加载完的图，防止串图
            holder.image.setScale(1f, false) // 复用页面时重置缩放，避免残留上一张的放大状态
            holder.image.setBackgroundColor(0) // 新版：清掉上一个视频页可能残留的深色背景
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
                holder.playIcon.setOnClickListener(null)
                holder.image.isZoomable = true
                // 新版：按屏幕2倍清晰度加载原图（CENTER_INSIDE：只缩小不放大；dontTransform：交给PhotoView自己适配屏幕）
                val builder = Glide.with(holder.image).load(photo.url)
                    .override(maxW, maxH)
                    .downsample(DownsampleStrategy.CENTER_INSIDE)
                    .dontTransform()
                if (photo.thumb_url != null) {
                    builder.thumbnail(Glide.with(holder.image).load(photo.thumb_url)).into(holder.image)
                } else {
                    builder.into(holder.image)
                }
            }
        }

        override fun getItemCount() = photos.size
    }

    companion object {
        private const val PAGE_SIZE = 100
    }
}
