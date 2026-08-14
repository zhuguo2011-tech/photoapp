package com.nanjing.photoapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.nanjing.photoapp.model.Album

class AlbumAdapter(
    private var albums: List<Album>,
    private val onClick: (Album) -> Unit,
    private val onLongClick: (Album) -> Unit
) : RecyclerView.Adapter<AlbumAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val image: android.widget.ImageView = view.findViewById(R.id.imageCover)
        val name: android.widget.TextView = view.findViewById(R.id.textName)
        val count: android.widget.TextView = view.findViewById(R.id.textCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_album, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val album = albums[position]
        holder.name.text = album.name
        holder.count.text = "${album.photo_count}张"

        if (album.cover_url != null) {
            Glide.with(holder.image.context).load(album.cover_url).centerCrop().into(holder.image)
        } else {
            holder.image.setImageDrawable(null)
            holder.image.setBackgroundColor(0xFFE0E0E0.toInt())
        }

        holder.itemView.setOnClickListener { onClick(album) }
        holder.itemView.setOnLongClickListener {
            onLongClick(album)
            true
        }
    }

    override fun getItemCount() = albums.size

    fun updateData(newAlbums: List<Album>) {
        albums = newAlbums
        notifyDataSetChanged()
    }
}
