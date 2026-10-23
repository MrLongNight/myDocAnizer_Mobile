package com.example.service

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Runs the bundled DocCornerNet LiteRT model on a 224x224 RGB bitmap.
 * Input storage and output/pixel arrays are reused for every inference.
 */
class TfliteDocumentCornerDetector(context: Context) : Closeable {
    private val inputBuffer = ByteBuffer
        .allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4)
        .order(ByteOrder.nativeOrder())
    private val outputArray = Array(1) { FloatArray(9) }
    private val pixelArray = IntArray(INPUT_SIZE * INPUT_SIZE)
    private val interpreter: Interpreter = createInterpreter(context)

    /**
     * [bitmap224] must already be rotated and scaled to exactly 224x224.
     */
    fun detect(bitmap224: Bitmap): NormalizedQuad? {
        require(bitmap224.width == INPUT_SIZE && bitmap224.height == INPUT_SIZE) {
            "Expected a 224x224 bitmap, got ${bitmap224.width}x${bitmap224.height}"
        }

        bitmap224.getPixels(pixelArray, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        inputBuffer.rewind()
        for (i in 0 until INPUT_SIZE * INPUT_SIZE) {
            val px = pixelArray[i]
            inputBuffer.putFloat(((px shr 16) and 0xFF) / 255.0f)
            inputBuffer.putFloat(((px shr 8) and 0xFF) / 255.0f)
            inputBuffer.putFloat((px and 0xFF) / 255.0f)
        }
        inputBuffer.rewind()
        interpreter.run(inputBuffer, outputArray)

        val result = outputArray[0]
        val score = result[8]
        if (!score.isFinite() || score < MIN_PRESENCE_SCORE) return null
        for (i in 0 until 8) {
            if (!result[i].isFinite() || result[i] !in 0.0f..1.0f) return null
        }

        val quad = NormalizedQuad.fromFloatArray(result, score).orderClockwise()
        return quad.takeIf { it.isConvex() && it.area() > 0f }
    }

    override fun close() {
        interpreter.close()
    }

    private companion object {
        const val MODEL_ASSET = "doc_corner_net.tflite"
        const val INPUT_SIZE = 224
        const val MIN_PRESENCE_SCORE = 0.65f

        fun createInterpreter(context: Context): Interpreter {
            val options = Interpreter.Options().apply {
                setNumThreads(4)
                setUseXNNPACK(true)
            }
            val asset = context.assets.openFd(MODEL_ASSET)
            try {
                FileInputStream(asset.fileDescriptor).use { stream ->
                    val modelBuffer = stream.channel.map(
                        FileChannel.MapMode.READ_ONLY,
                        asset.startOffset,
                        asset.declaredLength
                    )
                    return Interpreter(modelBuffer, options)
                }
            } finally {
                asset.close()
            }
        }
    }
}
