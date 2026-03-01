package com.qualcomm.aihub.diffusionlab.engine.drawing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.view.MotionEvent

/**
 * Renders strokes from S-Pen / stylus / finger input into a binary mask bitmap.
 *
 * The mask is maintained at the model's input resolution (512×512) regardless
 * of the display canvas size. Coordinate mapping is handled transparently.
 *
 * Features:
 * - Pressure-sensitive stroke width (S-Pen)
 * - Tilt-to-width mapping (S-Pen angled = wider strokes)
 * - Button press toggles eraser mode
 * - Undo/redo stack
 */
class MaskRenderer(
    private val maskWidth: Int = 512,
    private val maskHeight: Int = 512,
) {
    private var maskBitmap: Bitmap = Bitmap.createBitmap(
        maskWidth, maskHeight, Bitmap.Config.ARGB_8888
    )
    private var maskCanvas: Canvas = Canvas(maskBitmap)

    private val drawPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    private val erasePaint = Paint().apply {
        color = Color.TRANSPARENT
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        isAntiAlias = true
    }

    private val currentPath = Path()
    private val undoStack = mutableListOf<Bitmap>()
    private val redoStack = mutableListOf<Bitmap>()

    private var displayWidth: Float = 1f
    private var displayHeight: Float = 1f

    /**
     * Updates the display size for coordinate mapping.
     */
    fun setDisplaySize(width: Float, height: Float) {
        displayWidth = width
        displayHeight = height
    }

    /**
     * Processes a MotionEvent from the drawing canvas.
     * Maps display coordinates to mask coordinates.
     *
     * S-Pen specifics:
     * - pressure: Maps to stroke opacity (0-1)
     * - tilt: Maps to extra stroke width
     * - buttonState: Primary button = eraser toggle
     */
    fun onTouchEvent(event: MotionEvent, config: BrushConfig): Boolean {
        val scaleX = maskWidth / displayWidth
        val scaleY = maskHeight / displayHeight
        val x = event.x * scaleX
        val y = event.y * scaleY

        // S-Pen pressure sensitivity
        val pressure = event.pressure.coerceIn(0f, 1f)
        val tiltFactor = if (event.getAxisValue(MotionEvent.AXIS_TILT) > 0.3f) 1.5f else 1f

        val strokeWidth = config.sizePx * pressure * tiltFactor
        val paint = if (config.isEraser) erasePaint else drawPaint
        paint.strokeWidth = strokeWidth
        paint.alpha = (pressure * config.pressureOpacity * 255).toInt()

        // Check S-Pen button state for eraser toggle
        val isSPenButton = event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0

        val activePaint = if (isSPenButton || config.isEraser) erasePaint else drawPaint
        activePaint.strokeWidth = strokeWidth

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                saveUndoState()
                currentPath.reset()
                currentPath.moveTo(x, y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                // Process historical points for smooth strokes
                for (i in 0 until event.historySize) {
                    val hx = event.getHistoricalX(i) * scaleX
                    val hy = event.getHistoricalY(i) * scaleY
                    currentPath.lineTo(hx, hy)
                }
                currentPath.lineTo(x, y)
                maskCanvas.drawPath(currentPath, activePaint)
                currentPath.reset()
                currentPath.moveTo(x, y)
                return true
            }
            MotionEvent.ACTION_UP -> {
                currentPath.lineTo(x, y)
                maskCanvas.drawPath(currentPath, activePaint)
                currentPath.reset()
                redoStack.clear()
                return true
            }
        }
        return false
    }

    /**
     * Returns the current mask as a Bitmap (white = masked area).
     */
    fun getMaskBitmap(): Bitmap = maskBitmap.copy(Bitmap.Config.ARGB_8888, false)

    /**
     * Returns a semi-transparent overlay for display purposes.
     * Red-tinted mask overlay on top of the image.
     */
    fun getOverlayBitmap(): Bitmap {
        val overlay = Bitmap.createBitmap(maskWidth, maskHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(overlay)
        val paint = Paint().apply {
            color = Color.argb(128, 255, 0, 0) // Semi-transparent red
        }

        val pixels = IntArray(maskWidth * maskHeight)
        maskBitmap.getPixels(pixels, 0, maskWidth, 0, 0, maskWidth, maskHeight)
        for (i in pixels.indices) {
            if (pixels[i] and 0xFF > 128) {
                overlay.setPixel(i % maskWidth, i / maskWidth, paint.color)
            }
        }
        return overlay
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            redoStack.add(maskBitmap.copy(Bitmap.Config.ARGB_8888, true))
            maskBitmap = undoStack.removeAt(undoStack.lastIndex)
            maskCanvas = Canvas(maskBitmap)
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            undoStack.add(maskBitmap.copy(Bitmap.Config.ARGB_8888, true))
            maskBitmap = redoStack.removeAt(redoStack.lastIndex)
            maskCanvas = Canvas(maskBitmap)
        }
    }

    fun clear() {
        saveUndoState()
        maskBitmap.eraseColor(Color.TRANSPARENT)
    }

    private fun saveUndoState() {
        // Limit undo stack to 20 states to conserve memory
        if (undoStack.size >= 20) {
            undoStack.removeAt(0).recycle()
        }
        undoStack.add(maskBitmap.copy(Bitmap.Config.ARGB_8888, true))
    }
}
