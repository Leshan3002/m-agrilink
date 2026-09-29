package com.example.m_agrilink.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.exp

/**
 * On-device leaf vision pipeline for the CameraX field scanner.
 *
 * Tries the bundled [MODEL_ASSET] first; when the asset is missing or the
 * tensor shapes mismatch, it falls back to a deterministic offline color
 * heuristic so the scanner never crashes and always returns a diagnosis.
 *
 * Expected model contract:
 *  - Input : float32 [1, 224, 224, 3], RGB normalized to [-1, 1]
 *  - Output: float32 [1, 4] over [CLASSES]
 */
data class LeafDiagnosis(
    val label: String,
    val confidence: Float,
    val source: String
)

enum class FrameRejectReason { OK, TOO_DARK, TOO_BRIGHT, BLURRY, NO_LEAF, LOW_CONFIDENCE }

data class FrameQuality(
    val usable: Boolean,
    val reason: FrameRejectReason,
    val brightness01: Float,
    val greenRatio: Float,
    val sharpness: Float,
    val guidance: String
)

data class SmartDiagnosis(
    val diagnosis: LeafDiagnosis,
    val quality: FrameQuality
)

object TfliteLeafAnalyzer {

    const val MODEL_ASSET = "plant_disease_model.tflite"
    const val CABI_URL = "https://plantwiseplusknowledgebank.org/DoSearch?query=Spodoptera%20frugiperda"

    private const val INPUT_SIZE = 224

    val CLASSES = arrayOf(
        "Healthy",
        "Fall Armyworm damage",
        "Gray Leaf Spot",
        "Maize Lethal Necrosis"
    )

