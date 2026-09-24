package com.nanjing.photoapp

import android.content.Context
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

// 支持双指缩放、放大后拖动、双击放大/还原的 ImageView
// 未放大时不拦截横向滑动，让 ViewPager2 正常翻页；放大后拦截触摸用于拖动查看
class ZoomableImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : AppCompatImageView(context, attrs, defStyle) {

    private val baseMatrix = Matrix()    // 图片"适应屏幕"的基础矩阵
    private val drawMatrix = Matrix()    // 实际绘制用的矩阵（基础 + 缩放平移）
    private val values = FloatArray(9)

    private var currentScale = 1f        // 相对于基础大小的缩放倍数
    private val minScale = 1f
    private val maxScale = 5f

    var isZoomEnabled = true

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val factor = detector.scaleFactor
            val newScale = (currentScale * factor).coerceIn(minScale, maxScale)
            val real = newScale / currentScale
            drawMatrix.postScale(real, real, detector.focusX, detector.focusY)
            currentScale = newScale
            fixTrans()
            imageMatrix = drawMatrix
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (currentScale > minScale) {
                resetZoom()
            } else {
                val target = 2.5f
                drawMatrix.postScale(target, target, e.x, e.y)
                currentScale = target
                fixTrans()
                imageMatrix = drawMatrix
            }
            return true
        }
    })

    init {
        scaleType = ScaleType.MATRIX
        isClickable = true
        isFocusable = true
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        post { setupBaseMatrix() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        setupBaseMatrix()
    }

    // 计算把图片按比例缩放到刚好填满屏幕（fitCenter效果）的基础矩阵
    private fun setupBaseMatrix() {
        val d = drawable ?: return
        val dw = d.intrinsicWidth.toFloat()
        val dh = d.intrinsicHeight.toFloat()
        if (dw <= 0 || dh <= 0 || width == 0 || height == 0) return

        val scale = minOf(width / dw, height / dh)
        val dx = (width - dw * scale) / 2f
        val dy = (height - dh * scale) / 2f

        baseMatrix.reset()
        baseMatrix.postScale(scale, scale)
        baseMatrix.postTranslate(dx, dy)

        currentScale = 1f
        drawMatrix.set(baseMatrix)
        imageMatrix = drawMatrix
    }

    fun resetZoom() {
        currentScale = 1f
        drawMatrix.set(baseMatrix)
        imageMatrix = drawMatrix
    }

    // 限制拖动范围，不让图片被拖出屏幕留黑边
    private fun fixTrans() {
        drawMatrix.getValues(values)
        val transX = values[Matrix.MTRANS_X]
        val transY = values[Matrix.MTRANS_Y]
        val scaleX = values[Matrix.MSCALE_X]
        val d = drawable ?: return
        val contentW = d.intrinsicWidth * scaleX
        val contentH = d.intrinsicHeight * scaleX

        val fixX = getFix(transX, width.toFloat(), contentW)
        val fixY = getFix(transY, height.toFloat(), contentH)
        drawMatrix.postTranslate(fixX, fixY)
    }

    private fun getFix(trans: Float, viewSize: Float, contentSize: Float): Float {
        val minTrans: Float
        val maxTrans: Float
        if (contentSize <= viewSize) {
            minTrans = (viewSize - contentSize) / 2f
            maxTrans = minTrans
        } else {
            minTrans = viewSize - contentSize
            maxTrans = 0f
        }
        if (trans < minTrans) return minTrans - trans
        if (trans > maxTrans) return maxTrans - trans
        return 0f
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
                if (currentScale > minScale) parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (currentScale > minScale && event.pointerCount == 1 && !scaleDetector.isInProgress) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    drawMatrix.postTranslate(dx, dy)
                    fixTrans()
                    imageMatrix = drawMatrix
                    lastX = event.x
                    lastY = event.y
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (currentScale <= minScale) parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }
}
