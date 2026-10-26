package com.mytime.launcher.ui

import android.graphics.Typeface
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.fillMaxSize
import com.mytime.launcher.icons.BuiltinStyle
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.ExperimentalTextApi
import com.mytime.launcher.R
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytime.launcher.data.SavedTheme
import com.mytime.launcher.data.Settings
import com.mytime.launcher.icons.IconPackInfo
import com.mytime.launcher.icons.IconPacks
import com.mytime.launcher.icons.IconShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.drawscope.scale

/** Colour of text and icons drawn straight on the wallpaper. */
val LocalOnWallpaper = compositionLocalOf { Color.White }
val LocalAppFont = compositionLocalOf<FontFamily> { FontFamily.Default }

/** Text style for anything drawn on the wallpaper: wallpaper text colour, app font, soft shadow. */
internal val OnWallpaper: TextStyle
    @Composable get() {
        val color = LocalOnWallpaper.current
        val light = color.luminance() > 0.5f
        return TextStyle(
            color = color,
            fontFamily = LocalAppFont.current,
            shadow = if (light) Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 8f) else null,
        )
    }

internal val Wall: Color @Composable get() = LocalOnWallpaper.current
internal val Accent: Color @Composable get() = MaterialTheme.colorScheme.primary

/**
 * Fonts: the phone's own families, the Google Fonts used by Niagara's themes (bundled in
 * res/font, SIL Open Font License, licences in assets/licenses/fonts), and a file of your own.
 */
enum class FontChoice(val title: String) {
    Default("Default"),
    Light("Light"),
    Condensed("Condensed"),
    Serif("Serif"),
    Mono("Monospace"),
    Casual("Casual"),
    Cursive("Cursive"),
    Righteous("Righteous"),
    TsukimiRounded("Tsukimi Rounded"),
    Lexend("Lexend"),
    Montserrat("Montserrat"),
    Manrope("Manrope"),
    Nunito("Nunito"),
    Onest("Onest"),
    Merienda("Merienda"),
    Kalam("Kalam"),
    Pangolin("Pangolin"),
    Gaegu("Gaegu"),
    SourGummy("Sour Gummy"),
    FuzzyBubbles("Fuzzy Bubbles"),
    Custom("Your font file");

    fun family(filesDir: File): FontFamily = when (this) {
        Default -> FontFamily.Default
        Light -> FontFamily(Typeface.create("sans-serif-light", Typeface.NORMAL))
        Condensed -> FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))
        Serif -> FontFamily.Serif
        Mono -> FontFamily.Monospace
        Casual -> FontFamily(Typeface.create("casual", Typeface.NORMAL))
        Cursive -> FontFamily.Cursive
        Righteous -> FontFamily(Font(R.font.righteous_regular))
        TsukimiRounded -> FontFamily(Font(R.font.tsukimi_rounded_regular), Font(R.font.tsukimi_rounded_bold, FontWeight.Bold))
        Lexend -> variable(R.font.lexend_variable)
        Montserrat -> variable(R.font.montserrat_variable)
        Manrope -> variable(R.font.manrope_variable)
        Nunito -> variable(R.font.nunito_variable)
        Onest -> variable(R.font.onest_variable)
        Merienda -> variable(R.font.merienda_variable)
        Kalam -> FontFamily(Font(R.font.kalam_regular))
        Pangolin -> FontFamily(Font(R.font.pangolin_regular))
        Gaegu -> FontFamily(Font(R.font.gaegu_regular))
        SourGummy -> variable(R.font.sour_gummy_variable)
        FuzzyBubbles -> FontFamily(Font(R.font.fuzzy_bubbles_regular), Font(R.font.fuzzy_bubbles_bold, FontWeight.Bold))
        Custom -> customFontFile(filesDir).takeIf { it.exists() }
            ?.let { runCatching { FontFamily(Font(it)) }.getOrNull() } ?: FontFamily.Default
    }

    companion object {
        /** One variable-weight file, exposed at the weights the app uses. */
        @OptIn(ExperimentalTextApi::class)
        private fun variable(res: Int) = FontFamily(
            listOf(FontWeight.Light, FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { w ->
                Font(res, w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight)))
            },
        )

        fun of(name: String) = entries.firstOrNull { it.name == name } ?: Default
        fun customFontFile(filesDir: File) = File(File(filesDir, "fonts"), "custom_font")
    }
}

