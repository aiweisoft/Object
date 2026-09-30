package uts.sdk.modules.uniYoloDetect

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 实时检测结果桥接对象，由 UTS 侧设置回调
 */
object DetectBridge {
    @Volatile
    var onResult: ((String) -> Unit)? = null

    /**
     * 启动实时检测界面
     */
    fun launch(threshold: Number, type: String): Boolean {
        return try {
            val activity = io.dcloud.uts.UTSAndroid.getUniActivity()
            if (activity == null) {
                false
            } else {
                val intent = Intent(activity, DetectActivity::class.java)
                intent.putExtra(DetectActivity.EXTRA_THRESHOLD, threshold.toFloat())
                intent.putExtra(DetectActivity.EXTRA_TYPE, type)
                activity.startActivity(intent)
                true
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            false
        }
    }
}

/**
 * 摄像头实时目标检测界面
 */
class DetectActivity : ComponentActivity() {

    companion object {
        const val EXTRA_THRESHOLD = "threshold"
        const val EXTRA_TYPE = "type"
        private const val REQ_CAMERA = 100
    }

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: OverlayView
    private var executor: ExecutorService? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var threshold = 0.25f
    private var type = "detect"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        threshold = intent.getFloatExtra(EXTRA_THRESHOLD, 0.25f)
        type = intent.getStringExtra(EXTRA_TYPE) ?: "detect"
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        previewView = PreviewView(this)
        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
        overlayView = OverlayView(this)
        val container = FrameLayout(this)
        container.addView(previewView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        container.addView(overlayView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val closeBtn = TextView(this).apply {
            text = "✕ 关闭"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setBackgroundColor(Color.parseColor("#88000000"))
            setPadding(36, 20, 36, 20)
            setOnClickListener { finish() }
        }
        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.gravity = Gravity.TOP or Gravity.START
        lp.topMargin = 40
        lp.leftMargin = 24
        container.addView(closeBtn, lp)

        setContentView(container)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
            } else {
                startCamera()
            }
        } else {
            startCamera()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CAMERA) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                finish()
            }
        }
    }

    private fun startCamera() {
        if (!YoloDetector.isReady()) {
            if (!YoloDetector.init(applicationContext)) {
                finish()
                return
            }
        }
        if (!YoloDetector.isTypeReady(type)) {
            finish()
            return
        }
        executor = Executors.newSingleThreadExecutor()
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                bindUseCases(provider)
            } catch (e: Throwable) {
                e.printStackTrace()
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindUseCases(provider: ProcessCameraProvider) {
        val preview = Preview.Builder().build()
        preview.setSurfaceProvider(previewView.surfaceProvider)

        imageAnalysis = ImageAnalysis.Builder()
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setTargetResolution(android.util.Size(640, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis?.setAnalyzer(executor!!) { image -> analyze(image) }

        provider.unbindAll()
        provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
    }

    private fun analyze(image: ImageProxy) {
        try {
            val bitmap = toBitmap(image)
            if (bitmap != null) {
                val results = YoloDetector.detectBitmap(bitmap, threshold, type)
                val json = YoloDetector.toJson(results, type)
                runOnUiThread {
                    overlayView.setDetections(results, bitmap.width, bitmap.height, type)
                    val cb = DetectBridge.onResult
                    if (cb != null) {
                        try { cb(json) } catch (_: Throwable) {}
                    }
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        } finally {
            image.close()
        }
    }

    private fun toBitmap(image: ImageProxy): Bitmap? {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val bmpWidth = image.width + rowPadding / pixelStride
        val bitmap = Bitmap.createBitmap(bmpWidth, image.height, Bitmap.Config.ARGB_8888)
        buffer.rewind()
        bitmap.copyPixelsFromBuffer(buffer)
        return if (rowPadding == 0) bitmap
        else Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
    }

    override fun onDestroy() {
        super.onDestroy()
        try { imageAnalysis?.clearAnalyzer() } catch (_: Throwable) {}
        imageAnalysis = null
        executor?.shutdown()
        executor = null
    }
}
