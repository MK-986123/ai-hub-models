package com.qualcomm.aihub.diffusionlab.engine.drawing

import com.qualcomm.aihub.diffusionlab.domain.model.EdgeMode

/**
 * Configuration for the mask painting brush.
 * All values are adjustable via sliders on the Edit screen.
 */
data class BrushConfig(
    /** Brush radius in pixels (1-100). Maps to stroke width. */
    val sizePx: Int = 24,

    /** Feather amount (0-100%). Controls Gaussian blur radius on mask edges. */
    val featherPercent: Float = 0f,

    /** Edge hardness mode. Controls how mask transitions are thresholded. */
    val edgeMode: EdgeMode = EdgeMode.SHARP,

    /** Denoise strength (0-1). Blending factor at inpainted boundaries. */
    val denoiseStrength: Float = 0.3f,

    /** Whether eraser mode is active (S-Pen button toggles this). */
    val isEraser: Boolean = false,

    /** Opacity based on stylus pressure (0-1). 1.0 = no pressure sensitivity. */
    val pressureOpacity: Float = 1f,
)
