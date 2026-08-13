package com.nanjing.photoapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.nanjing.photoapp.model.Photo

class PhotoAdapter(
    private var photos: List<Photo>,
    private val canDelete: Boolean,
    private val onPhotoClick: (Photo) -> Unit,
    private val onDeleteClick: (Photo) -> Unit
) : RecyclerView.Adapter<PhotoAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val image: android.widget.ImageView = view.findViewById(R.id.imagePhoto)
        val deleteBtn: android.widget.ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_photo, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val photo = photos[position]
        Glide.with(holder.image.context).load(photo.url).centerCrop().into(holder.image)

        holder.deleteBtn.visibility = if (canDelete) View.VISIBLE else View.GONE
        holder.deleteBtn.setOnClickListener { onDeleteClick(photo) }
        holder.itemView.setOnClickListener { onPhotoClick(photo) }
    }

    override fun getItemCount() = photos.size

    fun updateData(newPhotos: List<Photo>) {
        photos = newPhotos
        notifyDataSetChanged()
    }
}
