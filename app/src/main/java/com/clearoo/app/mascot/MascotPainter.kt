package com.clearoo.app.mascot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.clearoo.app.domain.Mood

/**
 * Draws Roo, Clearoo's kangaroo mascot, with plain android.graphics so the same drawing
 * works in Compose, the home-screen widget and notification icons.
 * Everything is laid out on a 200×200 grid and scaled to the requested size.
 */
class MascotPainter {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val bodyShader = LinearGradient(0f, 52f, 0f, 186f, BODY_LIGHT, BODY, Shader.TileMode.CLAMP)
    private val path = Path()
    private val rect = RectF()

    fun render(mood: Mood, sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        draw(Canvas(bitmap), sizePx.toFloat(), mood)
        return bitmap
    }

    /** [blink] runs 0 (open) → 1 (closed). */
    fun draw(canvas: Canvas, size: Float, mood: Mood, blink: Float = 0f) {
        current = canvas
        canvas.save()
        canvas.scale(size / 200f, size / 200f)

        oval(100f, 190f, 52f, 7f, SHADOW)

        val armsUp = mood == Mood.PROUD || mood == Mood.EXCITED
        if (armsUp) raisedArms()
        ears(mood)
        oval(72f, 180f, 20f, 9f, BODY_DARK)
        oval(128f, 180f, 20f, 9f, BODY_DARK)

        fill.shader = bodyShader
        oval(100f, 119f, 70f, 67f, BODY)
        fill.shader = null
        oval(100f, 147f, 38f, 35f, BELLY)
        stroke.color = POUCH
        stroke.strokeWidth = 3f
        path.reset()
        path.moveTo(74f, 152f)
        path.quadTo(100f, 168f, 126f, 152f)
        canvas.drawPath(path, stroke)
        if (!armsUp) paws(mood)

        oval(62f, 120f, 10f, 6f, CHEEK)
        oval(138f, 120f, 10f, 6f, CHEEK)

        eyes(canvas, mood, blink)
        brows(canvas, mood)
        oval(100f, 112f, 7f, 5f, DARK)
        mouth(canvas, mood)
        extras(canvas, mood)

        canvas.restore()
    }

    /** The canvas being drawn on; set at the start of [draw]. */
    private lateinit var current: Canvas

    private fun oval(cx: Float, cy: Float, rx: Float, ry: Float, color: Int) {
        if (fill.shader == null) fill.color = color
        rect.set(cx - rx, cy - ry, cx + rx, cy + ry)
        current.drawOval(rect, fill)
    }

    private fun ears(mood: Mood) {
        val tilt = when (mood) {
            Mood.SAD, Mood.WORRIED, Mood.SLEEPY -> 38f
            Mood.EXCITED, Mood.PROUD, Mood.SHOCKED -> 6f
            else -> 16f
        }
        ear(70f, -tilt)
        ear(130f, tilt)
    }

    private fun ear(cx: Float, angle: Float) {
        current.save()
        current.rotate(angle, cx, 72f)
        oval(cx, 46f, 16f, 36f, BODY)
        oval(cx, 50f, 8f, 25f, INNER_EAR)
        current.restore()
    }

    private fun raisedArms() {
        current.save()
        current.rotate(-35f, 52f, 112f)
        oval(50f, 86f, 12f, 26f, BODY_DARK)
        current.restore()
        current.save()
        current.rotate(35f, 148f, 112f)
        oval(150f, 86f, 12f, 26f, BODY_DARK)
        current.restore()
    }

    private fun paws(mood: Mood) {
        val y = if (mood == Mood.SAD) 158f else 146f
        oval(80f, y, 11f, 9f, BODY_DARK)
        oval(120f, y, 11f, 9f, BODY_DARK)
    }

    private fun eyes(canvas: Canvas, mood: Mood, blink: Float) {
        for (x in floatArrayOf(76f, 124f)) {
            val y = 98f
            when (mood) {
                Mood.HAPPY, Mood.PROUD -> arc(canvas, x - 9f, y + 3f, x, y - 9f, x + 9f, y + 3f, 5f)
                Mood.SLEEPY -> arc(canvas, x - 9f, y, x, y + 6f, x + 9f, y, 4f)
                Mood.LOVE -> heart(canvas, x, y, 11f)
                Mood.SHOCKED -> {
                    oval(x, y, 13f, 13f, WHITE)
                    oval(x, y, 6f, 6f, DARK)
                }
                else -> {
                    val big = mood == Mood.EXCITED
                    val open = (1f - blink).coerceIn(0f, 1f)
                    if (open < 0.2f) {
                        arc(canvas, x - 8f, y, x, y + 3f, x + 8f, y, 4f)
                    } else {
                        oval(x, y, if (big) 10f else 8f, (if (big) 13f else 11f) * open, DARK)
                        oval(x + 3f, y - 4f * open, 3f, 3f * open, WHITE)
                        if (big) oval(x - 3f, y + 4f * open, 1.6f, 1.6f * open, WHITE)
                    }
                }
            }
        }
    }

