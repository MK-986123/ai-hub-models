package com.qualcomm.aihub.diffusionlab.ui.components

import android.graphics.Bitmap
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.qualcomm.aihub.diffusionlab.engine.drawing.BrushConfig
import com.qualcomm.aihub.diffusionlab.engine.drawing.MaskRenderer

/**
 * Composable wrapper for the mask drawing canvas.
 *
 * Uses AndroidView to integrate the MaskRenderer with Compose.
 * This is necessary because stylus events (pressure, tilt, button state)
 * require direct MotionEvent handling, which Compose doesn't fully support
 * for advanced stylus APIs.
 *
 * Displays the source image as background with a semi-transparent red overlay
 * showing the current mask. Drawing adds to the mask; S-Pen button erases.
 */
@Composable
fun DrawingCanvas(
    sourceImage: Bitmap?,
    brushConfig: BrushConfig,
    maskRenderer: MaskRenderer,
    onMaskChanged: (Bitmap) -> Unit,
    modifier: Modifier = Modifier,
) {
    val overlayView = remember { mutableListOf<ImageView>() }

    Box(modifier = modifier) {
        AndroidView(
            factory = { context ->
                val imageView = ImageView(context).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    sourceImage?.let { setImageBitmap(it) }
                }

                val overlay = ImageView(context).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    alpha = 0.5f
                }
                overlayView.add(overlay)

                val container = android.widget.FrameLayout(context).apply {
                    addView(imageView, android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    ))
                    addView(overlay, android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    ))
                }

                container.setOnTouchListener(object : View.OnTouchListener {
                    override fun onTouch(v: View, event: MotionEvent): Boolean {
                        maskRenderer.setDisplaySize(
                            v.width.toFloat(),
                            v.height.toFloat(),
                        )
                        val handled = maskRenderer.onTouchEvent(event, brushConfig)
                        if (handled) {
                            overlay.setImageBitmap(maskRenderer.getOverlayBitmap())
                            if (event.actionMasked == MotionEvent.ACTION_UP) {
                                onMaskChanged(maskRenderer.getMaskBitmap())
                            }
                        }
                        return handled
                    }
                })

                container
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
