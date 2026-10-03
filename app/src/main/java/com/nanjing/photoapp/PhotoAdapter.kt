package com.nanjing.photoapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.nanjing.photoapp.model.Photo

// 相册里的照片格子列表
// 【新版改进】
// - 格子被复用时先取消上一次没加载完的图片，并恢复默认背景色（以前快速滑动偶尔会串图、视频格子的深色背景残留到图片格子上）
// - 点击时按“当前实际位置”找照片（删除/新上传后位置会变，以前可能打开或删除错照片）
// - 只改选中状态时只刷新勾选标记，不重新加载图片（拖动多选时不闪烁）
// - 拖动多选时，手指往回拖，已经划出范围的格子会恢复原来的状态（和系统相册一样）
// - 支持在列表头部插入新上传的照片、按id删除照片，不用整页刷新
class PhotoAdapter(
    photos: List<Photo>,
    private var canManage: Boolean, // 是否已登录（决定删除按钮/多选是否可用）
    private val onPhotoClick: (position: Int) -> Unit,
    private val onDeleteClick: (Photo) -> Unit,
    private val onLongPressEnterSelect: (position: Int) -> Unit
) : RecyclerView.Adapter<PhotoAdapter.VH>() {

    private val photos = ArrayList<Photo>(photos)

    var selectionMode: Boolean = false
        private set
    private val selectedIds = mutableSetOf<Int>()

    // 新版：选中数量变化时通知界面（用来更新“删除选中(N)”按钮上的数字）
    var onSelectionChanged: (() -> Unit)? = null

    init {
        setHasStableIds(true)
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val image: android.widget.ImageView = view.findViewById(R.id.imagePhoto)
        val deleteBtn: android.widget.ImageButton = view.findViewById(R.id.btnDelete)
        val videoIcon: android.widget.ImageView = view.findViewById(R.id.iconVideo)
        val checkMark: android.widget.TextView = view.findViewById(R.id.checkMark)
        val selectedOverlay: View = view.findViewById(R.id.selectedOverlay)
    }

    override fun getItemId(position: Int): Long = photos[position].id.toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_photo, parent, false)
        val holder = VH(view)
        // 点击事件只在创建时设置一次，点击时再按“当前实际位置”取照片
        holder.deleteBtn.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onDeleteClick(photos[pos])
        }
        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
            if (selectionMode) {
                toggleSelection(photos[pos].id)
                notifyItemChanged(pos, PAYLOAD_SELECTION)
            } else {
                onPhotoClick(pos)
            }
        }
        holder.itemView.setOnLongClickListener {
            val pos = holder.bindingAdapterPosition
            // 新版：多选模式下长按某一张，也能从它开始拖动连续选
            if (canManage && pos != RecyclerView.NO_POSITION) onLongPressEnterSelect(pos)
            true
        }
        return holder
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val photo = photos[position]
        // 先取消这个格子上一次还没加载完的图片，防止复用格子时显示成别的照片
        Glide.with(holder.image).clear(holder.image)
        if (photo.isVideo() && photo.thumb_url == null) {
            holder.image.setImageDrawable(null)
            holder.image.setBackgroundColor(0xFF333333.toInt())
        } else {
            holder.image.setBackgroundColor(0xFFE0E0E0.toInt()) // 恢复默认浅灰背景（这个格子之前可能是视频的深色背景）
            Glide.with(holder.image).load(photo.gridThumbUrl()).fitCenter().into(holder.image)
        }

        holder.videoIcon.visibility = if (photo.isVideo()) View.VISIBLE else View.GONE
        bindSelection(holder, photo)
    }

    // 只刷新勾选状态（不重新加载图片）
    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_SELECTION)) {
            bindSelection(holder, photos[position])
        } else {
            onBindViewHolder(holder, position)
        }
    }

    private fun bindSelection(holder: VH, photo: Photo) {
        val isSelected = selectedIds.contains(photo.id)
        holder.deleteBtn.visibility = if (canManage && !selectionMode) View.VISIBLE else View.GONE
        holder.checkMark.visibility = if (canManage && selectionMode) View.VISIBLE else View.GONE
        holder.checkMark.setBackgroundResource(
            if (isSelected) R.drawable.bg_check_circle else R.drawable.bg_check_circle_unselected
        )
        holder.selectedOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE
    }

    override fun getItemCount() = photos.size

    fun getPhoto(position: Int): Photo? = photos.getOrNull(position)

    fun updateData(newPhotos: List<Photo>) {
        photos.clear()
        photos.addAll(newPhotos)
        // 刷新之后清理已经不存在的选中项
        selectedIds.retainAll(newPhotos.map { it.id }.toSet())
        notifyDataSetChanged()
        onSelectionChanged?.invoke()
    }

    // 新版：在末尾追加下一页（只刷新新加的格子，已有格子不动）
    fun appendData(more: List<Photo>) {
        if (more.isEmpty()) return
        val start = photos.size
        photos.addAll(more)
        notifyItemRangeInserted(start, more.size)
    }

    // 新版：新上传的照片插到最前面
    fun insertAtTop(photo: Photo) {
        photos.add(0, photo)
        notifyItemInserted(0)
    }

    // 新版：按id删除若干张（删除后不整页刷新，列表停在原来的位置）
    fun removeIds(ids: Set<Int>) {
        var i = photos.size - 1
        while (i >= 0) {
            if (ids.contains(photos[i].id)) {
                photos.removeAt(i)
                notifyItemRemoved(i)
            }
            i--
        }
        if (selectedIds.removeAll(ids)) onSelectionChanged?.invoke()
    }

    // 新版：登录状态变化（比如登录过期被自动退出）时刷新删除按钮
    fun setCanManage(enabled: Boolean) {
        if (canManage == enabled) return
        canManage = enabled
        if (!enabled) selectionMode = false
        notifyItemRangeChanged(0, photos.size, PAYLOAD_SELECTION)
    }

    fun setSelectionMode(enabled: Boolean) {
        selectionMode = enabled
        if (!enabled) selectedIds.clear()
        notifyItemRangeChanged(0, photos.size, PAYLOAD_SELECTION)
        onSelectionChanged?.invoke()
    }

    fun toggleSelection(id: Int) {
        if (selectedIds.contains(id)) selectedIds.remove(id) else selectedIds.add(id)
        onSelectionChanged?.invoke()
    }

    // 全选（如果已经全选了，再点一次就是取消全选）
    fun selectAll() {
        val allSelected = photos.isNotEmpty() && selectedIds.size == photos.size
        selectedIds.clear()
        if (!allSelected) selectedIds.addAll(photos.map { it.id })
        notifyItemRangeChanged(0, photos.size, PAYLOAD_SELECTION)
        onSelectionChanged?.invoke()
    }

    fun isAllSelected(): Boolean = photos.isNotEmpty() && selectedIds.size == photos.size

    fun clearSelection() {
        selectedIds.clear()
        notifyItemRangeChanged(0, photos.size, PAYLOAD_SELECTION)
        onSelectionChanged?.invoke()
    }

    fun getSelectedIds(): Set<Int> = selectedIds.toSet()

    // ===== 拖动多选支持 =====
    fun isPositionSelected(position: Int): Boolean {
        if (position < 0 || position >= photos.size) return false
        return selectedIds.contains(photos[position].id)
    }

    // 新版：拖动开始时记住“拖动前”的选中状态；拖动范围缩小时，划出范围的格子恢复成拖动前的样子
    private var dragBase: Set<Int>? = null
    private var dragLastLo = -1
    private var dragLastHi = -1

    fun beginDrag() {
        dragBase = selectedIds.toSet()
        dragLastLo = -1
        dragLastHi = -1
    }

    fun endDrag() {
        dragBase = null
        dragLastLo = -1
        dragLastHi = -1
    }

    // 把 start..end 范围内的格子统一设为选中或取消选中（范围外的恢复拖动前的状态）
    fun setRangeSelected(start: Int, end: Int, selecting: Boolean) {
        if (photos.isEmpty()) return
        val lo = start.coerceIn(0, photos.size - 1)
        val hi = end.coerceIn(0, photos.size - 1)
        if (lo > hi) return
        val base = dragBase ?: selectedIds.toSet().also { dragBase = it }
        selectedIds.clear()
        selectedIds.addAll(base)
        for (i in lo..hi) {
            val id = photos[i].id
            if (selecting) selectedIds.add(id) else selectedIds.remove(id)
        }
        // 需要刷新的范围 = 这次的范围 + 上次的范围（上次范围里被划出去的格子要恢复）
        val refreshLo = if (dragLastLo < 0) lo else minOf(lo, dragLastLo)
        val refreshHi = if (dragLastHi < 0) hi else maxOf(hi, dragLastHi).coerceAtMost(photos.size - 1)
        dragLastLo = lo
        dragLastHi = hi
        if (refreshLo <= refreshHi) notifyItemRangeChanged(refreshLo, refreshHi - refreshLo + 1, PAYLOAD_SELECTION)
        onSelectionChanged?.invoke()
    }

    companion object {
        private const val PAYLOAD_SELECTION = "selection"
    }
}
