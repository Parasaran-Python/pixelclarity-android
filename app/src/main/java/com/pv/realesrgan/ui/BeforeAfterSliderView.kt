package com.pv.realesrgan.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.pv.realesrgan.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class BeforeAfterSliderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var beforeBitmap: Bitmap? = null
    private var afterBitmap: Bitmap? = null
    private var afterLabelText: String = "Upscaled (4x HD)"

    private var splitPosition: Float = 0.5f // 0.0 to 1.0

    // Zoom & Pan state
    var zoomScale: Float = 1.0f
        private set
    private var panX: Float = 0f
    private var panY: Float = 0f

    var badgeTopOffset: Float = 0f
    var badgeBottomOffset: Float = 0f

    private val minScale = 1.0f
    private val maxScale = 8.0f

    private var isDraggingSlider = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 4f * resources.displayMetrics.density
        style = Paint.Style.STROKE
    }

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38BDF8")
        style = Paint.Style.FILL
    }

    private val handleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 2f * resources.displayMetrics.density
        style = Paint.Style.STROKE
    }

    private val handleIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 2f * resources.displayMetrics.density
        style = Paint.Style.STROKE
    }

    private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B30F172A") // 70% opacity dark
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 12f * resources.displayMetrics.density
        isFakeBoldText = true
    }

    private val dstRect = RectF()
    private val srcBeforeRect = Rect()
    private val srcAfterRect = Rect()

    private val scaleGestureDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector

    init {

        scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scaleFactor = detector.scaleFactor
                val prevScale = zoomScale
                zoomScale = (zoomScale * scaleFactor).coerceIn(minScale, maxScale)

                if (zoomScale != prevScale) {
                    val focusX = detector.focusX
                    val focusY = detector.focusY
                    panX += (focusX - (width / 2f + panX)) * (1 - zoomScale / prevScale)
                    panY += (focusY - (height / 2f + panY)) * (1 - zoomScale / prevScale)
                    clampPan()
                    invalidate()
                }
                return true
            }
        })

        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (zoomScale > 1.05f) {
                    resetZoom()
                } else {
                    zoomScale = 3.0f
                    panX = (width / 2f - e.x) * 2f
                    panY = (height / 2f - e.y) * 2f
                    clampPan()
                }
                invalidate()
                return true
            }
        })
    }

    fun setBitmaps(before: Bitmap?, after: Bitmap?, afterLabel: String = "Upscaled (4x HD)") {
        this.beforeBitmap = before
        this.afterBitmap = after
        this.afterLabelText = afterLabel
        splitPosition = if (after != null) 0.5f else 1.0f
        resetZoom()
        requestLayout()
        invalidate()
    }

    fun resetZoom() {
        zoomScale = 1.0f
        panX = 0f
        panY = 0f
        invalidate()
    }

    private fun clampPan() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val maxPanX = max(0f, (w * zoomScale - w) / 2f)
        val maxPanY = max(0f, (h * zoomScale - h) / 2f)

        panX = panX.coerceIn(-maxPanX, maxPanX)
        panY = panY.coerceIn(-maxPanY, maxPanY)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredW = beforeBitmap?.width ?: afterBitmap?.width ?: suggestedMinimumWidth
        val desiredH = beforeBitmap?.height ?: afterBitmap?.height ?: suggestedMinimumHeight

        val w = resolveSize(desiredW, widthMeasureSpec)
        val h = resolveSize(desiredH, heightMeasureSpec)
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val before = beforeBitmap
        val after = afterBitmap

        if (before == null && after == null) return

        val refBmp = before ?: after!!
        val bmpW = refBmp.width.toFloat()
        val bmpH = refBmp.height.toFloat()

        val fitScale = min(w / bmpW, h / bmpH)
        val baseDrawnW = bmpW * fitScale
        val baseDrawnH = bmpH * fitScale
        val baseLeft = (w - baseDrawnW) / 2f
        val baseTop = (h - baseDrawnH) / 2f

        dstRect.set(baseLeft, baseTop, baseLeft + baseDrawnW, baseTop + baseDrawnH)

        val splitX = w * splitPosition

        // 1. Draw Before and After Bitmaps with Zoom & Pan Transformations
        canvas.save()
        canvas.clipRect(0f, 0f, w, h)

        if (before != null && (after == null || splitPosition > 0f)) {
            canvas.save()
            canvas.clipRect(0f, 0f, if (after != null) splitX else w, h)
            canvas.translate(w / 2f + panX, h / 2f + panY)
            canvas.scale(zoomScale, zoomScale)
            canvas.translate(-w / 2f, -h / 2f)

            srcBeforeRect.set(0, 0, before.width, before.height)
            canvas.drawBitmap(before, srcBeforeRect, dstRect, null)
            canvas.restore()
        }

        if (after != null && splitPosition < 1.0f) {
            canvas.save()
            canvas.clipRect(splitX, 0f, w, h)
            canvas.translate(w / 2f + panX, h / 2f + panY)
            canvas.scale(zoomScale, zoomScale)
            canvas.translate(-w / 2f, -h / 2f)

            srcAfterRect.set(0, 0, after.width, after.height)
            canvas.drawBitmap(after, srcAfterRect, dstRect, null)
            canvas.restore()
        }
        canvas.restore()

        // 2. Draw Badges & Overlay Controls in Screen Space
        val density = resources.displayMetrics.density
        val badgePaddingX = 10f * density
        val badgePaddingY = 6f * density
        val badgeMargin = 12f * density
        val badgeCornerRadius = 8f * density
        val topMargin = badgeMargin + badgeTopOffset
        val bottomMargin = badgeMargin + badgeBottomOffset

        // "Original" Badge (Top-Left)
        if (before != null && after != null && splitPosition > 0.15f) {
            val beforeText = context.getString(R.string.before_label)
            val beforeTextW = textPaint.measureText(beforeText)
            val beforeRect = RectF(
                badgeMargin,
                topMargin,
                badgeMargin + beforeTextW + badgePaddingX * 2,
                topMargin + textPaint.textSize + badgePaddingY * 2
            )
            canvas.drawRoundRect(beforeRect, badgeCornerRadius, badgeCornerRadius, badgeBgPaint)
            canvas.drawText(
                beforeText,
                beforeRect.left + badgePaddingX,
                beforeRect.bottom - badgePaddingY,
                textPaint
            )
        }

        // "Upscaled" Badge (Top-Right)
        if (after != null && splitPosition < 0.85f) {
            val afterText = afterLabelText
            val afterTextW = textPaint.measureText(afterText)
            val afterRect = RectF(
                w - badgeMargin - afterTextW - badgePaddingX * 2,
                topMargin,
                w - badgeMargin,
                topMargin + textPaint.textSize + badgePaddingY * 2
            )
            canvas.drawRoundRect(afterRect, badgeCornerRadius, badgeCornerRadius, badgeBgPaint)
            canvas.drawText(
                afterText,
                afterRect.left + badgePaddingX,
                afterRect.bottom - badgePaddingY,
                textPaint
            )
        }

        // Zoom Indicator Badge (Bottom-Right if zoomed in)
        if (zoomScale > 1.05f) {
            val zoomText = String.format("🔍 %.1fx (Double-tap to reset)", zoomScale)
            val zoomTextW = textPaint.measureText(zoomText)
            val zoomRect = RectF(
                w - badgeMargin - zoomTextW - badgePaddingX * 2,
                h - bottomMargin - textPaint.textSize - badgePaddingY * 2,
                w - badgeMargin,
                h - bottomMargin
            )
            canvas.drawRoundRect(zoomRect, badgeCornerRadius, badgeCornerRadius, badgeBgPaint)
            canvas.drawText(
                zoomText,
                zoomRect.left + badgePaddingX,
                zoomRect.bottom - badgePaddingY,
                textPaint
            )
        }

        // 3. Draw Split Line and Center Handle
        if (before != null && after != null) {
            canvas.drawLine(splitX, 0f, splitX, h, linePaint)

            val handleRadius = 20f * resources.displayMetrics.density
            val handleCenterY = h / 2f

            canvas.drawCircle(splitX, handleCenterY, handleRadius, handlePaint)
            canvas.drawCircle(splitX, handleCenterY, handleRadius, handleBorderPaint)

            // Draw left-right arrows inside handle
            val arrowOffset = 8f * resources.displayMetrics.density
            val arrowSize = 6f * resources.displayMetrics.density

            // Left arrow
            canvas.drawLine(splitX - arrowOffset, handleCenterY, splitX - arrowOffset + arrowSize, handleCenterY - arrowSize, handleIconPaint)
            canvas.drawLine(splitX - arrowOffset, handleCenterY, splitX - arrowOffset + arrowSize, handleCenterY + arrowSize, handleIconPaint)

            // Right arrow
            canvas.drawLine(splitX + arrowOffset, handleCenterY, splitX + arrowOffset - arrowSize, handleCenterY - arrowSize, handleIconPaint)
            canvas.drawLine(splitX + arrowOffset, handleCenterY, splitX + arrowOffset - arrowSize, handleCenterY + arrowSize, handleIconPaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (beforeBitmap == null && afterBitmap == null) return super.onTouchEvent(event)

        gestureDetector.onTouchEvent(event)
        scaleGestureDetector.onTouchEvent(event)

        val splitScreenX = width * splitPosition
        val handleTouchTolerance = 48f * resources.displayMetrics.density

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y

                isDraggingSlider = afterBitmap != null && abs(event.x - splitScreenX) <= handleTouchTolerance
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (scaleGestureDetector.isInProgress) {
                    return true
                }

                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY

                if (isDraggingSlider) {
                    splitPosition = (event.x / width.toFloat()).coerceIn(0f, 1f)
                    invalidate()
                } else if (zoomScale > 1.05f) {
                    panX += dx
                    panY += dy
                    clampPan()
                    invalidate()
                } else if (afterBitmap != null) {
                    // At 1x zoom, dragging anywhere moves the slider
                    splitPosition = (event.x / width.toFloat()).coerceIn(0f, 1f)
                    invalidate()
                }

                lastTouchX = event.x
                lastTouchY = event.y
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDraggingSlider = false
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
