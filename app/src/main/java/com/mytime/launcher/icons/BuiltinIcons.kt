package com.mytime.launcher.icons

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import com.caverock.androidsvg.RenderOptions
import com.caverock.androidsvg.SVG
import java.io.File
import java.io.RandomAccessFile
import com.mytime.launcher.data.Settings

/**
 * MyTime's own icon looks, in the spirit of Niagara's Anycons. Every app is drawn from one line
 * glyph, so the whole home screen matches:
 *  - the glyph comes from Lawnicons (10,000+ open-source icons, Apache-2.0, bundled in assets),
 *  - else from the app's own monochrome icon (Android 13+),
 *  - else it is the app's first letter.
 */
enum class BuiltinStyle(val id: String, val title: String, val description: String) {
    Outline("outline", "Outline", "Line icons in your text colour"),
    OutlineChroma("outline_chroma", "Outline Chroma", "Line icons in each app's colour"),
    Themed("themed", "Themed", "Accent-coloured icons on a tinted circle"),
    Material("material", "Material", "Dark icons on pastel circles"),
    Sticker("sticker", "Sticker", "White icons on the app's colour with a white rim"),
    Clicks("clicks", "Clicks", "Light icons on dark tiles"),
    Gradient("gradient", "Gradient", "White icons on colourful gradient tiles");

    val packId get() = PREFIX + id

    companion object {
        const val PREFIX = "builtin:"
        fun fromPack(pack: String): BuiltinStyle? =
            if (!pack.startsWith(PREFIX)) null else entries.firstOrNull { it.id == pack.removePrefix(PREFIX) }
    }
}

object Lawnicons {
    @Volatile private var byComponent: Map<String, String>? = null
    @Volatile private var byPackage: Map<String, String>? = null

    private fun load(context: Context) {
        if (byComponent != null) return
        synchronized(this) {
            if (byComponent != null) return
            val comp = HashMap<String, String>(32_000)
            val pkg = HashMap<String, String>(16_000)
            context.assets.open("lawnicons/map.tsv").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val tab = line.indexOf('\t')
                    if (tab <= 0) return@forEach
                    val key = line.substring(0, tab)
                    val name = line.substring(tab + 1)
                    comp[key] = name
                    pkg.putIfAbsent(key.substringBefore('/'), name)
                }
            }
            byPackage = pkg
            byComponent = comp
        }
    }

    /** Icon name for an activity: exact match first, then any icon for the same app. */
    fun nameFor(context: Context, packageName: String, className: String): String? {
        load(context)
        return byComponent!!["$packageName/$className"] ?: byPackage!![packageName]
    }

    /**
     * The icons ship as one combined file (it compresses about 4× better in the APK than
     * 10,000 separate files) plus an index of where each icon starts. On first use the file is
     * unpacked into app storage, where single icons can be read without unpacking the rest.
     */
    @Volatile private var index: Map<String, LongArray>? = null
    @Volatile private var unpacked: File? = null

    private fun ensureUnpacked(context: Context): File {
        unpacked?.let { return it }
        synchronized(this) {
            unpacked?.let { return it }
            val version = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
            val dir = File(context.filesDir, "lawnicons").apply { mkdirs() }
            val file = File(dir, "icons-$version.txt")
            if (!file.exists()) {
                dir.listFiles()?.forEach { it.delete() } // icons from an older version of MyTime
                val tmp = File(dir, "icons.tmp")
                context.assets.open("lawnicons/icons.txt").use { input -> tmp.outputStream().use { input.copyTo(it) } }
                tmp.renameTo(file)
            }
            val idx = HashMap<String, LongArray>(12_000)
            context.assets.open("lawnicons/index.tsv").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val p = line.split('\t')
                    if (p.size == 3) idx[p[0]] = longArrayOf(p[1].toLong(), p[2].toLong())
                }
            }
            index = idx
            unpacked = file
            return file
        }
    }

    private fun svgText(context: Context, name: String): String? {
        val file = ensureUnpacked(context)
        val (offset, length) = index!![name]?.let { it[0] to it[1].toInt() } ?: return null
        val buf = ByteArray(length)
        RandomAccessFile(file, "r").use { it.seek(offset); it.readFully(buf) }
        return String(buf, Charsets.UTF_8)
    }

    /** Draws a Lawnicons glyph in [color] into a [size] square. */
    fun render(context: Context, name: String, size: Int, color: Int): Bitmap? = runCatching {
        val svg = SVG.getFromString(svgText(context, name) ?: return null)
        // Lawnicons have width/height but no viewBox; give them one and let them fill the viewport.
        if (svg.documentViewBox == null) svg.setDocumentViewBox(0f, 0f, svg.documentWidth, svg.documentHeight)
        svg.setDocumentWidth("100%")
        svg.setDocumentHeight("100%")
        // Draw the black glyph, then paint it in [color] keeping only its shape (SRC_IN).
        val black = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        svg.renderToCanvas(Canvas(black), RenderOptions().viewPort(0f, 0f, size.toFloat(), size.toFloat()))
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val tint = Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN) }
        Canvas(bmp).drawBitmap(black, 0f, 0f, tint)
        black.recycle()
        bmp
    }.getOrNull()
}

object BuiltinIcons {

