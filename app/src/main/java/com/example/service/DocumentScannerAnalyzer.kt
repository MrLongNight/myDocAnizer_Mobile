package com.example.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

class DocumentScannerAnalyzer(
    private val detector: TfliteDocumentCornerDetector,
    private val autoCapture: AutoCaptureController,
    private val onStatusUpdate: (TrackerStatus) -> Unit
) : ImageAnalysis.Analyzer {
    private val targetBitmap = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(targetBitmap)
    private val transformMatrix = Matrix()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var sourceBitmap: Bitmap? = null
    private var sourcePixels: IntArray? = null
    private var sourceWidth = 0
    private var sourceHeight = 0

    override fun analyze(imageProxy: ImageProxy) {
        try {
            val srcBitmap = rgbaImageToReusableBitmap(imageProxy)
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            transformMatrix.reset()
            transformMatrix.postTranslate(-srcBitmap.width / 2f, -srcBitmap.height / 2f)
            transformMatrix.postRotate(rotationDegrees.toFloat())
            val rotW = if (rotationDegrees % 180 != 0) srcBitmap.height else srcBitmap.width
            val rotH = if (rotationDegrees % 180 != 0) srcBitmap.width else srcBitmap.height
            transformMatrix.postScale(224f / rotW, 224f / rotH)
            transformMatrix.postTranslate(112f, 112f)
            canvas.drawBitmap(srcBitmap, transformMatrix, paint)
            val rawQuad = detector.detect(targetBitmap)
            val status = autoCapture.processFrame(rawQuad, imageProxy.imageInfo.timestamp)
            onStatusUpdate(status)
        } finally {
            imageProxy.close()
        }
    }

    private fun rgbaImageToReusableBitmap(imageProxy: ImageProxy): Bitmap {
        val width = imageProxy.width
        val height = imageProxy.height
        if (sourceBitmap == null || sourceWidth != width || sourceHeight != height) {
            sourceBitmap?.recycle()
            sourceBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            sourcePixels = IntArray(width * height)
            sourceWidth = width
            sourceHeight = height
        }
        val plane = imageProxy.planes[0]
        val buffer = plane.buffer
        val pixels = sourcePixels!!
        for (y in 0 until height) {
            val rowStart = y * plane.rowStride
            val outRow = y * width
            for (x in 0 until width) {
                val index = rowStart + x * plane.pixelStride
                val a = buffer.get(index).toInt() and 0xff
                val r = buffer.get(index + 1).toInt() and 0xff
                val g = buffer.get(index + 2).toInt() and 0xff
                val b = buffer.get(index + 3).toInt() and 0xff
                pixels[outRow + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        sourceBitmap!!.setPixels(pixels, 0, width, 0, 0, width, height)
        return sourceBitmap!!
    }
}
