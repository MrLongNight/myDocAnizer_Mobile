package com.example.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.camera.core.AspectRatio
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.toBitmap

/**
 * CameraX analyzer for the LiteRT document-corner model.
 *
 * Configure the ImageAnalysis use case with OUTPUT_IMAGE_FORMAT_RGBA_8888,
 * STRATEGY_KEEP_ONLY_LATEST, and a 4:3 aspect-ratio strategy before attaching
 * this analyzer. The target bitmap, canvas, matrix, and paint are reused.
 */
class DocumentScannerAnalyzer(
    private val detector: TfliteDocumentCornerDetector,
    private val autoCapture: AutoCaptureController,
    private val onStatusUpdate: (TrackerStatus) -> Unit
) : ImageAnalysis.Analyzer {
    private val targetBitmap = Bitmap.createBitmap(TARGET_SIZE, TARGET_SIZE, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(targetBitmap)
    private val transformMatrix = Matrix()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    override fun analyze(imageProxy: ImageProxy) {
        try {
            val srcBitmap = imageProxy.toBitmap()
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            transformMatrix.reset()
            transformMatrix.postTranslate(-srcBitmap.width / 2f, -srcBitmap.height / 2f)
            transformMatrix.postRotate(rotationDegrees.toFloat())
            val rotW = if (rotationDegrees % 180 != 0) srcBitmap.height else srcBitmap.width
            val rotH = if (rotationDegrees % 180 != 0) srcBitmap.width else srcBitmap.height
            transformMatrix.postScale(TARGET_SIZE / rotW.toFloat(), TARGET_SIZE / rotH.toFloat())
            transformMatrix.postTranslate(TARGET_SIZE / 2f, TARGET_SIZE / 2f)

            canvas.drawBitmap(srcBitmap, transformMatrix, paint)
            if (srcBitmap !== targetBitmap && !srcBitmap.isRecycled) {
                srcBitmap.recycle()
            }

            val rawQuad = detector.detect(targetBitmap)
            val status = autoCapture.processFrame(rawQuad, imageProxy.imageInfo.timestamp)
            onStatusUpdate(status)
        } finally {
            imageProxy.close()
        }
    }

    private companion object {
        const val TARGET_SIZE = 224
    }
}