    fun render(context: Context, activity: LauncherActivityInfo, size: Int, style: BuiltinStyle, s: Settings): Bitmap {
        val original = activity.getIcon(0)
        val appColor = dominantColor(original)
        val accent = accentColor(context, s)
        val onWall = if (s.wallpaperText == "dark") Color.rgb(21, 23, 27) else Color.WHITE

        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val full = RectF(0f, 0f, size.toFloat(), size.toFloat())
        val corner = size * 0.28f

        val (glyphColor, glyphScale) = when (style) {
            BuiltinStyle.Outline -> onWall to 1f
            BuiltinStyle.OutlineChroma -> mix(appColor, Color.WHITE, 0.25f) to 1f
            BuiltinStyle.Themed -> {
                paint.color = mix(Color.rgb(28, 28, 34), accent, 0.22f); c.drawOval(full, paint)
                accent to 0.78f
            }
            BuiltinStyle.Material -> {
                paint.color = mix(appColor, Color.WHITE, 0.62f); c.drawOval(full, paint)
                mix(appColor, Color.BLACK, 0.55f) to 0.78f
            }
            BuiltinStyle.Sticker -> {
                paint.color = Color.WHITE; c.drawOval(full, paint)
                val inset = size * 0.07f
                paint.color = appColor; c.drawOval(RectF(inset, inset, size - inset, size - inset), paint)
                Color.WHITE to 0.66f
            }
            BuiltinStyle.Clicks -> {
                paint.color = Color.rgb(21, 22, 26); c.drawRoundRect(full, corner, corner, paint)
                Color.rgb(237, 235, 228) to 0.76f
            }
            BuiltinStyle.Gradient -> {
                paint.shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(),
                    mix(appColor, Color.WHITE, 0.2f), hueShift(mix(appColor, Color.BLACK, 0.25f), 40f), Shader.TileMode.CLAMP)
                c.drawRoundRect(full, corner, corner, paint)
                paint.shader = null
                Color.WHITE to 0.76f
            }
        }

        val g = (size * glyphScale).toInt().coerceAtLeast(1)
        val glyph = glyph(context, activity, original, g, glyphColor)
        val o = (size - g) / 2f
        c.drawBitmap(glyph, o, o, null)
        return out
    }

    private fun glyph(context: Context, activity: LauncherActivityInfo, original: Drawable, size: Int, color: Int): Bitmap {
        val cn = activity.componentName
        Lawnicons.nameFor(context, cn.packageName, cn.className)?.let { name ->
            Lawnicons.render(context, name, size, color)?.let { return it }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            (original as? AdaptiveIconDrawable)?.monochrome?.let { mono ->
                val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val layer = (size * 1.5f).toInt()
                val off = (size - layer) / 2
                mono.mutate().colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
                mono.setBounds(off, off, off + layer, off + layer)
                mono.draw(Canvas(bmp))
                return bmp
            }
        }
        return letter(activity.label.toString(), size, color)
    }

    /** First letter in a thin circle, matching the line-icon look. */
    private fun letter(label: String, size: Int, color: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val stroke = size * 12f / 192f
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; this.color = color }
        val r = size * 74f / 192f
        c.drawCircle(size / 2f, size / 2f, r, ring)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; textAlign = Paint.Align.CENTER; textSize = size * 0.42f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val ch = label.trim().firstOrNull()?.uppercase() ?: "?"
        c.drawText(ch, size / 2f, size / 2f - (text.descent() + text.ascent()) / 2, text)
        return bmp
    }

    /**
     * The app's most noticeable colour: the most common hue among its saturated pixels (so a
     * many-coloured icon like Gmail comes out red rather than a muddy average), averaged within
     * that hue.
     */
    fun dominantColor(d: Drawable): Int {
        val bmp = runCatching { d.toBitmap(24, 24, Bitmap.Config.ARGB_8888) }.getOrNull() ?: return Color.GRAY
        val buckets = 12
        val weight = FloatArray(buckets)
        val sumR = LongArray(buckets); val sumG = LongArray(buckets); val sumB = LongArray(buckets); val count = IntArray(buckets)
        var ar = 0L; var ag = 0L; var ab = 0L; var an = 0
        val hsv = FloatArray(3)
        for (y in 0 until 24) for (x in 0 until 24) {
            val p = bmp.getPixel(x, y)
            if (Color.alpha(p) < 128) continue
            ar += Color.red(p); ag += Color.green(p); ab += Color.blue(p); an++
            Color.colorToHSV(p, hsv)
            if (hsv[1] > 0.3f && hsv[2] > 0.25f) {
                val i = ((hsv[0] / 360f) * buckets).toInt().coerceIn(0, buckets - 1)
                weight[i] += hsv[1] * hsv[2]
                sumR[i] += Color.red(p); sumG[i] += Color.green(p); sumB[i] += Color.blue(p); count[i]++
            }
        }
        val best = weight.indices.maxByOrNull { weight[it] } ?: -1
        return when {
            best >= 0 && count[best] > 6 -> Color.rgb((sumR[best] / count[best]).toInt(), (sumG[best] / count[best]).toInt(), (sumB[best] / count[best]).toInt())
            an > 0 -> Color.rgb((ar / an).toInt(), (ag / an).toInt(), (ab / an).toInt())
            else -> Color.GRAY
        }
    }

    private fun accentColor(context: Context, s: Settings): Int {
        if (s.accent == "auto" && Build.VERSION.SDK_INT >= 31) return context.getColor(android.R.color.system_accent1_300)
        return runCatching { Color.parseColor(s.accent) }.getOrDefault(Color.rgb(244, 211, 94))
    }

    private fun mix(a: Int, b: Int, t: Float) = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
    )

    private fun hueShift(c: Int, degrees: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(c, hsv)
        hsv[0] = (hsv[0] + degrees) % 360f
        return Color.HSVToColor(hsv)
    }
}