private val ACCENTS = listOf(
    "#F4D35E", "#7FD1B9", "#8AB4F8", "#C58AF9", "#FF8A65", "#F28B82", "#81C995", "#E8EAED",
)

fun parseAccent(hex: String): Color? = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()

/** Applies accent, light/dark scheme, pitch black, font, text size and wallpaper text colour. */
@Composable
fun MyTimeTheme(s: Settings, wallpaperPrefersDarkText: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = when (s.themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val scheme: ColorScheme = remember(s.accent, dark, s.pitchBlack) {
        val base = if (s.accent == "auto" && Build.VERSION.SDK_INT >= 31) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            val accent = parseAccent(s.accent) ?: Color(0xFFF4D35E)
            // A pale accent is unreadable on light menus and a very dark one on dark menus.
            val primary = when {
                !dark && accent.luminance() > 0.55f -> darken(accent, 0.45f)
                dark && accent.luminance() < 0.06f -> Color(0xFFE6E6DE)
                else -> accent
            }
            val onPrimary = if (primary.luminance() > 0.5f) Color.Black else Color.White
            if (dark) darkColorScheme(primary = primary, onPrimary = onPrimary, background = Color(0xFF0F1115), surface = Color(0xFF15181E))
            else lightColorScheme(primary = primary, onPrimary = onPrimary)
        }
        if (dark && s.pitchBlack) base.copy(background = Color.Black, surface = Color.Black, surfaceContainer = Color(0xFF0B0B0B))
        else base
    }
    val font = remember(s.font) { FontChoice.of(s.font).family(context.filesDir) }
    val onWallpaper = when (s.wallpaperText) {
        "light" -> Color.White
        "dark" -> Color(0xFF15171B)
        else -> if (wallpaperPrefersDarkText) Color(0xFF15171B) else Color.White
    }
    val density = LocalDensity.current
    MaterialTheme(colorScheme = scheme, typography = withFont(Typography(), font)) {
        CompositionLocalProvider(
            LocalOnWallpaper provides onWallpaper,
            LocalAppFont provides font,
            LocalDensity provides Density(density.density, density.fontScale * s.fontScale),
            content = content,
        )
    }
}

private fun darken(c: Color, by: Float) = Color(c.red * (1 - by), c.green * (1 - by), c.blue * (1 - by), c.alpha)

private fun withFont(t: Typography, f: FontFamily) = Typography(
    displayLarge = t.displayLarge.copy(fontFamily = f), displayMedium = t.displayMedium.copy(fontFamily = f),
    displaySmall = t.displaySmall.copy(fontFamily = f), headlineLarge = t.headlineLarge.copy(fontFamily = f),
    headlineMedium = t.headlineMedium.copy(fontFamily = f), headlineSmall = t.headlineSmall.copy(fontFamily = f),
    titleLarge = t.titleLarge.copy(fontFamily = f), titleMedium = t.titleMedium.copy(fontFamily = f),
    titleSmall = t.titleSmall.copy(fontFamily = f), bodyLarge = t.bodyLarge.copy(fontFamily = f),
    bodyMedium = t.bodyMedium.copy(fontFamily = f), bodySmall = t.bodySmall.copy(fontFamily = f),
    labelLarge = t.labelLarge.copy(fontFamily = f), labelMedium = t.labelMedium.copy(fontFamily = f),
    labelSmall = t.labelSmall.copy(fontFamily = f),
)

/* ---------------------------------------------------------------- Look & feel screen */

@Composable
fun AppearanceScreen(vm: LauncherViewModel, state: LauncherState, actions: SystemActions) {
    EditorScaffold("Look & feel", { vm.screen.value = Screen.Settings }, content = appearanceItems(vm, state, actions))
}

