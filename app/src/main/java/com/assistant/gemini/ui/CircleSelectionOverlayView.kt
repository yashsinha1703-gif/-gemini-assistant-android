package com.assistant.gemini.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * Custom overlay canvas that replicates "Circle to Search" / Screen Selection.
 * Freezes the active screen buffer, allows the user to lasso/circle or box-select
 * any element on the display, and extracts the cropped Bitmap for OCR, Translation,
 * Google Lens Search, and Gemini multimodal analysis.
 */
class CircleSelectionOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var screenBitmap: Bitmap? = null
    private val drawPath = Path()
    private val points = mutableListOf<PointF>()
    var selectedBounds: RectF? = null
        private set

    // Vibrant Glow / Stroke Paint for drawing the circle or selection box
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4285F4") // Google Blue
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        setShadowLayer(14f, 0f, 0f, Color.parseColor("#804285F4"))
    }

    // Outline for the active bounding box
    private val boxOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF") // Cyan glow
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        setShadowLayer(16f, 0f, 0f, Color.parseColor("#9900E5FF"))
    }

    // Semi-transparent fill paint inside the circle
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3300E5FF")
        style = Paint.Style.FILL
    }

    // Scrim paint to dim unselected parts of the screen
    private val dimPaint = Paint().apply {
        color = Color.parseColor("#66000000") // Dim background scrim
    }

    // Corner handle paint
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        setShadowLayer(8f, 0f, 0f, Color.parseColor("#80000000"))
    }

    // Callback when user completes circling or selecting an area
    var onRegionSelected: ((croppedBitmap: Bitmap, bounds: RectF) -> Unit)? = null
    var onSelectionCleared: (() -> Unit)? = null

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setScreenshot(bitmap: Bitmap?) {
        this.screenBitmap = bitmap
        resetSelection()
    }

    fun resetSelection() {
        selectedBounds = null
        drawPath.reset()
        points.clear()
        invalidate()
        onSelectionCleared?.invoke()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Draw the dimmed screenshot background
        screenBitmap?.let { bmp ->
            val src = Rect(0, 0, bmp.width, bmp.height)
            val dst = Rect(0, 0, width, height)
            canvas.drawBitmap(bmp, src, dst, null)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
        }

        // 2. Draw user's drawn circle/lasso
        if (!drawPath.isEmpty) {
            canvas.drawPath(drawPath, fillPaint)
            canvas.drawPath(drawPath, strokePaint)
        }

        // 3. Draw final bounding highlight if selected
        selectedBounds?.let { bounds ->
            // Clear dimming on selected region
            screenBitmap?.let { bmp ->
                val scaleX = bmp.width.toFloat() / width.toFloat()
                val scaleY = bmp.height.toFloat() / height.toFloat()
                val srcX = (bounds.left * scaleX).toInt().coerceIn(0, bmp.width - 1)
                val srcY = (bounds.top * scaleY).toInt().coerceIn(0, bmp.height - 1)
                val srcW = ((bounds.width() * scaleX).toInt()).coerceIn(1, bmp.width - srcX)
                val srcH = ((bounds.height() * scaleY).toInt()).coerceIn(1, bmp.height - srcY)
                val srcRect = Rect(srcX, srcY, srcX + srcW, srcY + srcH)
                val dstRect = RectF(bounds)
                canvas.drawBitmap(bmp, srcRect, dstRect, null)
            }

            // Rounded box border
            canvas.drawRoundRect(bounds, 18f, 18f, boxOutlinePaint)

            // Draw 4 corner handles
            val handleRadius = 12f
            canvas.drawCircle(bounds.left, bounds.top, handleRadius, handlePaint)
            canvas.drawCircle(bounds.right, bounds.top, handleRadius, handlePaint)
            canvas.drawCircle(bounds.left, bounds.bottom, handleRadius, handlePaint)
            canvas.drawCircle(bounds.right, bounds.bottom, handleRadius, handlePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                drawPath.reset()
                points.clear()
                drawPath.moveTo(x, y)
                points.add(PointF(x, y))
                selectedBounds = null
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                drawPath.lineTo(x, y)
                points.add(PointF(x, y))
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (points.size > 2) {
                    drawPath.close()

                    // Compute bounding box around user's drawn path
                    var minX = Float.MAX_VALUE
                    var minY = Float.MAX_VALUE
                    var maxX = Float.MIN_VALUE
                    var maxY = Float.MIN_VALUE

                    for (p in points) {
                        minX = min(minX, p.x)
                        minY = min(minY, p.y)
                        maxX = max(maxX, p.x)
                        maxY = max(maxY, p.y)
                    }

                    // Enforce a minimum size in case user just tapped
                    if (maxX - minX < 60f && maxY - minY < 60f) {
                        minX -= 40f
                        minY -= 40f
                        maxX += 40f
                        maxY += 40f
                    }

                    // Add padding
                    val padding = 16f
                    val bounds = RectF(
                        max(0f, minX - padding),
                        max(0f, minY - padding),
                        min(width.toFloat(), maxX + padding),
                        min(height.toFloat(), maxY + padding)
                    )
                    selectedBounds = bounds
                    drawPath.reset()
                    invalidate()

                    // Crop region from the actual screen bitmap
                    cropSelectedBitmap(bounds)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun cropSelectedBitmap(bounds: RectF) {
        val bmp = screenBitmap ?: return
        if (width <= 0 || height <= 0) return

        val scaleX = bmp.width.toFloat() / width.toFloat()
        val scaleY = bmp.height.toFloat() / height.toFloat()

        val cropX = (bounds.left * scaleX).toInt().coerceIn(0, bmp.width - 1)
        val cropY = (bounds.top * scaleY).toInt().coerceIn(0, bmp.height - 1)
        val cropWidth = ((bounds.width() * scaleX).toInt()).coerceIn(1, bmp.width - cropX)
        val cropHeight = ((bounds.height() * scaleY).toInt()).coerceIn(1, bmp.height - cropY)

        try {
            val cropped = Bitmap.createBitmap(bmp, cropX, cropY, cropWidth, cropHeight)
            onRegionSelected?.invoke(cropped, bounds)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
