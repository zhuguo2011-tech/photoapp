package com.nanjing.photoapp

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

// 支持双指缩放（以图片中心为锚点，跟网页版一致，稳定不跳位）、放大后拖动、双击放大/还原
// 未放大时不拦截横向滑动，让 ViewPager2 正常翻页；放大后拦截触摸用于拖动查看
class ZoomableImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : AppCompatImageView(context, attrs, defStyle) {

    private var scale = 1f          // 当前缩放倍数
    private var transX = 0f         // 当前水平平移
    private var transY = 0f         // 当前垂直平移
    private val minScale = 1f
    private val maxScale = 5f

    var isZoomEnabled = true

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val newScale = (scale * detector.scaleFactor).coerceIn(minScale, maxScale)
            scale = newScale
            if (scale == 1f) { transX = 0f; transY = 0f } // 缩回原始大小就归位居中
            clampTrans()
            applyTransform()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (scale > minScale) {
                // 还原
                scale = 1f; transX = 0f; transY = 0f
            } else {
                scale = 2.5f
            }
            clampTrans()
            applyTransform()
            return true
        }
    })

    init {
        // 用 FIT_CENTER 让图片自动适应屏幕，缩放/平移通过 View 的 scaleX/translationX 属性实现
        scaleType = ScaleType.FIT_CENTER
        isClickable = true
        isFocusable = true
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        resetZoom()
    }

    fun resetZoom() {
        scale = 1f; transX = 0f; transY = 0f
        applyTransform()
    }

    private fun applyTransform() {
        scaleX = scale
        scaleY = scale
        translationX = transX
        translationY = transY
    }

    // 限制平移范围：放大后不让图片被拖出屏幕露出过多空白
    private fun clampTrans() {
        if (scale <= 1f) { transX = 0f; transY = 0f; return }
        // 放大后，图片相对原始显示区域每边多出的可拖动空间
        val maxX = (width * (scale - 1f)) / 2f
        val maxY = (height * (scale - 1f)) / 2f
        transX = transX.coerceIn(-maxX, maxX)
        transY = transY.coerceIn(-maxY, maxY)
    }

    private var lastX = 0f
    private var lastY = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isZoomEnabled) return super.onTouchEvent(event)

        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                if (scale > minScale) parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // 有手指抬起、但还剩手指时，重置基准点到剩余手指，避免从双指切到单指时跳一下
                val remainingIndex = if (event.actionIndex == 0) 1 else 0
                if (remainingIndex < event.pointerCount) {
                    lastX = event.getX(remainingIndex)
                    lastY = event.getY(remainingIndex)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (scale > minScale && event.pointerCount == 1 && !scaleDetector.isInProgress) {
                    transX += event.x - lastX
                    transY += event.y - lastY
                    lastX = event.x
                    lastY = event.y
                    clampTrans()
                    applyTransform()
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (scale <= minScale) parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }
}
