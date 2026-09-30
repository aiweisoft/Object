package uts.sdk.modules.uniYoloDetect

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/**
 * 检测框叠加绘制 View
 */
class OverlayView(context: Context) : View(context) {

    private val boxPaint = Paint().apply {
        color = Color.parseColor("#00FF00")
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.parseColor("#00FF00")
        textSize = 42f
        isAntiAlias = true
    }

    private val textBgPaint = Paint().apply {
        color = Color.parseColor("#88000000")
        style = Paint.Style.FILL
    }

    private var detections: List<FloatArray> = emptyList()
    private var imageWidth = 1
    private var imageHeight = 1
    private var type = "detect"

    fun setDetections(dets: List<FloatArray>, imgW: Int, imgH: Int, type: String) {
        detections = dets
        imageWidth = if (imgW > 0) imgW else 1
        imageHeight = if (imgH > 0) imgH else 1
        this.type = type
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // FILL_CENTER 变换（裁剪填满）：还原 PreviewView 实际显示区域，保证框与画面对齐
        val imgRatio = imageWidth.toFloat() / imageHeight
        val viewRatio = w / h
        val drawW: Float
        val drawH: Float
        if (viewRatio > imgRatio) {
            drawW = w
            drawH = w / imgRatio
        } else {
            drawH = h
            drawW = h * imgRatio
        }
        val offsetX = (w - drawW) / 2f
        val offsetY = (h - drawH) / 2f

        for (d in detections) {
            val x = offsetX + d[0] * drawW
            val y = offsetY + d[1] * drawH
            val bw = d[2] * drawW
            val bh = d[3] * drawH
            val label = YoloDetector.getLabel(d[5].toInt(), type) + " " + String.format("%.2f", d[4])
            canvas.drawRect(x, y, x + bw, y + bh, boxPaint)
            val textWidth = textPaint.measureText(label)
            canvas.drawRect(x, y - 46f, x + textWidth + 8f, y, textBgPaint)
            canvas.drawText(label, x + 4f, y - 12f, textPaint)
        }
    }
}
