First public release of **GlassHome**, an ad-free, tvOS-inspired home screen for the Amazon Fire TV Stick. No root required, and nothing in Fire OS is disabled: Amazon's launcher keeps running in the background and everything is reversible.

## Features
- **Top Shelf layout**: full-screen photo fading into a frosted-glass dock, with every installed app below
- **tvOS-style focus**: spring animation, soft shadow, parallax tilt and a specular highlight
- **Smart tiles**: uses each app's TV banner, or generates a tile from its icon; custom tiles can be uploaded from a computer
- **Space wallpapers** from James Webb and Hubble (1920×1080), with an optional new wallpaper every day
- **Recent apps** row, **hide apps** from the drawer, **memory** panel with "close background apps"
- **Top bar**: search (opens Fire TV's "Find" tab), Fire TV settings shortcuts, Amazon home, options, **weather** (Open-Meteo, no account) and **Wi-Fi** status
- **Coexists with Amazon's screensaver**: no wake locks, no keep-screen-on; the screensaver always starts and you return to GlassHome afterwards
- **Backup and restore** of your configuration (dock, hidden apps, tiles, wallpaper, weather city)

## Install
1. On the Fire TV: Settings → My Fire TV → About → press OK 7 times on the device name, then enable **ADB debugging** in Developer Options.
2. Download `GlassHome-1.1.0.apk` below and place it in the `dist/` folder of this repository.
3. Run `./scripts/install.sh <fire-tv-ip>` (macOS/Linux) or `.\scripts\install.ps1 <fire-tv-ip>` (Windows).
4. To undo everything: `./scripts/restore-defaults.sh <fire-tv-ip> --uninstall`.

Full step-by-step guide in [README.md](README.md) (English) and [README.it.md](README.it.md) (Italian).

## Tested on
Fire TV Stick 3rd gen (AFTSSS), Fire OS 7.7.1.7. Fire OS 8 should work but is untested; Vega OS devices are not supported.

## Notes
- The Home button is redirected with an accessibility overlay (the same approach as FireFrame). A future Fire OS update could break it; re-running `install` usually fixes it.
- Alexa cannot be launched from GlassHome: Fire OS only starts it from the remote's microphone button, which keeps working as usual.
- Personal project, provided as is, without support or warranty (MIT).

## Verify the download
SHA-256 of `GlassHome-1.1.0.apk`:
`98f07f5bfaf583e47c2f7982c13a08530ea2065f7a313c0c8eabf4dda0dc06a7`

## Credits
Based on [FireFrame](https://github.com/dyascj/FireFrame) by CJ Dyas (MIT); wake-up handling inspired by [dumbtv](https://github.com/humblemuzzu/dumbtv). Wallpapers: NASA, ESA, CSA, STScI. Font: Inter (OFL). Weather data: Open-Meteo (CC BY 4.0). Not affiliated with Amazon or Apple.