/** The Look & feel rows, shared by its own screen and the Themes tab of settings. */
@Composable
fun appearanceItems(vm: LauncherViewModel, state: LauncherState, actions: SystemActions): LazyListScope.() -> Unit {
    val s = state.settings
    val saved by vm.savedThemes.collectAsState()
    val context = LocalContext.current
    val packLabel = remember(s.iconPack) {
        val builtin = BuiltinStyle.fromPack(s.iconPack)
        if (s.iconPack.isBlank()) "Phone default icons"
        else if (builtin != null) "MyTime icons · ${builtin.title}"
        else runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(s.iconPack, 0)).toString() }
            .getOrDefault(s.iconPack)
    }
    fun set(t: (Settings) -> Settings) = vm.updateSettings(t)

    return {
        item { Header("Themes") }
        item {
            val count = com.mytime.launcher.theme.ThemeCatalog.GROUPS.sumOf { it.themes.size }
            ActionRow("Browse themes", "$count looks: MyTime, open-source palettes (Catppuccin, Nord, Dracula…) and Niagara's themes") {
                vm.screen.value = Screen.ThemeGallery
            }
        }
        item { ActionRow("Save current look as a theme", "${saved.size} saved") { vm.saveCurrentTheme() } }
        items(saved, key = { "t_" + it.name }) { t ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t.name, Modifier.weight(1f))
                TextButton(onClick = { vm.applyTheme(t, withWallpaper = false) }) { Text("Apply") }
                TextButton(onClick = { vm.deleteTheme(t) }) { Text("Delete") }
            }
        }

        item { Header("Colour") }
        item {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 6.dp)) {
                Text("Accent colour", style = MaterialTheme.typography.bodyLarge)
                Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(selected = s.accent == "auto", onClick = { set { it.copy(accent = "auto") } },
                        label = { Text(if (Build.VERSION.SDK_INT >= 31) "From wallpaper" else "Auto") })
                    ACCENTS.forEach { hex ->
                        val c = parseAccent(hex)!!
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(c)
                                .border(3.dp, if (s.accent.equals(hex, true)) LocalContentColor.current else Color.Transparent, CircleShape)
                                .clickable { set { it.copy(accent = hex) } },
                        )
                    }
                }
            }
        }
        item { ChipRow("Menus and settings", listOf("system" to "Follow phone", "dark" to "Dark", "light" to "Light"), s.themeMode) { v -> set { it.copy(themeMode = v) } } }
        item { ToggleRow("Pitch black", "True black in dark mode; saves battery on OLED screens", s.pitchBlack) { v -> set { it.copy(pitchBlack = v) } } }
        item { ChipRow("Text on the wallpaper", listOf("auto" to "From wallpaper", "light" to "Light", "dark" to "Dark"), s.wallpaperText) { v -> set { it.copy(wallpaperText = v) } } }
        item { ActionRow("Change wallpaper", "Opens your phone's wallpaper picker") { actions.changeWallpaper() } }

        item { Header("Font") }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FontChoice.entries.filter { it != FontChoice.Custom || FontChoice.customFontFile(context.filesDir).exists() }.forEach { f ->
                    val family = remember(f) { f.family(context.filesDir) }
                    FilterChip(selected = s.font == f.name, onClick = { set { it.copy(font = f.name) } },
                        label = { Text(f.title, fontFamily = family) })
                }
            }
        }
        item { ActionRow("Use a font file…", "A .ttf or .otf from your phone") { actions.pickFontFile() } }
        item { FontLicencesRow() }
        item {
            ChipRow("Text size", listOf("0.85" to "Small", "1.0" to "Normal", "1.15" to "Large", "1.3" to "Huge"),
                when { s.fontScale < 0.95f -> "0.85"; s.fontScale < 1.07f -> "1.0"; s.fontScale < 1.22f -> "1.15"; else -> "1.3" }) { v ->
                set { it.copy(fontScale = v.toFloat()) }
            }
        }

        item { Header("Icons") }
        item { ActionRow("Icon pack", packLabel) { vm.screen.value = Screen.PickIconPack } }
        item {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 6.dp)) {
                Text("Icon shape", style = MaterialTheme.typography.bodyLarge)
                Text("Applies to the phone's own icons; most icon packs draw their own shapes.",
                    style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.7f))
                Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconShape.entries.forEach { shape -> ShapeSwatch(shape, s.iconShape == shape.name) { set { it.copy(iconShape = shape.name) } } }
                }
            }
        }
        item {
            ChipRow("Icon size", listOf("80" to "80%", "90" to "90%", "100" to "100%", "115" to "115%", "130" to "130%"),
                s.iconSizePct.toString()) { v -> set { it.copy(iconSizePct = v.toInt()) } }
        }
        item { ToggleRow("Reshape old icons", "Put icons without a shape on a white background so they match", s.reshapeLegacy) { v -> set { it.copy(reshapeLegacy = v) } } }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun ChipRow(title: String, options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 24.dp, vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Row(Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, label) -> FilterChip(selected = selected == value, onClick = { onPick(value) }, label = { Text(label) }) }
        }
    }
}