    /** Loads the interpreter from assets, or null when the model file is absent. */
    fun loadInterpreter(context: Context): Interpreter? {
        return try {
            context.assets.openFd(MODEL_ASSET).use { fd ->
                FileInputStream(fd.fileDescriptor).channel.use { channel ->
                    val mapped = channel.map(
                        FileChannel.MapMode.READ_ONLY,
                        fd.startOffset,
                        fd.declaredLength
                    )
                    Interpreter(mapped)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Thread-safe YUV_420_888 -> Bitmap conversion for the analyzer loop. */
    fun imageProxyToBitmap(proxy: ImageProxy): Bitmap? {
        return try {
            val image = proxy.image ?: return null
            if (image.format != ImageFormat.YUV_420_888) return null
            val width = image.width
            val height = image.height
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer
            val yRowStride = image.planes[0].rowStride
            val uvRowStride = image.planes[1].rowStride
            val uvPixelStride = image.planes[1].pixelStride

            val nv21 = ByteArray(width * height + 2 * (width / 2) * (height / 2))
            for (row in 0 until height) {
                for (col in 0 until width) {
                    nv21[row * width + col] = yBuffer.get(row * yRowStride + col)
                }
            }
            var offset = width * height
            for (row in 0 until height / 2) {
                for (col in 0 until width / 2) {
                    val vuPos = row * uvRowStride + col * uvPixelStride
                    nv21[offset++] = vBuffer.get(vuPos)
                    nv21[offset++] = uBuffer.get(vuPos)
                }
            }
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val out = ByteArrayOutputStream()
            if (!yuvImage.compressToJpeg(Rect(0, 0, width, height), 90, out)) return null
            val bytes = out.toByteArray()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }

    /** Runs TFLite when available, otherwise the offline heuristic fallback. */
    fun classify(bitmap: Bitmap, interpreter: Interpreter?): LeafDiagnosis {
        return tryRunTflite(bitmap, interpreter) ?: heuristic(bitmap)
    }

    /**
     * Smart gate: rejects dark / washed-out / blurry / non-leaf frames BEFORE
     * any disease label is produced, so a dark image can never return
     * "Fall Armyworm" advisories. Returns explicit lens/crop guidance instead.
     */
    fun assessQuality(bitmap: Bitmap): FrameQuality {
        return try {
            val w = 48
            val h = 48
            val small = Bitmap.createScaledBitmap(bitmap, w, h, true)
            val pixels = IntArray(w * h)
            small.getPixels(pixels, 0, w, 0, 0, w, h)
            var rSum = 0L
            var gSum = 0L
            var bSum = 0L
            var lumSum = 0.0
            var darkCount = 0
            var brightCount = 0
            val lums = DoubleArray(pixels.size)
            for (i in pixels.indices) {
                val p = pixels[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                rSum += r
                gSum += g
                bSum += b
                val lum = 0.299 * r + 0.587 * g + 0.114 * b
                lums[i] = lum
                lumSum += lum
                if (lum < 30.0) darkCount++
                if (lum > 225.0) brightCount++
            }
            val n = pixels.size.toDouble()
            val mean = lumSum / n
            var variance = 0.0
            for (lum in lums) variance += (lum - mean) * (lum - mean)
            variance /= n
            val stddev = kotlin.math.sqrt(variance)
            val rf = rSum / n / 255.0
            val gf = gSum / n / 255.0
            val bf = bSum / n / 255.0
            val greenRatio = (gf / (rf + gf + bf + 1e-6)).toFloat()
            val brightness01 = (mean / 255.0).toFloat().coerceIn(0f, 1f)
            val darkFraction = darkCount / n
            val brightFraction = brightCount / n
            // Fruit-friendly: ripe produce (mango, tomato, banana) is red/yellow,
            // not green — accept vivid non-green crops when sharp and well lit.
            val produceLike = (rf > 0.32 && rf >= gf * 0.85 && bf < rf) ||
                (rf + gf > 0.75 && bf < 0.35)
            val cropLike = greenRatio >= 0.26 || produceLike
            when {
                darkFraction > 0.72 || mean < 32.0 -> FrameQuality(
                    usable = false,
                    reason = FrameRejectReason.TOO_DARK,
                    brightness01 = brightness01,
                    greenRatio = greenRatio,
                    sharpness = stddev.toFloat(),
                    guidance = "⚠️ Scanner Alert: Frame too dark — no crop detail visible. Clean the lens, turn on the light/flash, and point steady at the crop (leaf, fruit, or stem) filling the frame, then retry."
                )
                brightFraction > 0.72 || mean > 228.0 -> FrameQuality(
                    usable = false,
                    reason = FrameRejectReason.TOO_BRIGHT,
                    brightness01 = brightness01,
                    greenRatio = greenRatio,
                    sharpness = stddev.toFloat(),
                    guidance = "⚠️ Scanner Alert: Frame washed out by glare. Shade the crop, wipe the lens, hold steady and retry."
                )
                stddev < 11.0 -> FrameQuality(
                    usable = false,
                    reason = FrameRejectReason.BLURRY,
                    brightness01 = brightness01,
                    greenRatio = greenRatio,
                    sharpness = stddev.toFloat(),
                    guidance = "⚠️ Scanner Alert: Blurry or covered lens — hold the phone steady, tap to focus, clean the lens, and make the crop fill the frame."
                )
                !cropLike -> FrameQuality(
                    usable = false,
                    reason = FrameRejectReason.NO_LEAF,
                    brightness01 = brightness01,
                    greenRatio = greenRatio,
                    sharpness = stddev.toFloat(),
                    guidance = "⚠️ Scanner Alert: No crop detected. Point the camera at a crop leaf, fruit, or stem so it fills the frame — not a dark room, wall, or empty background."
                )
                else -> FrameQuality(
                    usable = true,
                    reason = FrameRejectReason.OK,
                    brightness01 = brightness01,
                    greenRatio = greenRatio,
                    sharpness = stddev.toFloat(),
                    guidance = ""
                )
            }
        } catch (e: Exception) {
            FrameQuality(
                usable = false,
                reason = FrameRejectReason.BLURRY,
                brightness01 = 0f,
                greenRatio = 0f,
                sharpness = 0f,
                guidance = "⚠️ Scanner Alert: Leaf frame parsing failed due to suboptimal lighting conditions or hardware focus latency. Please steady your Lenovo camera device and try again."
            )
        }
    }

    /** Quality-gated classification: invalid frames never produce disease advisories. */
    fun classifySmart(bitmap: Bitmap, interpreter: Interpreter?): SmartDiagnosis {
        val quality = assessQuality(bitmap)
        if (!quality.usable) {
            return SmartDiagnosis(
                LeafDiagnosis("Invalid frame — retake", 0f, "quality gate"),
                quality
            )
        }
        val result = tryRunTflite(bitmap, interpreter) ?: heuristic(bitmap)
        if (result.confidence < 0.55f) {
            val gated = quality.copy(
                usable = false,
                reason = FrameRejectReason.LOW_CONFIDENCE,
                guidance = "⚠️ Scanner Alert: Scan unclear (low confidence ${(result.confidence * 100).toInt()}%). Clean the lens, improve lighting, move closer so the leaf fills the frame, and retry."
            )
            return SmartDiagnosis(
                LeafDiagnosis("Uncertain — retake", result.confidence, result.source),
                gated
            )
        }
        return SmartDiagnosis(result, quality)
    }

    private fun tryRunTflite(bitmap: Bitmap, interpreter: Interpreter?): LeafDiagnosis? {
        if (interpreter == null) return null
        return try {
            val side = minOf(bitmap.width, bitmap.height)
            val cropped = Bitmap.createBitmap(
                bitmap,
                (bitmap.width - side) / 2,
                (bitmap.height - side) / 2,
                side,
                side
            )
            val scaled = Bitmap.createScaledBitmap(cropped, INPUT_SIZE, INPUT_SIZE, true)
            val input = ByteBuffer
                .allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3)
                .order(ByteOrder.nativeOrder())
            val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
            scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
            for (p in pixels) {
                input.putFloat((((p shr 16) and 0xFF) - 127.5f) / 127.5f)
                input.putFloat((((p shr 8) and 0xFF) - 127.5f) / 127.5f)
                input.putFloat(((p and 0xFF) - 127.5f) / 127.5f)
            }
            input.rewind()
            val output = Array(1) { FloatArray(CLASSES.size) }
            interpreter.run(input, output)
            val scores = output[0]
            var best = 0
            for (i in 1 until scores.size) {
                if (scores[i] > scores[best]) best = i
            }
            val max = scores[best]
            var sum = 0.0
            for (s in scores) sum += exp((s - max).toDouble())
            val confidence = (exp(0.0) / sum).toFloat().coerceIn(0f, 1f)
            LeafDiagnosis(CLASSES[best], confidence, "on-device TFLite")
        } catch (e: Exception) {
            null
        }
    }

    private fun heuristic(bitmap: Bitmap): LeafDiagnosis {
        return try {
            val small = Bitmap.createScaledBitmap(bitmap, 48, 48, true)
            val pixels = IntArray(48 * 48)
            small.getPixels(pixels, 0, 48, 0, 0, 48, 48)
            var rSum = 0L
            var gSum = 0L
            var bSum = 0L
            for (p in pixels) {
                rSum += (p shr 16) and 0xFF
                gSum += (p shr 8) and 0xFF
                bSum += p and 0xFF
            }
            val n = pixels.size.toFloat()
            val rf = rSum / n / 255f
            val gf = gSum / n / 255f
            val bf = bSum / n / 255f
            val greenRatio = gf / (rf + gf + bf)
            val fallback = "offline heuristic (tflite asset missing)"
            when {
                greenRatio >= 0.40f -> LeafDiagnosis(
                    "Healthy",
                    (0.60f + (greenRatio - 0.40f) * 1.5f).coerceIn(0.60f, 0.88f),
                    fallback
                )
                rf >= gf && rf > bf -> LeafDiagnosis("Fall Armyworm damage", 0.66f, fallback)
                gf >= rf && (rf + gf) > bf * 1.6f -> LeafDiagnosis("Gray Leaf Spot", 0.64f, fallback)
                else -> LeafDiagnosis("Fall Armyworm damage", 0.60f, fallback)
            }
        } catch (e: Exception) {
            LeafDiagnosis("Scan unclear — retake leaf photo", 0.50f, "offline heuristic")
        }
    }
}
