package com.mytime.launcher.icons

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Xml
import org.xmlpull.v1.XmlPullParser

data class IconPackInfo(val packageName: String, val label: String)

/**
 * A third-party icon pack in the common ADW/Nova format: an appfilter.xml that maps
 * "ComponentInfo{package/activity}" to drawable names, plus optional iconback / iconmask /
 * iconupon / scale entries for drawing apps the pack doesn't cover.
 */
class IconPack(private val context: Context, val packageName: String) {

    private val res: Resources = context.packageManager.getResourcesForApplication(packageName)
    private val components = HashMap<String, String>()
    private val backs = ArrayList<String>()
    var maskName: String? = null; private set
    var uponName: String? = null; private set
    var scale = 1f; private set

    init {
        parse()
    }

    private fun parse() {
        val parser = openAppFilter() ?: return
        try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "item" -> {
                            val comp = parser.getAttributeValue(null, "component")
                            val drawable = parser.getAttributeValue(null, "drawable")
                            if (comp != null && drawable != null) normalize(comp)?.let { components.putIfAbsent(it, drawable) }
                        }
                        "iconback" -> for (i in 0 until parser.attributeCount) parser.getAttributeValue(i)?.let(backs::add)
                        "iconmask" -> maskName = parser.getAttributeValue(null, "img1") ?: parser.getAttributeValue(0)
                        "iconupon" -> uponName = parser.getAttributeValue(null, "img1") ?: parser.getAttributeValue(0)
                        "scale" -> scale = parser.getAttributeValue(null, "factor")?.toFloatOrNull() ?: 1f
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {
            // A malformed appfilter still gives whatever was read before the error.
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun openAppFilter(): XmlPullParser? {
        val id = res.getIdentifier("appfilter", "xml", packageName)
        if (id != 0) return res.getXml(id)
        return runCatching {
            Xml.newPullParser().apply { setInput(res.assets.open("appfilter.xml"), "UTF-8") }
        }.getOrNull()
    }

    /** "ComponentInfo{pkg/.Cls}" → "pkg/pkg.Cls". */
    private fun normalize(raw: String): String? {
        val inner = raw.substringAfter('{', "").substringBefore('}', "")
        val cn = ComponentName.unflattenFromString(inner) ?: return null
        return cn.packageName + "/" + cn.className
    }

    /** The pack's own icon for this activity, if it has one. */
    fun iconFor(component: ComponentName): Drawable? {
        val name = components[component.packageName + "/" + component.className] ?: return null
        return drawable(name)
    }

    val hasMasking: Boolean get() = backs.isNotEmpty() || maskName != null || uponName != null

    /** Back plate for an unsupported app; picked by package so each app keeps the same one. */
    fun backFor(component: ComponentName): Drawable? {
        if (backs.isEmpty()) return null
        return drawable(backs[Math.floorMod(component.packageName.hashCode(), backs.size)])
    }

    fun mask(): Drawable? = maskName?.let(::drawable)
    fun upon(): Drawable? = uponName?.let(::drawable)

    @SuppressLint("DiscouragedApi", "UseCompatLoadingForDrawables")
    private fun drawable(name: String): Drawable? {
        val id = res.getIdentifier(name, "drawable", packageName).takeIf { it != 0 }
            ?: res.getIdentifier(name, "mipmap", packageName).takeIf { it != 0 }
            ?: return null
        return runCatching { res.getDrawable(id, null) }.getOrNull()
    }

    val coverage: Int get() = components.size
}

object IconPacks {
    /** Intents icon packs declare so launchers can find them (ADW, Nova, Apex, GO, etc.). */
    private val ACTIONS = listOf(
        "org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.anddoes.launcher.THEME",
        "com.gau.go.launcherex.theme", "com.teslacoilsw.launcher.THEME",
    )
    private const val CATEGORY = "com.fede.launcher.THEME_ICONPACK"

    private var cached: IconPack? = null

    fun installed(context: Context): List<IconPackInfo> {
        val pm = context.packageManager
        val found = LinkedHashMap<String, IconPackInfo>()
        val intents = ACTIONS.map { Intent(it) } + Intent(Intent.ACTION_MAIN).addCategory(CATEGORY)
        for (intent in intents) {
            for (ri in pm.queryIntentActivities(intent, PackageManager.GET_META_DATA)) {
                val pkg = ri.activityInfo.packageName
                if (pkg !in found) found[pkg] = IconPackInfo(pkg, ri.loadLabel(pm).toString())
            }
        }
        return found.values.sortedBy { it.label.lowercase() }
    }

    /** The selected pack, parsed once and kept until the choice changes. */
    @Synchronized
    fun get(context: Context, packageName: String): IconPack? {
        if (packageName.isBlank()) return null
        cached?.let { if (it.packageName == packageName) return it }
        return runCatching { IconPack(context, packageName) }.getOrNull().also { cached = it }
    }

    @Synchronized
    fun clear() { cached = null }
}
