package com.mytime.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytime.launcher.data.SavedTheme
import com.mytime.launcher.theme.ThemeCatalog
import com.mytime.launcher.theme.ThemeWallpaper

/** All ready-made looks, three per row, each previewed on its own wallpaper. */
@Composable
fun ThemeGalleryScreen(vm: LauncherViewModel) {
    val saved by vm.savedThemes.collectAsState()
    var applying by remember { mutableStateOf<SavedTheme?>(null) }
    val groups = buildList {
        if (saved.isNotEmpty()) add(com.mytime.launcher.theme.ThemeGroup("Your themes", "Looks you saved.", saved))
        addAll(ThemeCatalog.GROUPS)
    }
    EditorScaffold("Themes", { vm.screen.value = Screen.Settings }) {
        groups.forEach { g ->
            item(key = "h_" + g.title) {
                Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 6.dp)) {
                    Text("${g.title} (${g.themes.size})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text(g.note, style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.7f))
                }
            }
            g.themes.chunked(3).forEachIndexed { i, row ->
                item(key = "r_${g.title}_$i") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { t -> ThemePreview(t, Modifier.weight(1f)) { applying = t } }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
    applying?.let { t -> ApplyThemeDialog(t, onDismiss = { applying = null }) { withWallpaper -> vm.applyTheme(t, withWallpaper); applying = null } }
}

@Composable
internal fun ThemePreview(t: SavedTheme, modifier: Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    val wallpaper = remember(t.wallpaper) {
        if (t.wallpaper.isEmpty()) null else ThemeWallpaper.render(t.wallpaper, 180, 320).asImageBitmap()
    }
    val accent = parseAccent(t.accent) ?: MaterialTheme.colorScheme.primary
    val font = remember(t.font) { FontChoice.of(t.font).family(context.filesDir) }
    // Text colour as the wallpaper would ask for it: dark on light backgrounds.
    val topColor = t.wallpaper.firstOrNull()?.let(::parseAccent)
    val onWall = if (topColor != null && topColor.luminance() > 0.5f) Color(0xFF15171B) else Color.White
    Box(
        modifier
            .aspectRatio(0.6f)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, LocalContentColor.current.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        if (wallpaper != null) Image(wallpaper, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Column(Modifier.padding(10.dp)) {
            Text("12:30", fontFamily = font, color = onWall, fontSize = 22.sp, fontWeight = FontWeight.Light)
            Box(Modifier.padding(vertical = 6.dp).size(width = 28.dp, height = 5.dp).clip(CircleShape).background(accent))
            repeat(3) {
                Box(Modifier.padding(vertical = 3.dp).size(width = 44.dp, height = 6.dp).clip(CircleShape).background(onWall.copy(alpha = 0.55f)))
            }
        }
        Text(
            t.name, color = onWall, fontFamily = font, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
        )
    }
}

@Composable
internal fun ApplyThemeDialog(t: SavedTheme, onDismiss: () -> Unit, onApply: (withWallpaper: Boolean) -> Unit) {
    var withWallpaper by remember { mutableStateOf(t.wallpaper.isNotEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Apply ${t.name}?") },
        text = {
            Column {
                Text("Changes the accent colour, font, clock style and icon shape.")
                if (t.wallpaper.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp).clickable { withWallpaper = !withWallpaper }) {
                        Checkbox(checked = withWallpaper, onCheckedChange = { withWallpaper = it })
                        Text("Also set its wallpaper (replaces your home-screen wallpaper)")
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onApply(withWallpaper) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Ready-made themes as swipeable rows, one per group; shown at the top of the Themes tab. */
@Composable
fun ReadyMadeThemes(vm: LauncherViewModel) {
    val saved by vm.savedThemes.collectAsState()
    var applying by remember { mutableStateOf<SavedTheme?>(null) }
    val groups = buildList {
        if (saved.isNotEmpty()) add(com.mytime.launcher.theme.ThemeGroup("Your themes", "", saved))
        addAll(ThemeCatalog.GROUPS)
    }
    Column {
        groups.forEach { g ->
            Text("${g.title} (${g.themes.size})", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 6.dp))
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(g.themes.size, key = { g.title + it }) { i ->
                    ThemePreview(g.themes[i], Modifier.width(104.dp)) { applying = g.themes[i] }
                }
            }
        }
    }
    applying?.let { t -> ApplyThemeDialog(t, onDismiss = { applying = null }) { withWallpaper -> vm.applyTheme(t, withWallpaper); applying = null } }
}