    private fun brows(canvas: Canvas, mood: Mood) {
        stroke.color = DARK
        stroke.strokeWidth = 4f
        when (mood) {
            Mood.SAD, Mood.WORRIED -> {
                canvas.drawLine(64f, 82f, 86f, 76f, stroke)
                canvas.drawLine(114f, 76f, 136f, 82f, stroke)
            }
            Mood.SHOCKED -> {
                arc(canvas, 64f, 78f, 76f, 70f, 88f, 78f, 4f)
                arc(canvas, 112f, 78f, 124f, 70f, 136f, 78f, 4f)
            }
            else -> Unit
        }
    }

    private fun mouth(canvas: Canvas, mood: Mood) {
        when (mood) {
            Mood.HAPPY, Mood.PROUD, Mood.EXCITED, Mood.LOVE -> {
                path.reset()
                path.moveTo(84f, 122f)
                path.quadTo(100f, 152f, 116f, 122f)
                path.close()
                fill.color = MOUTH
                canvas.drawPath(path, fill)
                oval(100f, 136f, 7f, 4f, TONGUE)
            }
            Mood.SAD -> arc(canvas, 88f, 134f, 100f, 122f, 112f, 134f, 4f)
            Mood.WORRIED -> {
                stroke.color = DARK
                stroke.strokeWidth = 4f
                path.reset()
                path.moveTo(86f, 130f)
                path.quadTo(93f, 124f, 100f, 130f)
                path.quadTo(107f, 136f, 114f, 130f)
                canvas.drawPath(path, stroke)
            }
            Mood.SHOCKED -> oval(100f, 132f, 7f, 9f, MOUTH)
            Mood.SLEEPY -> oval(100f, 128f, 4f, 3f, MOUTH)
            Mood.HOPEFUL -> arc(canvas, 88f, 124f, 100f, 138f, 112f, 124f, 4f)
        }
    }

    private fun extras(canvas: Canvas, mood: Mood) {
        when (mood) {
            Mood.SAD -> drop(canvas, 70f, 120f, 5f)
            Mood.WORRIED -> drop(canvas, 154f, 72f, 6f)
            Mood.SLEEPY -> {
                text.color = ZZZ
                text.textSize = 24f
                canvas.drawText("z", 150f, 60f, text)
                text.textSize = 32f
                canvas.drawText("Z", 164f, 36f, text)
            }
            Mood.PROUD, Mood.EXCITED -> {
                sparkle(canvas, 28f, 40f, 11f)
                sparkle(canvas, 174f, 52f, 9f)
                sparkle(canvas, 168f, 150f, 7f)
            }
            Mood.LOVE -> {
                heart(canvas, 166f, 40f, 9f)
                heart(canvas, 34f, 58f, 6f)
            }
            else -> Unit
        }
    }

    private fun arc(canvas: Canvas, x1: Float, y1: Float, cx: Float, cy: Float, x2: Float, y2: Float, width: Float) {
        stroke.color = DARK
        stroke.strokeWidth = width
        path.reset()
        path.moveTo(x1, y1)
        path.quadTo(cx, cy, x2, y2)
        canvas.drawPath(path, stroke)
    }

    private fun heart(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        path.reset()
        path.moveTo(cx, cy + 0.8f * s)
        path.cubicTo(cx - 1.3f * s, cy - 0.1f * s, cx - 0.6f * s, cy - 1.0f * s, cx, cy - 0.35f * s)
        path.cubicTo(cx + 0.6f * s, cy - 1.0f * s, cx + 1.3f * s, cy - 0.1f * s, cx, cy + 0.8f * s)
        path.close()
        fill.color = HEART
        canvas.drawPath(path, fill)
    }

    private fun sparkle(canvas: Canvas, x: Float, y: Float, r: Float) {
        path.reset()
        path.moveTo(x, y - r)
        path.quadTo(x, y, x + r, y)
        path.quadTo(x, y, x, y + r)
        path.quadTo(x, y, x - r, y)
        path.quadTo(x, y, x, y - r)
        path.close()
        fill.color = SPARKLE
        canvas.drawPath(path, fill)
    }

    private fun drop(canvas: Canvas, x: Float, y: Float, r: Float) {
        path.reset()
        path.moveTo(x, y - r * 2.4f)
        path.lineTo(x + r * 0.95f, y - r * 0.3f)
        path.lineTo(x - r * 0.95f, y - r * 0.3f)
        path.close()
        fill.color = TEAR
        canvas.drawPath(path, fill)
        oval(x, y, r, r, TEAR)
    }

    private companion object {
        const val BODY = 0xFFFFA24C.toInt()
        const val BODY_LIGHT = 0xFFFFBC70.toInt()
        const val BODY_DARK = 0xFFF0883A.toInt()
        const val BELLY = 0xFFFFE6CC.toInt()
        const val POUCH = 0xFFEFC39B.toInt()
        const val INNER_EAR = 0xFFFF8FA8.toInt()
        const val DARK = 0xFF3A2440.toInt()
        const val CHEEK = 0x78FF7F9E
        const val MOUTH = 0xFF7A2142.toInt()
        const val TONGUE = 0xFFFF7C98.toInt()
        const val TEAR = 0xFF7FD6FF.toInt()
        const val SPARKLE = 0xFFFFD84D.toInt()
        const val HEART = 0xFFFF4D78.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
        const val ZZZ = 0xFFD9D0FF.toInt()
        const val SHADOW = 0x33000000
    }
}