@Composable
private fun ShapeSwatch(shape: IconShape, selected: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val fg = LocalContentColor.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(4.dp)) {
        Canvas(Modifier.size(44.dp)) {
            val p = shape.path()
            if (p == null) {
                drawCircle(fg.copy(alpha = 0.25f))
            } else {
                scale(size.width / 100f, size.height / 100f, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                    drawPath(p.asComposePath(), if (selected) accent else fg.copy(alpha = 0.35f))
                }
            }
        }
        Text(shape.title, style = MaterialTheme.typography.labelSmall, color = if (selected) accent else fg)
    }
}

/** A popular icon pack to install: Play Store id, and F-Droid when it is there too. */
private data class PopularPack(val name: String, val note: String, val playId: String?, val fdroidId: String? = null, val search: String = name)

/** Well-known free packs. The ones with ids were checked on their project pages. */
private val POPULAR_PACKS = listOf(
    PopularPack("Arcticons", "15,000+ line icons. Free and open source (CC BY-SA 4.0).", "com.donnnno.arcticons", "com.donnnno.arcticons"),
    PopularPack("Lawnicons", "Themed line icons from the Lawnchair team. Free and open source (Apache 2.0).", "app.lawnchair.lawnicons.play"),
    PopularPack("Delta Icons", "Colourful, consistent icons. Free and open source.", null, search = "Delta icon pack"),
    PopularPack("Pix Material You", "Icons that follow your wallpaper colours.", null, search = "Pix Material You icon pack"),
    PopularPack("Whicons", "Clean white icons. Free.", null, search = "Whicons icon pack"),
    PopularPack("Glim", "2,500+ Material-style icons.", null, search = "Glim icon pack"),
    PopularPack("Verticons", "Vertical card-style icons.", null, search = "Verticons icon pack"),
)

