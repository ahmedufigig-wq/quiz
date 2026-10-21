package com.friends.quiz

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * ست خلفيات فاتحة ومنوّرة بأشكال هندسية (دوائر، مثلثات، معيّنات، حلقات).
 * مرسومة بالكود فلا تزيد حجم التطبيق، وتعمل على كل إصدارات أندرويد.
 *
 * الاستخدام:
 *   view.background = GameBackground()               // الخلفية الأولى
 *   view.background = GameBackground.random()        // خلفية عشوائية
 *   view.background = GameBackground.forIndex(round) // خلفية مختلفة لكل جولة
 */
class GameBackground(theme: Int = 0) : Drawable() {

    private class S(val type: Int, val fx: Float, val fy: Float, val fs: Float,
                    val rot: Float, val ci: Int, val a: Int)

    private class T(val grad: IntArray, val pal: IntArray,
                    val layoutB: Boolean, val mirror: Boolean, val flip: Boolean)

    companion object {
        const val COUNT = 6

        private fun op(rgb: Int): Int = rgb or (0xFF shl 24)

        fun random(): GameBackground = GameBackground((0 until COUNT).random())

        fun forIndex(i: Int): GameBackground = GameBackground(((i % COUNT) + COUNT) % COUNT)

        private val LAYOUT_A = listOf(
        S(0, 0.12f, 0.08f, 0.22f, 0f, 0, 85),
        S(1, 0.88f, 0.14f, 0.16f, 15f, 1, 85),
        S(2, 0.1f, 0.32f, 0.1f, 20f, 2, 85),
        S(3, 0.9f, 0.4f, 0.12f, 0f, 3, 85),
        S(4, 0.15f, 0.58f, 0.14f, 0f, 4, 102),
        S(0, 0.85f, 0.66f, 0.2f, 0f, 5, 85),
        S(1, 0.2f, 0.82f, 0.18f, -20f, 6, 85),
        S(2, 0.8f, 0.9f, 0.12f, 30f, 7, 85),
        S(4, 0.58f, 0.05f, 0.07f, 0f, 0, 102),
        S(0, 0.45f, 0.96f, 0.1f, 0f, 1, 85)
        )

        private val LAYOUT_B = listOf(
        S(1, 0.1f, 0.1f, 0.18f, -10f, 1, 85),
        S(0, 0.9f, 0.08f, 0.12f, 0f, 2, 85),
        S(3, 0.85f, 0.28f, 0.1f, 0f, 0, 85),
        S(4, 0.12f, 0.4f, 0.1f, 0f, 3, 102),
        S(2, 0.92f, 0.52f, 0.12f, -15f, 4, 85),
        S(0, 0.08f, 0.66f, 0.16f, 0f, 5, 85),
        S(3, 0.78f, 0.74f, 0.14f, 0f, 6, 85),
        S(1, 0.3f, 0.92f, 0.16f, 25f, 7, 85),
        S(4, 0.62f, 0.88f, 0.08f, 0f, 2, 102),
        S(2, 0.5f, 0.04f, 0.07f, 10f, 5, 85)
        )

        private val THEMES = listOf(
        T(intArrayOf(op(0xE3F2FF), op(0xF3E8FF), op(0xFFF0E1)), intArrayOf(0xFF6B9D, 0x00C2FF, 0xFFC400, 0x7C4DFF, 0x00E5A8, 0xFFA000, 0x536DFE, 0xFF5252),
          false, false, false),
        T(intArrayOf(op(0xFFF1E0), op(0xFFE0EC), op(0xFFF8D6)), intArrayOf(0xFF7043, 0xFFB300, 0xF06292, 0xAB47BC, 0xFF8A65, 0xFDD835, 0xEC407A, 0xFF5252),
          true, false, false),
        T(intArrayOf(op(0xE0F7F4), op(0xE3F6FF), op(0xF1FFE6)), intArrayOf(0x00BFA5, 0x26C6DA, 0x66BB6A, 0x29B6F6, 0x9CCC65, 0x26A69A, 0x5C6BC0, 0xFFCA28),
          false, true, false),
        T(intArrayOf(op(0xFFFBE0), op(0xFFF3D6), op(0xF1FFE3)), intArrayOf(0xFDD835, 0xFF9800, 0x8BC34A, 0xFF7043, 0x4DD0E1, 0xFFCA28, 0x66BB6A, 0xEF5350),
          true, true, false),
        T(intArrayOf(op(0xF3E5FF), op(0xE8EAFF), op(0xFFE8F5)), intArrayOf(0x9C27B0, 0x7C4DFF, 0xE040FB, 0x5C6BC0, 0xF06292, 0x40C4FF, 0xBA68C8, 0x3F51B5),
          false, false, true),
        T(intArrayOf(op(0xE1F5FE), op(0xE0F7FA), op(0xFFF8E1)), intArrayOf(0x0288D1, 0x00ACC1, 0xFFB300, 0x4FC3F7, 0xFF7043, 0x26C6DA, 0xFFD54F, 0x29B6F6),
          true, true, true)
        )
    }

    private val t: T = THEMES[((theme % COUNT) + COUNT) % COUNT]
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val box = RectF()

    override fun draw(canvas: Canvas) {
        val b = bounds
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        if (w <= 0f || h <= 0f) return

        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(),
            t.grad, floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(b, paint)
        paint.shader = null

        val base = minOf(w, h)
        val shapes = if (t.layoutB) LAYOUT_B else LAYOUT_A
        for (s in shapes) {
            val fx = if (t.mirror) 1f - s.fx else s.fx
            val fy = if (t.flip) 1f - s.fy else s.fy
            val cx = b.left + fx * w
            val cy = b.top + fy * h
            val r = s.fs * base
            paint.color = (s.a shl 24) or t.pal[s.ci]
            paint.style = Paint.Style.FILL
            canvas.save()
            canvas.rotate(if (t.mirror) -s.rot else s.rot, cx, cy)
            when (s.type) {
                0 -> canvas.drawCircle(cx, cy, r, paint)
                1 -> {
                    path.reset()
                    path.moveTo(cx, cy - r)
                    path.lineTo(cx - r * 0.87f, cy + r * 0.5f)
                    path.lineTo(cx + r * 0.87f, cy + r * 0.5f)
                    path.close()
                    canvas.drawPath(path, paint)
                }
                2 -> {
                    box.set(cx - r, cy - r, cx + r, cy + r)
                    canvas.drawRoundRect(box, r * 0.25f, r * 0.25f, paint)
                }
                3 -> {
                    canvas.rotate(45f, cx, cy)
                    box.set(cx - r * 0.8f, cy - r * 0.8f, cx + r * 0.8f, cy + r * 0.8f)
                    canvas.drawRect(box, paint)
                }
                else -> {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = r * 0.3f
                    canvas.drawCircle(cx, cy, r, paint)
                }
            }
            canvas.restore()
        }
    }

    override fun setAlpha(alpha: Int) { }

    override fun setColorFilter(colorFilter: ColorFilter?) { }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}
