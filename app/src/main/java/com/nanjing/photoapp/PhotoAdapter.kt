package com.nanjing.photoapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.nanjing.photoapp.model.Photo

class PhotoAdapter(
    private var photos: List<Photo>,
    private val canManage: Boolean, // 是否已登录（决定删除按钮/多选是否可用）
    private val onPhotoClick: (position: Int) -> Unit,
    private val onDeleteClick: (Photo) -> Unit,
    private val onLongPressEnterSelect: (Photo) -> Unit
) : RecyclerView.Adapter<PhotoAdapter.VH>() {

    var selectionMode: Boolean = false
        private set
    private val selectedIds = mutableSetOf<Int>()

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val image: android.widget.ImageView = view.findViewById(R.id.imagePhoto)
        val deleteBtn: android.widget.ImageButton = view.findViewById(R.id.btnDelete)
        val videoIcon: android.widget.ImageView = view.findViewById(R.id.iconVideo)
        val checkMark: android.widget.TextView = view.findViewById(R.id.checkMark)
        val selectedOverlay: View = view.findViewById(R.id.selectedOverlay)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_photo, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val photo = photos[position]
        if (photo.isVideo()) {
            holder.image.setImageDrawable(null)
            holder.image.setBackgroundColor(0xFF333333.toInt())
        } else {
            Glide.with(holder.image.context).load(photo.gridThumbUrl()).centerCrop().into(holder.image)
        }

        holder.videoIcon.visibility = if (photo.isVideo()) View.VISIBLE else View.GONE

        val isSelected = selectedIds.contains(photo.id)
        holder.deleteBtn.visibility = if (canManage && !selectionMode) View.VISIBLE else View.GONE
        holder.checkMark.visibility = if (canManage && selectionMode) View.VISIBLE else View.GONE
        holder.checkMark.setBackgroundResource(
            if (isSelected) R.drawable.bg_check_circle else R.drawable.bg_check_circle_unselected
        )
        holder.selectedOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE

        holder.deleteBtn.setOnClickListener { onDeleteClick(photo) }

        holder.itemView.setOnClickListener {
            if (selectionMode) {
                toggleSelection(photo.id)
                notifyItemChanged(position)
            } else {
                onPhotoClick(position)
            }
        }
        holder.itemView.setOnLongClickListener {
            if (canManage && !selectionMode) {
                onLongPressEnterSelect(photo)
            }
            true
        }
    }

    override fun getItemCount() = photos.size

    fun updateData(newPhotos: List<Photo>) {
        photos = newPhotos
        // 刷新之后清理已经不存在的选中项
        selectedIds.retainAll(newPhotos.map { it.id }.toSet())
        notifyDataSetChanged()
    }

    fun setSelectionMode(enabled: Boolean) {
        selectionMode = enabled
        if (!enabled) selectedIds.clear()
        notifyDataSetChanged()
    }

    fun toggleSelection(id: Int) {
        if (selectedIds.contains(id)) selectedIds.remove(id) else selectedIds.add(id)
    }

    fun selectAll() {
        selectedIds.clear()
        selectedIds.addAll(photos.map { it.id })
        notifyDataSetChanged()
    }

    fun clearSelection() {
        selectedIds.clear()
        notifyDataSetChanged()
    }

    fun getSelectedIds(): Set<Int> = selectedIds.toSet()
}
