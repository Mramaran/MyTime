package com.mytime.launcher.icons

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.os.UserHandle
import androidx.core.graphics.drawable.toBitmap
import com.mytime.launcher.data.Settings

/** Icon shapes on a 100×100 box, as in Niagara (circle, squircle, rounded square) plus a teardrop. */
enum class IconShape(val title: String) {
    System("Phone default"),
    Circle("Circle"),
    Squircle("Squircle"),
    RoundedSquare("Rounded square"),
    Teardrop("Teardrop");

    fun path(): Path? = when (this) {
        System -> null
        Circle -> Path().apply { addCircle(50f, 50f, 50f, Path.Direction.CW) }
        Squircle -> Path().apply {
            moveTo(50f, 0f)
            cubicTo(8f, 0f, 0f, 8f, 0f, 50f)
            cubicTo(0f, 92f, 8f, 100f, 50f, 100f)
            cubicTo(92f, 100f, 100f, 92f, 100f, 50f)
            cubicTo(100f, 8f, 92f, 0f, 50f, 0f)
            close()
        }
        RoundedSquare -> Path().apply { addRoundRect(0f, 0f, 100f, 100f, 24f, 24f, Path.Direction.CW) }
        Teardrop -> Path().apply {
            addRoundRect(0f, 0f, 100f, 100f, floatArrayOf(50f, 50f, 50f, 50f, 16f, 16f, 50f, 50f), Path.Direction.CW)
        }
    }

    companion object {
        fun of(name: String) = entries.firstOrNull { it.name == name } ?: System
    }
}

/**
 * Builds the icon shown for an app:
 * 1. the icon pack's own drawing, if the pack covers the app;
 * 2. otherwise, with a pack, the original icon on the pack's back plate, masked and overlaid;
 * 3. otherwise the phone's icon in the chosen shape. Old non-adaptive icons are placed on a
 *    white shape when "reshape legacy icons" is on, so they match the rest.
 * Work-profile icons get the system briefcase badge last.
 */
object IconLoader {

    /** Changes whenever any setting that affects icons changes, for cache keys. */
    fun signature(s: Settings): String {
        val base = "${s.iconPack}|${s.iconShape}|${s.reshapeLegacy}"
        // Built-in styles are drawn in the accent and wallpaper text colours.
        return if (BuiltinStyle.fromPack(s.iconPack) != null) "$base|${s.accent}|${s.wallpaperText}" else base
    }

    fun load(context: Context, activity: LauncherActivityInfo, user: UserHandle, isWork: Boolean, size: Int, s: Settings): Bitmap {
        val builtin = BuiltinStyle.fromPack(s.iconPack)
        val pack = if (builtin == null) IconPacks.get(context, s.iconPack) else null
        val original = activity.getIcon(0)
        val packIcon = pack?.iconFor(activity.componentName)
        val bmp = when {
            builtin != null -> BuiltinIcons.render(context, activity, size, builtin, s)
            packIcon != null -> packIcon.toBitmap(size, size, Bitmap.Config.ARGB_8888)
            pack != null && pack.hasMasking -> masked(pack, activity, original, size)
            else -> shaped(original, IconShape.of(s.iconShape), size, s.reshapeLegacy)
        }
        if (!isWork) return bmp
        val badged = context.packageManager.getUserBadgedIcon(BitmapDrawable(context.resources, bmp), user)
        return badged.toBitmap(size, size, Bitmap.Config.ARGB_8888)
    }

    private fun shaped(d: Drawable, shape: IconShape, size: Int, reshapeLegacy: Boolean): Bitmap {
        val adaptive = d as? AdaptiveIconDrawable
            ?: if (reshapeLegacy) AdaptiveIconDrawable(ColorDrawable(Color.WHITE), InsetDrawable(d, LEGACY_INSET)) else null
        if (adaptive == null) return d.toBitmap(size, size, Bitmap.Config.ARGB_8888)
        val path = shape.path() ?: return adaptive.toBitmap(size, size, Bitmap.Config.ARGB_8888)

        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val scaled = Path(path).apply { transform(Matrix().apply { setScale(size / 100f, size / 100f) }) }
        canvas.clipPath(scaled)
        // Adaptive layers are 108dp with the middle 72dp visible: draw them 1.5× and centred.
        val layer = (size * 1.5f).toInt()
        val offset = (size - layer) / 2
        adaptive.background?.apply { setBounds(offset, offset, offset + layer, offset + layer); draw(canvas) }
        adaptive.foreground?.apply { setBounds(offset, offset, offset + layer, offset + layer); draw(canvas) }
        return out
    }

    private fun masked(pack: IconPack, activity: LauncherActivityInfo, original: Drawable, size: Int): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        pack.backFor(activity.componentName)?.apply { setBounds(0, 0, size, size); draw(canvas) }

        // The original icon, scaled by the pack's factor, with the mask cut out of it.
        val inner = (size * pack.scale).toInt().coerceAtLeast(1)
        val icon = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val ic = Canvas(icon)
        val o = (size - inner) / 2
        original.toBitmap(inner, inner, Bitmap.Config.ARGB_8888).let { ic.drawBitmap(it, o.toFloat(), o.toFloat(), null) }
        pack.mask()?.let { mask ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
            ic.drawBitmap(mask.toBitmap(size, size, Bitmap.Config.ARGB_8888), 0f, 0f, paint)
        }
        canvas.drawBitmap(icon, 0f, 0f, null)
        pack.upon()?.apply { setBounds(0, 0, size, size); draw(canvas) }
        return out
    }

    private const val LEGACY_INSET = 0.22f
}
