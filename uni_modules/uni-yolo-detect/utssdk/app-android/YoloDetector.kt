package uts.sdk.modules.uniYoloDetect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.util.Base64
import java.nio.FloatBuffer
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.json.JSONArray
import org.json.JSONObject

/**
 * YOLOv8 推理核心（基于 ONNX Runtime），支持双模型：
 * - detect：通用物体检测 yolov8n.onnx（COCO 80 类）
 * - face：人脸检测 yolov8n-face.onnx（单类）
 * 均为带 NMS 端到端，输入 [1,3,640,640]，输出 [1,300,6]（x1,y1,x2,y2,score,classId）
 */
object YoloDetector {

    private const val INPUT_SIZE = 640

    private var env: OrtEnvironment? = null
    private var detectSession: OrtSession? = null
    private var faceSession: OrtSession? = null
    private var detectInputName: String = ""
    private var faceInputName: String = ""
    private var cocoLabels: List<String> = emptyList()
    private var faceLabels: List<String> = emptyList()
    private var detectReady = false
    private var faceReady = false

    @Synchronized
    fun init(context: Context): Boolean {
        if (env == null) {
            env = OrtEnvironment.getEnvironment()
        }
        val appContext = context.applicationContext
        initDetect(appContext)
        initFace(appContext)
        return detectReady || faceReady
    }

    private fun initDetect(ctx: Context): Boolean {
        if (detectReady) return true
        return try {
            val e = env ?: return false
            val bytes = ctx.assets.open("yolov8n.onnx").use { it.readBytes() }
            detectSession = e.createSession(bytes)
            detectInputName = detectSession!!.inputNames.iterator().next()
            cocoLabels = ctx.assets.open("coco.names").use {
                it.bufferedReader().readLines().filter { s -> s.isNotBlank() }
            }
            detectReady = true
            true
        } catch (ex: Throwable) {
            ex.printStackTrace()
            detectReady = false
            false
        }
    }

    private fun initFace(ctx: Context): Boolean {
        if (faceReady) return true
        return try {
            val e = env ?: return false
            val bytes = ctx.assets.open("yolov8n-face.onnx").use { it.readBytes() }
            faceSession = e.createSession(bytes)
            faceInputName = faceSession!!.inputNames.iterator().next()
            faceLabels = ctx.assets.open("face.names").use {
                it.bufferedReader().readLines().filter { s -> s.isNotBlank() }
            }
            faceReady = true
            true
        } catch (ex: Throwable) {
            ex.printStackTrace()
            faceReady = false
            false
        }
    }

    fun isReady(): Boolean = detectReady || faceReady

    fun isTypeReady(type: String): Boolean = if (type == "face") faceReady else detectReady

    /**
     * 单图检测（本地路径），返回 JSON 字符串
     */
    fun detectImagePath(path: String, threshold: Double, type: String): String {
        val bitmap = BitmapFactory.decodeFile(path) ?: return "[]"
        return toJson(detectBitmap(bitmap, threshold.toFloat(), type), type)
    }

    /**
     * 单图检测（base64），返回 JSON 字符串
     */
    fun detectImageBase64(base64: String, threshold: Double, type: String): String {
        val bytes = try {
            Base64.decode(base64, Base64.DEFAULT)
        } catch (_: Throwable) {
            return "[]"
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return "[]"
        return toJson(detectBitmap(bitmap, threshold.toFloat(), type), type)
    }

    /**
     * Bitmap 检测，返回结果列表，每个元素为 [x, y, w, h, score, classId]（坐标归一化 0-1）
     */
    @Synchronized
    fun detectBitmap(bitmap: Bitmap, threshold: Float, type: String): List<FloatArray> {
        val s = if (type == "face") faceSession else detectSession
        val name = if (type == "face") faceInputName else detectInputName
        if (s == null) return emptyList()
        val e = env ?: return emptyList()
        return try {
            val lb = preprocess(bitmap)
            val tensor = OnnxTensor.createTensor(e, FloatBuffer.wrap(lb.data), longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong()))
            val result = s.run(mapOf(name to tensor))
            val raw = result.get(0).value as Array<Array<FloatArray>>
            parseOutput(raw, threshold, lb, bitmap.width, bitmap.height)
        } catch (ex: Throwable) {
            ex.printStackTrace()
            emptyList()
        }
    }

    fun getLabel(classId: Int, type: String): String {
        val labels = if (type == "face") faceLabels else cocoLabels
        return if (classId in labels.indices) labels[classId] else "unknown"
    }

    fun toJson(results: List<FloatArray>, type: String): String {
        val arr = JSONArray()
        for (d in results) {
            val obj = JSONObject()
            obj.put("x", d[0].toDouble())
            obj.put("y", d[1].toDouble())
            obj.put("width", d[2].toDouble())
            obj.put("height", d[3].toDouble())
            obj.put("score", d[4].toDouble())
            obj.put("classId", d[5].toInt())
            obj.put("className", getLabel(d[5].toInt(), type))
            arr.put(obj)
        }
        return arr.toString()
    }

    private data class Letterbox(val data: FloatArray, val scale: Float, val padX: Int, val padY: Int)

    private fun preprocess(bitmap: Bitmap): Letterbox {
        val scale = minOf(INPUT_SIZE.toFloat() / bitmap.width, INPUT_SIZE.toFloat() / bitmap.height)
        val newW = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val newH = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val padX = (INPUT_SIZE - newW) / 2
        val padY = (INPUT_SIZE - newH) / 2

        val scaled = Bitmap.createScaledBitmap(bitmap, newW, newH, false)
        val letter = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(letter)
        c.drawColor(Color.rgb(114, 114, 114))
        c.drawBitmap(scaled, padX.toFloat(), padY.toFloat(), null)

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        letter.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        val size = INPUT_SIZE * INPUT_SIZE
        val input = FloatArray(3 * size)
        for (i in 0 until size) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xFF) / 255.0f
            val g = ((p shr 8) and 0xFF) / 255.0f
            val b = (p and 0xFF) / 255.0f
            input[i] = r
            input[size + i] = g
            input[2 * size + i] = b
        }

        if (scaled !== bitmap) scaled.recycle()
        letter.recycle()
        return Letterbox(input, scale, padX, padY)
    }

    private fun parseOutput(raw: Array<Array<FloatArray>>, threshold: Float, lb: Letterbox, origW: Int, origH: Int): List<FloatArray> {
        val dets = raw[0]
        val out = mutableListOf<FloatArray>()
        for (d in dets) {
            val x1 = d[0]
            val y1 = d[1]
            val x2 = d[2]
            val y2 = d[3]
            val score = d[4]
            val classId = d[5].toInt()
            if (score >= threshold) {
                val ox1 = (x1 - lb.padX) / lb.scale
                val oy1 = (y1 - lb.padY) / lb.scale
                val ox2 = (x2 - lb.padX) / lb.scale
                val oy2 = (y2 - lb.padY) / lb.scale
                val x = ox1 / origW
                val y = oy1 / origH
                val w = (ox2 - ox1) / origW
                val h = (oy2 - oy1) / origH
                out.add(floatArrayOf(x, y, w, h, score, classId.toFloat()))
            }
        }
        return out
    }

    @Synchronized
    fun release() {
        try { detectSession?.close() } catch (_: Throwable) {}
        try { faceSession?.close() } catch (_: Throwable) {}
        try { env?.close() } catch (_: Throwable) {}
        detectSession = null
        faceSession = null
        env = null
        detectReady = false
        faceReady = false
    }
}
