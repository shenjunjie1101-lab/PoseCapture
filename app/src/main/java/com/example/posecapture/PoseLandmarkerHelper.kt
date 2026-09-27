package com.example.posecapture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

class PoseLandmarkerHelper(
    context: Context,
    private val listener: Listener
) {
    private var poseLandmarker: PoseLandmarker? = null

    init {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_NAME)
            .setDelegate(Delegate.CPU)
            .build()

        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(0.5f)
            .setMinPosePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setResultListener(::onResult)
            .setErrorListener { listener.onError(it.message ?: "Pose detection error") }
            .build()

        poseLandmarker = PoseLandmarker.createFromOptions(context, options)
    }

    fun detect(imageProxy: ImageProxy, frontCamera: Boolean) {
        val timestamp = SystemClock.uptimeMillis()
        val width = imageProxy.width
        val height = imageProxy.height
        val rotation = imageProxy.imageInfo.rotationDegrees

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            imageProxy.planes[0].buffer.rewind()
            bitmap.copyPixelsFromBuffer(imageProxy.planes[0].buffer)
        } finally {
            imageProxy.close()
        }

        val matrix = Matrix().apply {
            postRotate(rotation.toFloat())
            if (frontCamera) postScale(-1f, 1f)
        }
        val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (transformed !== bitmap) bitmap.recycle()

        val mpImage = BitmapImageBuilder(transformed).build()
        poseLandmarker?.detectAsync(mpImage, timestamp)
    }

    private fun onResult(result: PoseLandmarkerResult, input: MPImage) {
        val latency = (SystemClock.uptimeMillis() - result.timestampMs()).coerceAtLeast(0L)
        listener.onResult(result, input.width, input.height, latency)
    }

    fun close() {
        poseLandmarker?.close()
        poseLandmarker = null
    }

    interface Listener {
        fun onResult(result: PoseLandmarkerResult, inputWidth: Int, inputHeight: Int, latencyMs: Long)
        fun onError(message: String)
    }

    companion object {
        private const val MODEL_NAME = "pose_landmarker_lite.task"
    }
}
