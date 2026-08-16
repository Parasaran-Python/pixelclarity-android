package com.pv.realesrgan.ui

import android.annotation.SuppressLint
import android.content.Context
import com.pv.realesrgan.R
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
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

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 4f * resources.displayMetrics.density
        style = Paint.Style.STROKE
    }

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38BDF8")
        style = Paint.Style.FILL
        setShadowLayer(8f, 0f, 4f, Color.parseColor("#80000000"))
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
        textSize = 12f * resources.displayMetrics.scaledDensity
        isFakeBoldText = true
    }

    private val dstRect = RectF()
    private val srcBeforeRect = Rect()
    private val srcAfterRect = Rect()

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null) // for shadow layer
    }

    fun setBitmaps(before: Bitmap?, after: Bitmap?, afterLabel: String = "Upscaled (4x HD)") {
        this.beforeBitmap = before
        this.afterBitmap = after
        this.afterLabelText = afterLabel
        splitPosition = if (after != null) 0.5f else 1.0f
        requestLayout()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val before = beforeBitmap
        val after = afterBitmap

        if (before == null && after == null) return

        // Calculate aspect-fit destination rectangle
        val refBmp = before ?: after!!
        val bmpW = refBmp.width.toFloat()
        val bmpH = refBmp.height.toFloat()

        val scale = min(w / bmpW, h / bmpH)
        val drawnW = bmpW * scale
        val drawnH = bmpH * scale
        val left = (w - drawnW) / 2f
        val top = (h - drawnH) / 2f

        dstRect.set(left, top, left + drawnW, top + drawnH)

        val splitX = left + drawnW * splitPosition

        if (before != null && (after == null || splitPosition > 0f)) {
            // Draw before bitmap (left portion)
            canvas.save()
            canvas.clipRect(left, top, if (after != null) splitX else left + drawnW, top + drawnH)
            srcBeforeRect.set(0, 0, before.width, before.height)
            canvas.drawBitmap(before, srcBeforeRect, dstRect, null)
            canvas.restore()
        }

        if (after != null && splitPosition < 1.0f) {
            // Draw after bitmap (right portion)
            canvas.save()
            canvas.clipRect(splitX, top, left + drawnW, top + drawnH)
            srcAfterRect.set(0, 0, after.width, after.height)
            canvas.drawBitmap(after, srcAfterRect, dstRect, null)
            canvas.restore()
        }

        // Draw Badges if both bitmaps are loaded
        if (before != null && after != null) {
            val badgePaddingX = 24f
            val badgePaddingY = 12f
            val badgeMargin = 24f

            // "Original" Badge (Left)
            val beforeText = context.getString(R.string.before_label)
            val beforeTextW = textPaint.measureText(beforeText)
            val beforeRect = RectF(
                left + badgeMargin,
                top + badgeMargin,
                left + badgeMargin + beforeTextW + badgePaddingX * 2,
                top + badgeMargin + textPaint.textSize + badgePaddingY * 2
            )
            canvas.drawRoundRect(beforeRect, 24f, 24f, badgeBgPaint)
            canvas.drawText(
                beforeText,
                beforeRect.left + badgePaddingX,
                beforeRect.bottom - badgePaddingY,
                textPaint
            )

            // Upscaled Badge (Right)
            val afterText = afterLabelText
            val afterTextW = textPaint.measureText(afterText)
            val afterRect = RectF(
                left + drawnW - badgeMargin - afterTextW - badgePaddingX * 2,
                top + badgeMargin,
                left + drawnW - badgeMargin,
                top + badgeMargin + textPaint.textSize + badgePaddingY * 2
            )
            canvas.drawRoundRect(afterRect, 24f, 24f, badgeBgPaint)
            canvas.drawText(
                afterText,
                afterRect.left + badgePaddingX,
                afterRect.bottom - badgePaddingY,
                textPaint
            )

            // Draw Divider Line
            canvas.drawLine(splitX, top, splitX, top + drawnH, linePaint)

            // Draw Circular Slider Handle in Center
            val handleRadius = 20f * resources.displayMetrics.density
            val handleCenterY = top + drawnH / 2f

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
        if (afterBitmap == null || beforeBitmap == null) return super.onTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val drawnW = dstRect.width()
                if (drawnW > 0) {
                    val relativeX = event.x - dstRect.left
                    splitPosition = (relativeX / drawnW).coerceIn(0f, 1f)
                    invalidate()
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