/** MyTime's own icon styles, installed icon packs, and popular packs to get. */
@Composable
fun PickIconPackScreen(vm: LauncherViewModel, state: LauncherState, actions: SystemActions) {
    val context = LocalContext.current
    val packs by produceState<List<Pair<IconPackInfo, Int>>?>(null) {
        value = withContext(Dispatchers.IO) {
            IconPacks.installed(context).map { it to (IconPacks.get(context, it.packageName)?.coverage ?: 0) }
        }
    }
    // A few of your own apps to preview each built-in style with.
    val sample = remember(state.allApps, state.favorites) {
        (state.favorites + state.suggestions + state.visibleApps).distinctBy { it.packageName }.take(5)
    }
    val current = state.settings.iconPack
    EditorScaffold("Icon pack", { vm.screen.value = Screen.Settings }) {
        item { Header("MyTime icons") }
        item {
            Text("Niagara-style looks for every app, drawn from Lawnicons (10,000+ open-source line icons). Apps without one use Android's monochrome icon or their first letter.",
                style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
        }
        items(BuiltinStyle.entries, key = { "b_" + it.id }) { style ->
            BuiltinStyleRow(style, current == style.packId, sample, state.settings) {
                vm.updateSettings { s -> s.copy(iconPack = style.packId) }
            }
        }

        item { Header("On your phone") }
        item {
            ActionRow("Phone default icons" + if (current.isBlank()) "  ✓" else "", "Uses your chosen icon shape") {
                vm.updateSettings { it.copy(iconPack = "") }
            }
        }
        val list = packs
        if (list == null) item { Text("Looking for icon packs…", Modifier.padding(24.dp)) }
        else if (list.isEmpty()) item {
            Text("No icon packs installed yet. Packs made for Nova, ADW or other launchers work in MyTime; some popular ones are below.",
                Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
        }
        list?.let {
            items(it, key = { p -> p.first.packageName }) { (info, coverage) ->
                ActionRow(info.label + if (current == info.packageName) "  ✓" else "", "$coverage app icons") {
                    vm.updateSettings { s -> s.copy(iconPack = info.packageName) }
                }
            }
        }

        item { Header("Popular icon packs") }
        items(POPULAR_PACKS, key = { "p_" + it.name }) { p ->
            val installed = list?.any { (info, _) -> info.packageName == p.playId || info.packageName == p.fdroidId } == true
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text(p.name + if (installed) "  · installed" else "", style = MaterialTheme.typography.bodyLarge)
                Text(p.note, style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.7f))
                if (!installed) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { if (p.playId != null) actions.openStoreApp(p.playId) else actions.playStoreSearch(p.search) }) {
                        Text("Play Store")
                    }
                    p.fdroidId?.let { id -> TextButton(onClick = { actions.openUrl("https://f-droid.org/packages/$id/") }) { Text("F-Droid") } }
                }
            }
        }
        item { ActionRow("More icon packs", "Search the Play Store") { actions.playStoreSearch("icon pack") } }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun BuiltinStyleRow(style: BuiltinStyle, selected: Boolean, sample: List<com.mytime.launcher.data.AppInfo>, settings: Settings, onClick: () -> Unit) {
    val px = with(LocalDensity.current) { 40.dp.roundToPx() }
    val preview = settings.copy(iconPack = style.packId)
    val sig = com.mytime.launcher.icons.IconLoader.signature(preview)
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(style.title + if (selected) "  ✓" else "", style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else LocalContentColor.current)
            Spacer(Modifier.width(8.dp))
            Text(style.description, style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.7f))
        }
        // Outline styles are meant for the wallpaper, so preview them on a dark strip.
        Row(
            Modifier.padding(top = 6.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1A1C22)).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            sample.forEach { app ->
                val img by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, app.key, sig) {
                    value = com.mytime.launcher.data.Graph.apps.previewIcon(app, px, preview)
                }
                Box(Modifier.size(40.dp)) { img?.let { androidx.compose.foundation.Image(it, null, Modifier.fillMaxSize()) } }
            }
        }
    }
}

/** Licences for the bundled open-source fonts (SIL OFL) and Lawnicons (Apache 2.0). */
@Composable
private fun FontLicencesRow() {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    ActionRow("Open-source licences", "Bundled Google Fonts (SIL OFL 1.1) and Lawnicons (Apache 2.0)") { open = true }
    if (open) {
        val text by produceState("") {
            value = withContext(Dispatchers.IO) {
                val dir = "licenses/fonts"
                val fonts = context.assets.list(dir).orEmpty().sorted().joinToString("\n\n") { f ->
                    "— ${f.removeSuffix("-OFL.txt")} —\n" + context.assets.open("$dir/$f").bufferedReader().use { it.readText() }
                }
                val lawnicons = context.assets.open("licenses/lawnicons-LICENSE.txt").bufferedReader().use { it.readText() }
                "— Lawnicons (github.com/LawnchairLauncher/lawnicons) —\n$lawnicons\n\n$fonts"
            }
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Open-source licences") },
            text = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    Text(text.ifBlank { "Loading…" }, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } },
        )
    }
}
