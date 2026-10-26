package com.mytime.launcher.theme

import com.mytime.launcher.data.SavedTheme

data class ThemeGroup(val title: String, val note: String, val themes: List<SavedTheme>)

/**
 * Ready-made looks. Each sets the accent, menu colours, font, clock style, icon shape and a
 * generated gradient wallpaper.
 *
 * Open-source palettes: colours from each project's official palette (Catppuccin, Nord,
 * Dracula, Gruvbox, Tokyo Night, Rosé Pine, Everforest, Kanagawa, Solarized, One Dark).
 *
 * Niagara: the accent colours and fonts of Niagara Launcher's 36 built-in themes (the fonts are
 * the same open-source Google Fonts, bundled). Niagara's wallpapers are artists' paintings, so
 * these use generated wallpapers in the same mood.
 */
object ThemeCatalog {

    private fun t(
        name: String, accent: String, mode: String, font: String, clock: String, shape: String,
        wallpaper: List<String>, iconSize: Int = 100, fontScale: Float = 1f, pitchBlack: Boolean = false,
        icons: String = "",
    ) = SavedTheme(name, accent, mode, pitchBlack, "auto", font, fontScale, icons, shape, iconSize, clock, wallpaper)

    val MYTIME = listOf(
        t("Classic", "#F4D35E", "system", "Default", "Basic", "System", listOf("#1B1D24", "#3A3320")),
        t("Calm", "#7FD1B9", "system", "Serif", "Words", "Circle", listOf("#10201C", "#3E7F6E")),
        t("Focus", "#E8EAED", "dark", "Mono", "Bold", "Squircle", listOf("#000000", "#1A1A1A"), iconSize = 90, pitchBlack = true),
        t("Sunset", "#FF8A65", "system", "Condensed", "Vertical", "Teardrop", listOf("#2B1630", "#FF8A65"), iconSize = 110, fontScale = 1.05f),
    )

    val OPEN_SOURCE = listOf(
        t("Catppuccin Mocha", "#CBA6F7", "dark", "Default", "Basic", "Squircle", listOf("#1E1E2E", "#CBA6F7")),
        t("Catppuccin Latte", "#8839EF", "light", "Default", "Basic", "Squircle", listOf("#EFF1F5", "#EA76CB")),
        t("Nord", "#88C0D0", "dark", "Light", "Words", "Circle", listOf("#2E3440", "#5E81AC")),
        t("Nord Snow", "#5E81AC", "light", "Light", "Basic", "Circle", listOf("#ECEFF4", "#88C0D0")),
        t("Dracula", "#BD93F9", "dark", "Mono", "Bold", "RoundedSquare", listOf("#282A36", "#FF79C6")),
        t("Gruvbox", "#FABD2F", "dark", "Mono", "Flip", "RoundedSquare", listOf("#282828", "#FE8019")),
        t("Gruvbox Light", "#076678", "light", "Mono", "Flip", "RoundedSquare", listOf("#FBF1C7", "#D3869B")),
        t("Tokyo Night", "#7AA2F7", "dark", "Default", "Bold", "Squircle", listOf("#1A1B26", "#BB9AF7")),
        t("Rosé Pine", "#EBBCBA", "dark", "Serif", "Words", "Circle", listOf("#191724", "#EB6F92")),
        t("Rosé Pine Moon", "#C4A7E7", "dark", "Serif", "DayOfWeek", "Circle", listOf("#232136", "#3E8FB0")),
        t("Rosé Pine Dawn", "#D7827E", "light", "Serif", "Words", "Circle", listOf("#FAF4ED", "#EA9D34")),
        t("Everforest", "#A7C080", "dark", "Light", "Analog", "Teardrop", listOf("#2D353B", "#83C092")),
        t("Kanagawa", "#7E9CD8", "dark", "Serif", "Vertical", "Squircle", listOf("#1F1F28", "#D27E99")),
        t("Solarized Dark", "#268BD2", "dark", "Default", "Basic", "RoundedSquare", listOf("#002B36", "#2AA198")),
        t("Solarized Light", "#268BD2", "light", "Default", "Basic", "RoundedSquare", listOf("#FDF6E3", "#B58900")),
        t("One Dark", "#61AFEF", "dark", "Mono", "Bold", "Squircle", listOf("#282C34", "#C678DD")),
    )

