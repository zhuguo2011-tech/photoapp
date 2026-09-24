package com.nanjing.photoapp

import android.view.MotionEvent
import androidx.recyclerview.widget.RecyclerView

// 拖动多选：在多选模式下，手指按住一个格子后滑动，途经的格子会连续被选中/取消
// 手指滑到屏幕上下边缘时，列表会自动滚动，方便选很多张
// 用法：把它 addOnItemTouchListener 到 RecyclerView，并在进入多选模式后 setActive(true)
class DragSelectTouchListener(
    private val onSelectRange: (start: Int, end: Int, selecting: Boolean) -> Unit
) : RecyclerView.OnItemTouchListener {

    private var active = false
    private var startPosition = RecyclerView.NO_POSITION
    private var lastPosition = RecyclerView.NO_POSITION
    private var selecting = true // true=选中，false=取消选中（取决于起始格子当前状态）

    private var rv: RecyclerView? = null
    private var autoScrollSpeed = 0
    private val autoScrollHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var autoScrollRunnable: Runnable? = null

    // 由外部告知：起始格子当前是否已选中（决定这次拖动是"批量选"还是"批量取消"）
    var isPositionSelected: (Int) -> Boolean = { false }

    fun setActive(a: Boolean) {
        active = a
        if (!a) {
            startPosition = RecyclerView.NO_POSITION
            lastPosition = RecyclerView.NO_POSITION
            stopAutoScroll()
        }
    }

    // 从长按/按下的某个格子开始拖选
    fun startDrag(position: Int) {
        startPosition = position
        lastPosition = position
        selecting = !isPositionSelected(position)
        onSelectRange(position, position, selecting)
    }

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        this.rv = rv
        if (!active || startPosition == RecyclerView.NO_POSITION) return false
        // 已经在拖选中，拦截后续move/up事件自己处理
        return when (e.actionMasked) {
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
            else -> false
        }
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
        if (!active || startPosition == RecyclerView.NO_POSITION) return
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val v = rv.findChildViewUnder(e.x, e.y)
                if (v != null) {
                    val pos = rv.getChildAdapterPosition(v)
                    if (pos != RecyclerView.NO_POSITION && pos != lastPosition) {
                        lastPosition = pos
                        onSelectRange(
                            minOf(startPosition, pos),
                            maxOf(startPosition, pos),
                            selecting
                        )
                    }
                }
                handleAutoScroll(rv, e.y)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                startPosition = RecyclerView.NO_POSITION
                lastPosition = RecyclerView.NO_POSITION
                stopAutoScroll()
            }
        }
    }

    override fun onRequestDisallowInterceptTouchEvent(disallow: Boolean) {}

    // 手指靠近顶部/底部时自动滚动
    private fun handleAutoScroll(rv: RecyclerView, y: Float) {
        val edge = 120 // 距离上下边缘多少像素内触发自动滚动
        autoScrollSpeed = when {
            y < edge -> -25
            y > rv.height - edge -> 25
            else -> 0
        }
        if (autoScrollSpeed != 0) startAutoScroll(rv) else stopAutoScroll()
    }

    private fun startAutoScroll(rv: RecyclerView) {
        if (autoScrollRunnable != null) return
        autoScrollRunnable = object : Runnable {
            override fun run() {
                if (autoScrollSpeed != 0) {
                    rv.scrollBy(0, autoScrollSpeed)
                    autoScrollHandler.postDelayed(this, 16)
                }
            }
        }
        autoScrollHandler.post(autoScrollRunnable!!)
    }

    private fun stopAutoScroll() {
        autoScrollRunnable?.let { autoScrollHandler.removeCallbacks(it) }
        autoScrollRunnable = null
        autoScrollSpeed = 0
    }
}
