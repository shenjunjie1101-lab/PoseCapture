package com.example.posecapture

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity(), PoseLandmarkerHelper.Listener {
    private lateinit var previewView: PreviewView
    private lateinit var overlayView: PoseOverlayView
    private lateinit var statusView: TextView
    private lateinit var cameraExecutor: ExecutorService
    private var poseHelper: PoseLandmarkerHelper? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraFacing = CameraSelector.LENS_FACING_BACK
    private val helperReady = AtomicBoolean(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera()
        else Toast.makeText(this, "需要相机权限才能进行实时骨骼捕捉", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        cameraExecutor = Executors.newSingleThreadExecutor()
        buildUi()

        cameraExecutor.execute {
            try {
                poseHelper = PoseLandmarkerHelper(applicationContext, this)
                helperReady.set(true)
                runOnUiThread { statusView.text = "POSE • READY" }
            } catch (t: Throwable) {
                onError("模型初始化失败：" + (t.message ?: t.javaClass.simpleName))
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        previewView = PreviewView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
        root.addView(previewView)

        overlayView = PoseOverlayView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(overlayView)

        statusView = makePill("POSE • LOADING").apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, dp(38)
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = dp(16)
                topMargin = dp(18)
            }
        }
        root.addView(statusView)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = roundedBg(0x99000000.toInt(), 28f)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, dp(62)
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(28)
            }
        }

        controls.addView(actionButton("切换相机").apply {
            setOnClickListener {
                cameraFacing = if (cameraFacing == CameraSelector.LENS_FACING_BACK)
                    CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                startCamera()
            }
        })

        controls.addView(actionButton("骨骼：开").apply {
            setOnClickListener {
                val next = !overlayView.isSkeletonEnabled()
                overlayView.setSkeletonEnabled(next)
                text = if (next) "骨骼：开" else "骨骼：关"
            }
        })

        root.addView(controls)
        setContentView(root)
    }

    private fun makePill(value: String) = TextView(this).apply {
        text = value
        setTextColor(Color.WHITE)
        textSize = 12f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(dp(14), 0, dp(14), 0)
        background = roundedBg(0x99000000.toInt(), 19f)
    }

    private fun actionButton(value: String) = TextView(this).apply {
        text = value
        setTextColor(Color.WHITE)
        textSize = 13f
        gravity = Gravity.CENTER
        setPadding(dp(18), 0, dp(18), 0)
        background = roundedBg(0x22FFFFFF, 22f)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, dp(46)
        ).apply {
            marginStart = dp(4)
            marginEnd = dp(4)
        }
    }

    private fun roundedBg(color: Int, radiusDp: Float) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp.toInt()).toFloat()
            setStroke(dp(1), 0x28FFFFFF)
        }

    private fun startCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED) return

        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            cameraProvider = provider
            bindCamera(provider)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCamera(provider: ProcessCameraProvider) {
        val selector = CameraSelector.Builder().requireLensFacing(cameraFacing).build()
        if (!provider.hasCamera(selector)) {
            cameraFacing = CameraSelector.LENS_FACING_BACK
            Toast.makeText(this, "当前设备没有这个摄像头", Toast.LENGTH_SHORT).show()
            return
        }

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        analysis.setAnalyzer(cameraExecutor) { image ->
            val helper = poseHelper
            if (helperReady.get() && helper != null) {
                try {
                    helper.detect(image, cameraFacing == CameraSelector.LENS_FACING_FRONT)
                } catch (t: Throwable) {
                    try { image.close() } catch (_: Throwable) {}
                    onError(t.message ?: "检测失败")
                }
            } else image.close()
        }

        provider.unbindAll()
        try {
            provider.bindToLifecycle(this, selector, preview, analysis)
        } catch (t: Throwable) {
            onError("相机启动失败：" + (t.message ?: t.javaClass.simpleName))
        }
    }

    override fun onResult(
        result: PoseLandmarkerResult,
        inputWidth: Int,
        inputHeight: Int,
        latencyMs: Long
    ) {
        runOnUiThread {
            overlayView.setResult(result, inputWidth, inputHeight)
            statusView.text = if (result.landmarks().isNotEmpty())
                "POSE • " + latencyMs + " ms"
            else "POSE • SEARCHING"
        }
    }

    override fun onError(message: String) {
        runOnUiThread {
            statusView.text = "POSE • ERROR"
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        cameraExecutor.execute { poseHelper?.close() }
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()
}
