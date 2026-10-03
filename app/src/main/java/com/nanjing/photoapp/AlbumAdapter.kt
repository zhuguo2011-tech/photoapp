package com.nanjing.photoapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.nanjing.photoapp.model.Album

// 首页相册卡片列表
// 【新版】带密码的相册右上角显示锁（🔒没解锁 / 🔓已解锁），没解锁时封面显示“需要密码”；
//        格子复用时先取消上一次没加载完的封面图，防止串图
class AlbumAdapter(
    private var albums: List<Album>,
    private val onClick: (Album) -> Unit,
    private val onLongClick: (Album) -> Unit
) : RecyclerView.Adapter<AlbumAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val image: android.widget.ImageView = view.findViewById(R.id.imageCover)
        val name: android.widget.TextView = view.findViewById(R.id.textName)
        val count: android.widget.TextView = view.findViewById(R.id.textCount)
        val videoIcon: android.widget.ImageView = view.findViewById(R.id.iconCoverVideo)
        val lock: android.widget.TextView = view.findViewById(R.id.textLock)
        val lockedText: android.widget.TextView = view.findViewById(R.id.textLocked)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_album, parent, false)
        val holder = VH(view)
        // 点击时按“当前实际位置”取相册（列表刷新后位置可能变化）
        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onClick(albums[pos])
        }
        holder.itemView.setOnLongClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onLongClick(albums[pos])
            true
        }
        return holder
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val album = albums[position]
        holder.name.text = album.name
        holder.count.text = "${album.photo_count}项"
        holder.videoIcon.visibility = if (album.cover_is_video && album.cover_url != null) View.VISIBLE else View.GONE
        holder.lock.visibility = if (album.has_password) View.VISIBLE else View.GONE
        holder.lock.text = if (album.unlocked) "🔓" else "🔒"
        holder.lockedText.visibility = if (album.has_password && !album.unlocked && album.cover_url == null) View.VISIBLE else View.GONE

        Glide.with(holder.image).clear(holder.image)
        if (album.cover_url != null) {
            Glide.with(holder.image).load(album.cover_url).fitCenter().into(holder.image)
        } else {
            holder.image.setImageDrawable(null)
            holder.image.setBackgroundColor(0xFFE0E0E0.toInt())
        }
    }

    override fun getItemCount() = albums.size

    fun updateData(newAlbums: List<Album>) {
        albums = newAlbums
        notifyDataSetChanged()
    }
}
