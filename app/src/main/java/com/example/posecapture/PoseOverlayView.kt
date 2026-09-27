package com.example.posecapture

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.max

class PoseOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xE6FFFFFF.toInt()
        strokeWidth = dp(3f)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xDD000000.toInt()
        style = Paint.Style.FILL
    }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    private var inputWidth = 1
    private var inputHeight = 1
    private var points: MutableList<PointF>? = null
    private var enabled = true

    fun setSkeletonEnabled(value: Boolean) {
        enabled = value
        invalidate()
    }
    fun isSkeletonEnabled(): Boolean = enabled

    fun setResult(result: PoseLandmarkerResult, imageWidth: Int, imageHeight: Int) {
        inputWidth = max(1, imageWidth)
        inputHeight = max(1, imageHeight)
        val pose = result.landmarks().firstOrNull()
        if (pose == null || pose.isEmpty()) {
            points = null
            postInvalidateOnAnimation()
            return
        }
        val old = points
        points = MutableList(pose.size) { i ->
            val x = pose[i].x()
            val y = pose[i].y()
            if (old != null && i < old.size) {
                PointF(old[i].x * 0.55f + x * 0.45f, old[i].y * 0.55f + y * 0.45f)
            } else PointF(x, y)
        }
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!enabled) return
        val p = points ?: return
        if (p.size < 33) return
        val scale = max(width.toFloat() / inputWidth, height.toFloat() / inputHeight)
        val drawnWidth = inputWidth * scale
        val drawnHeight = inputHeight * scale
        val offsetX = (width - drawnWidth) / 2f
        val offsetY = (height - drawnHeight) / 2f
        fun sx(i: Int) = offsetX + p[i].x * drawnWidth
        fun sy(i: Int) = offsetY + p[i].y * drawnHeight

        for ((a, b) in CONNECTIONS) canvas.drawLine(sx(a), sy(a), sx(b), sy(b), linePaint)
        for (i in p.indices) {
            canvas.drawCircle(sx(i), sy(i), dp(6f), outerPaint)
            canvas.drawCircle(sx(i), sy(i), dp(3.5f), pointPaint)
        }
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    companion object {
        private val CONNECTIONS = arrayOf(
            0 to 1, 1 to 2, 2 to 3, 3 to 7, 0 to 4, 4 to 5, 5 to 6, 6 to 8, 9 to 10,
            11 to 12, 11 to 13, 13 to 15, 15 to 17, 15 to 19, 15 to 21, 17 to 19,
            12 to 14, 14 to 16, 16 to 18, 16 to 20, 16 to 22, 18 to 20,
            11 to 23, 12 to 24, 23 to 24,
            23 to 25, 25 to 27, 27 to 29, 29 to 31, 27 to 31,
            24 to 26, 26 to 28, 28 to 30, 30 to 32, 28 to 32
        )
    }
}
