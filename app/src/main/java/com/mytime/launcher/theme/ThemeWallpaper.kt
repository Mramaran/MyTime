package com.mytime.launcher.theme

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader

/**
 * Draws a theme's wallpaper from its colours: a vertical gradient, a soft glow of the
 * last colour, and two translucent hills along the bottom. Made on the phone, so themes
 * need no downloaded images.
 */
object ThemeWallpaper {

    fun render(colors: List<String>, width: Int, height: Int): Bitmap {
        val cs = colors.mapNotNull { runCatching { Color.parseColor(it) }.getOrNull() }.ifEmpty { listOf(Color.BLACK) }
        val top = cs.first()
        val bottom = cs.last()
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val w = width.toFloat()
        val h = height.toFloat()

        val gradient = if (cs.size >= 3) LinearGradient(0f, 0f, 0f, h, cs.toIntArray(), null, Shader.TileMode.CLAMP)
        else LinearGradient(0f, 0f, 0f, h, top, mix(top, bottom, 0.55f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, Paint().apply { shader = gradient })

        // Soft glow, like a sun or city light behind the hills.
        val glow = RadialGradient(w * 0.72f, h * 0.62f, w * 0.85f,
            intArrayOf(withAlpha(bottom, 150), withAlpha(bottom, 0)), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = glow })

        // Two hills, the nearer one darker.
        hill(canvas, w, h, baseY = 0.78f, amp = 0.05f, phase = 0.3f, color = withAlpha(mix(top, bottom, 0.35f), 200))
        hill(canvas, w, h, baseY = 0.86f, amp = 0.04f, phase = 1.7f, color = withAlpha(mix(top, Color.BLACK, 0.35f), 235))
        return bmp
    }

    private fun hill(canvas: Canvas, w: Float, h: Float, baseY: Float, amp: Float, phase: Float, color: Int) {
        val path = Path().apply {
            moveTo(0f, h)
            val steps = 48
            for (i in 0..steps) {
                val x = w * i / steps
                val y = h * (baseY + amp * kotlin.math.sin(phase + 2.6 * Math.PI * i / steps).toFloat())
                lineTo(x, y)
            }
            lineTo(w, h)
            close()
        }
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    }

    /** Sets the home-screen wallpaper only; the lock screen keeps its own. */
    fun apply(context: Context, colors: List<String>) {
        val metrics = context.resources.displayMetrics
        val bmp = render(colors, metrics.widthPixels, metrics.heightPixels)
        WallpaperManager.getInstance(context).setBitmap(bmp, null, true, WallpaperManager.FLAG_SYSTEM)
    }

    private fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
    )

    private fun withAlpha(c: Int, a: Int) = Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))
}