    /** Niagara's built-in themes: its accent colours, with the nearest font and a matching gradient. */
    val NIAGARA = listOf(
        t("Plasma", "#7C6DB5", "dark", "Righteous", "Bold", "Circle", listOf("#1A1233", "#7C6DB5"), fontScale = 1.1f, icons = "builtin:material"),
        t("Way Home", "#EE5D46", "dark", "TsukimiRounded", "Basic", "Squircle", listOf("#22141E", "#EE5D46")),
        t("Poppies", "#FDDEDC", "dark", "SourGummy", "Words", "Squircle", listOf("#3A1C22", "#E86A6A"), icons = "builtin:outline"),
        t("Carbon", "#BBBBBB", "dark", "Lexend", "Bold", "RoundedSquare", listOf("#111111", "#3A3A3A"), pitchBlack = true),
        t("Dune", "#C5C5C5", "dark", "Montserrat", "Vertical", "Circle", listOf("#3B2E22", "#C2A27A"), icons = "builtin:outline"),
        t("Cozy Skyline", "#00A1A0", "dark", "TsukimiRounded", "Basic", "Squircle", listOf("#0B1E2A", "#00A1A0"), icons = "builtin:sticker"),
        t("Eucalyptus", "#CACB9B", "dark", "TsukimiRounded", "Words", "Squircle", listOf("#1E241C", "#7C8A64")),
        t("Moody Sunset", "#FBBB1F", "dark", "Merienda", "Basic", "Circle", listOf("#2E1B3F", "#FBBB1F")),
        t("Shore", "#1E99C7", "dark", "Kalam", "Analog", "Circle", listOf("#0A2533", "#1E99C7")),
        t("White", "#FFFFFF", "light", "Manrope", "Basic", "Squircle", listOf("#F7F7F7", "#D9D9D9")),
        t("Vines", "#73962D", "dark", "Montserrat", "Vertical", "Teardrop", listOf("#141B0C", "#73962D")),
        t("Blue Smoke", "#2967C8", "dark", "Nunito", "Flip", "RoundedSquare", listOf("#0B1426", "#2967C8"), icons = "builtin:gradient"),
        t("Forest", "#7F854C", "dark", "FuzzyBubbles", "Words", "Teardrop", listOf("#14170E", "#7F854C"), icons = "builtin:gradient"),
        t("Mountain Path", "#922921", "dark", "Cursive", "DayOfWeek", "Squircle", listOf("#1A1010", "#922921")),
        t("Night Plane", "#C2C2C2", "dark", "Manrope", "Basic", "Circle", listOf("#0C0F1C", "#3A3F5C"), icons = "builtin:sticker"),
        t("Wall Cat", "#95CEEC", "dark", "Pangolin", "Analog", "Circle", listOf("#14202B", "#95CEEC"), icons = "builtin:outline_chroma"),
        t("Blue Mountains", "#E8A25F", "dark", "Lexend", "Bold", "Squircle", listOf("#14223A", "#E8A25F"), icons = "builtin:outline_chroma"),
        t("Purple Clouds", "#AD71D9", "dark", "Manrope", "Words", "Circle", listOf("#1E1229", "#AD71D9")),
        t("Mediterranean Village", "#EBC85C", "dark", "TsukimiRounded", "Basic", "RoundedSquare", listOf("#163452", "#EBC85C")),
        t("Night Frog", "#C9E26C", "dark", "Gaegu", "Flip", "Circle", listOf("#0C150D", "#4F6B2A")),
        t("Red Mountain", "#AC404C", "dark", "Gaegu", "Vertical", "Teardrop", listOf("#1F0A0F", "#AC404C")),
        t("White Clouds", "#5FB2E9", "light", "SourGummy", "Basic", "Circle", listOf("#E6F3FC", "#5FB2E9")),
        t("Dark Forest Road", "#E75A3B", "dark", "Manrope", "Bold", "Squircle", listOf("#0F0C0B", "#E75A3B")),
        t("Lilypad River", "#CD9FAD", "dark", "TsukimiRounded", "Words", "Circle", listOf("#162222", "#CD9FAD")),
        t("Purple Lightning", "#C776D4", "dark", "Montserrat", "Bold", "RoundedSquare", listOf("#120B22", "#C776D4")),
        t("Orange Moon", "#093B6D", "light", "SourGummy", "Analog", "Circle", listOf("#F2A65A", "#093B6D")),
        t("Night Sailboat", "#C2C2C2", "dark", "Righteous", "DayOfWeek", "Squircle", listOf("#08101F", "#2E4766"), icons = "builtin:themed"),
        t("Golden Nova", "#B48705", "dark", "Onest", "Flip", "RoundedSquare", listOf("#0F0C04", "#B48705"), pitchBlack = true),
        t("Blue Summit", "#71AAF1", "dark", "Montserrat", "Basic", "Teardrop", listOf("#0C1828", "#71AAF1")),
        t("Polar Bear", "#EAD87F", "light", "TsukimiRounded", "Words", "Circle", listOf("#E9F1F5", "#9CC3D6")),
        t("Dusk Waterfall", "#C03729", "dark", "FuzzyBubbles", "Vertical", "Squircle", listOf("#1A1220", "#C03729")),
        t("Eclipse", "#E8B674", "dark", "Onest", "Analog", "Circle", listOf("#08080C", "#E8B674"), pitchBlack = true),
        t("Bioluminescent Coast", "#A1C9FF", "dark", "FuzzyBubbles", "Words", "Circle", listOf("#03101C", "#1F5C8A")),
        t("Clicks Smoke", "#FCFDF4", "dark", "FuzzyBubbles", "Flip", "RoundedSquare", listOf("#22231F", "#5A5C52"), icons = "builtin:clicks"),
        t("Clicks Onyx", "#14140F", "light", "Default", "Bold", "RoundedSquare", listOf("#EDEDE6", "#9A9A90"), icons = "builtin:clicks"),
        t("Clicks Clover", "#5D7F5F", "dark", "Default", "Basic", "RoundedSquare", listOf("#101A13", "#5D7F5F"), icons = "builtin:clicks"),
    )

    val GROUPS = listOf(
        ThemeGroup("MyTime", "Made for MyTime.", MYTIME),
        ThemeGroup("Open-source palettes", "Colours from Catppuccin, Nord, Dracula, Gruvbox, Tokyo Night, Rosé Pine, Everforest, Kanagawa, Solarized and One Dark (all MIT-licensed palettes).", OPEN_SOURCE),
        ThemeGroup("Niagara themes", "Niagara Launcher's 36 themes: their colours and fonts, with matching wallpapers drawn by MyTime.", NIAGARA),
    )
}
