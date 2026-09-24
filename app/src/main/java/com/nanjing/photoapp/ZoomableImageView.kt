package com.nanjing.photoapp

import android.content.Context
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.max
import kotlin.math.min

// 支持双指缩放、放大后拖动、双击放大/还原的 ImageView
// 用在大图查看器里。未放大时不拦截横向滑动，好让 ViewPager2 正常左右翻页；
// 放大后拦截触摸事件用于拖动查看，不触发翻页。
class ZoomableImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : AppCompatImageView(context, attrs, defStyle) {

    private val matrixValues = FloatArray(9)
    private var currentScale = 1f
    private val minScale = 1f
    private val maxScale = 5f

    // 是否启用缩放（视频封面页关闭，让播放按钮点击正常）
    var isZoomEnabled = true

    private val imgMatrix = Matrix()
    private var lastFocusX = 0f
    private var lastFocusY = 0f

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val factor = detector.scaleFactor
            val newScale = (currentScale * factor).coerceIn(minScale, maxScale)
            val realFactor = newScale / currentScale
            imgMatrix.postScale(realFactor, realFactor, detector.focusX, detector.focusY)
            currentScale = newScale
            fixTranslation()
            imageMatrix = imgMatrix
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (currentScale > minScale) {
                resetZoom()
            } else {
                val target = 2.5f
                imgMatrix.postScale(target, target, e.x, e.y)
                currentScale = target
                fixTranslation()
                imageMatrix = imgMatrix
            }
            return true
        }
    })

    init {
        scaleType = ScaleType.FIT_CENTER
    }

    fun resetZoom() {
        currentScale = 1f
        imgMatrix.reset()
        imageMatrix = imgMatrix
        scaleType = ScaleType.FIT_CENTER
    }

    override fun setImageDrawable(drawable: android.graphics.drawable.Drawable?) {
        super.setImageDrawable(drawable)
        resetZoom()
    }

    private fun fixTranslation() {
        imgMatrix.getValues(matrixValues)
        val transX = matrixValues[Matrix.MTRANS_X]
        val transY = matrixValues[Matrix.MTRANS_Y]
        val fixX = getFixTrans(transX, width.toFloat(), (drawable?.intrinsicWidth ?: 0) * currentScale)
        val fixY = getFixTrans(transY, height.toFloat(), (drawable?.intrinsicHeight ?: 0) * currentScale)
        imgMatrix.postTranslate(fixX, fixY)
    }

    private fun getFixTrans(trans: Float, viewSize: Float, contentSize: Float): Float {
        val minTrans: Float
        val maxTrans: Float
        if (contentSize <= viewSize) {
            minTrans = 0f
            maxTrans = viewSize - contentSize
        } else {
            minTrans = viewSize - contentSize
            maxTrans = 0f
        }
        if (trans < minTrans) return minTrans - trans
        if (trans > maxTrans) return maxTrans - trans
        return 0f
    }

    private var lastTouchX = 0f
    private var lastTouchY = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isZoomEnabled) return super.onTouchEvent(event) // 视频页：不处理缩放，交给默认逻辑

        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                // 放大状态时，告诉父容器(ViewPager2)先别拦截，让本控件处理拖动
                if (currentScale > minScale) parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (currentScale > minScale && event.pointerCount == 1) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    imgMatrix.postTranslate(dx, dy)
                    fixTranslation()
                    imageMatrix = imgMatrix
                    lastTouchX = event.x
                    lastTouchY = event.y
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (currentScale <= minScale) parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }
}
