package com.example.ui.components

import android.graphics.PointF
import androidx.compose.ui.geometry.Offset
import kotlin.math.min

/**
 * Fit-center viewport for normalized image coordinates (origin at image top-left).
 * Bounds exclude the letterboxed bars around the displayed image.
 */
data class ImageViewportBounds(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
) {
    fun normToScreen(pt: PointF): Offset =
        Offset(left + pt.x * width, top + pt.y * height)

    fun screenToNorm(offset: Offset): PointF = PointF(
        ((offset.x - left) / width).coerceIn(0f, 1f),
        ((offset.y - top) / height).coerceIn(0f, 1f)
    )

    companion object {
        fun computeFitCenterBounds(
            containerWidth: Float,
            containerHeight: Float,
            imageAspectRatio: Float = 3f / 4f
        ): ImageViewportBounds {
            if (containerWidth <= 0f || containerHeight <= 0f ||
                imageAspectRatio <= 0f || !imageAspectRatio.isFinite()
            ) {
                return ImageViewportBounds(0f, 0f, 0f, 0f)
            }
            val containerAspect = containerWidth / containerHeight
            val width: Float
            val height: Float
            if (containerAspect > imageAspectRatio) {
                height = containerHeight
                width = height * imageAspectRatio
            } else {
                width = containerWidth
                height = width / imageAspectRatio
            }
            return ImageViewportBounds(
                left = (containerWidth - width) / 2f,
                top = (containerHeight - height) / 2f,
                width = width,
                height = height
            )
        }
    }
}
