# MyTime

A minimal Android launcher built to cut screen time. Your few favourite apps on a clean home screen, everything else one swipe away, and gentle nudges when leisure apps start eating your day.

Download the latest APK from [Releases](../../releases).

## Features

**Screen time**
- Today's screen time and unlocks on the home screen; a weekly report with a bar per day, top apps, and this week vs last.
- **Leisure apps**: mark the apps you overuse. They turn greyscale, can show a 5-second pause before opening, and count towards an optional daily limit.
- **Usage Breaker**: a quiet reminder at 5, 10, 20, 30, 45, 60… minutes in leisure apps.
- **Focus mode**: one tap (or a Quick Settings tile) hides leisure apps for 25 min, 1 h, 2 h or until you turn it off.
- **Notification Summary**: non-urgent notifications from chosen apps arrive together about every 6 hours; calls, alarms and conversations still come through.

**Home screen**
- Favourites, often-used suggestions, a clock in 8 styles, date, next alarm, battery, weather (Open-Meteo) and upcoming calendar events.
- Swipe up for the full A–Z app list with a wave alphabet; swipe down for notifications.
- Search with a calculator and web search.
- Inline notifications with quick replies, a media player, and home-screen widgets.

**Look**
- 56 ready-made themes: MyTime's own, 16 open-source palettes (Catppuccin, Nord, Dracula, Gruvbox, Tokyo Night, Rosé Pine, Everforest, Kanagawa, Solarized, One Dark) and 36 themes in the style of Niagara Launcher's, each with a generated wallpaper.
- Accent colour, light/dark/pitch-black menus, 20 fonts, icon shapes and sizes.
- 7 built-in icon styles (Outline, Outline Chroma, Themed, Material, Sticker, Clicks, Gradient) drawn from Lawnicons, plus support for any Nova/ADW icon pack.

Everything is free. MyTime has no ads, no accounts and no analytics; screen-time data never leaves the phone. The only network calls are weather (Open-Meteo) and web search suggestions you ask for.

## Install

1. Download `MyTime-<version>.apk` from [Releases](../../releases) and open it on your phone (allow installs from that app when asked).
2. Open MyTime → settings → Features, and work through **Setup**: set MyTime as the home app, then allow usage access, notifications and notification access.
3. For double-tap to lock on Android 13+: App info → ⋮ → *Allow restricted settings*, then turn on *MyTime screen lock* in Accessibility.

Requires Android 8.0 or newer.

## Build

```bash
./gradlew assembleDebug
```

A release build needs your own signing key: create `keystore.properties` in the project root with `storeFile`, `storePassword`, `keyAlias` and `keyPassword`. Without it, release builds fall back to the debug key.

## Credits

- [Lawnicons](https://github.com/LawnchairLauncher/lawnicons): the line icons behind MyTime's built-in icon styles. Apache License 2.0.
- [Google Fonts](https://github.com/google/fonts): Righteous, Tsukimi Rounded, Lexend, Montserrat, Manrope, Nunito, Onest, Merienda, Kalam, Pangolin, Gaegu, Sour Gummy and Fuzzy Bubbles. SIL Open Font License 1.1.
- [AndroidSVG](https://github.com/BigBadaboom/androidsvg) and [EvalEx](https://github.com/ezylang/EvalEx), under their own licences.
- Colour palettes from [Catppuccin](https://catppuccin.com), [Nord](https://www.nordtheme.com), [Dracula](https://draculatheme.com), [Gruvbox](https://github.com/morhetz/gruvbox), [Tokyo Night](https://github.com/folke/tokyonight.nvim), [Rosé Pine](https://rosepinetheme.com), [Everforest](https://github.com/sainnhe/everforest), [Kanagawa](https://github.com/rebelot/kanagawa.nvim), [Solarized](https://ethanschoonover.com/solarized/) and [One Dark](https://github.com/navarasu/onedark.nvim).
- Weather data by [Open-Meteo](https://open-meteo.com) (CC BY 4.0).

The licence texts for the bundled icons and fonts are in `app/src/main/assets/licenses/` and inside the app under Themes → Open-source licences.

MyTime is inspired by Niagara Launcher and is not affiliated with it. It contains none of Niagara's code, icons or wallpapers.
