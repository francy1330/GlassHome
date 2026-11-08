# GlassHome

**A tvOS-inspired, ad-free home screen for the Amazon Fire TV Stick.**
No root, nothing in Fire OS is disabled, and everything can be undone.

🇮🇹 [Leggi in italiano](README.it.md)

- A full-screen space photo that fades into a **frosted-glass dock** with your favourite apps
- **tvOS-style focus**: tiles lift with a spring, cast a soft shadow, tilt and catch a highlight
- **Recent apps**, **all apps** below, and you can **hide** the apps you never use
- **Space wallpapers** from James Webb and Hubble, a new one every day if you want
- **Weather**, **Wi-Fi** status, **search**, shortcuts to the Fire TV settings
- **Works with Amazon's screensaver** (Amazon Photos): it always starts, and afterwards you are back on GlassHome
- **Backup and restore** your setup from your computer

> **Personal project, no support.** Shared as is, under the MIT licence, without any warranty.
> Based on [FireFrame](https://github.com/dyascj/FireFrame). Not affiliated with Amazon or Apple.

Tested on Fire TV Stick 3rd gen (AFTSSS), Fire OS 7.7.1.7. Fire OS 8 should work but is untested.
Fire TV devices with **Vega OS** (e.g. Fire TV Stick 4K Select) cannot run it.

---

## How it works, in one minute

Fire OS does not let you replace Amazon's home screen, and GlassHome does not try to. It installs
like any other app, plus a small **accessibility service** that notices when Amazon's home screen
opens (for example when you press Home) and shows GlassHome on top of it instantly. Amazon's
launcher keeps running quietly in the background.

You install it **from a computer** (Mac or Windows) on the same Wi-Fi as the Fire TV, using a free
Google tool called **adb**. It takes about 15 minutes the first time. You only need the computer for
installing, updating and a few optional extras: day to day you use the remote.

---

## Step 1 · Prepare the Fire TV (5 minutes)

1. On the Fire TV open **Settings → My Fire TV → About**.
2. Select the device name (e.g. "Fire TV Stick") and press **OK 7 times** until it says you are a developer.
3. Go back: a new entry **Developer Options** appears under My Fire TV. Open it and turn on **ADB debugging**.
4. Find the Fire TV's **IP address**: **Settings → My Fire TV → About → Network**.
   It looks like `192.168.1.50`. Write it down: you will need it below.

## Step 2 · Download GlassHome (2 minutes)

1. On this page click the green **Code** button → **Download ZIP**, then unzip it.
   You get a folder called `GlassHome-main` (you can rename it `GlassHome`).
2. Open **[Releases](../../releases/latest)** and download `GlassHome-<version>.apk`.
3. Inside the GlassHome folder create a folder named **`dist`** and put the `.apk` file in it.

## Step 3 · Get adb (5 minutes)

adb is Google's official tool to talk to Android devices.

1. Download **SDK Platform-Tools** for your system from
   <https://developer.android.com/tools/releases/platform-tools> and unzip it.
   You get a folder called `platform-tools`.
2. Inside the GlassHome folder create the folders **`toolchain`** and, inside it, **`sdk`**,
   then move `platform-tools` there. The result must be:
   `GlassHome/toolchain/sdk/platform-tools/adb` (on Windows `adb.exe`).

> If you already have adb installed (e.g. `brew install android-platform-tools` on a Mac), skip
> this: the scripts find it automatically.

## Step 4 · Open a terminal in the GlassHome folder

**Mac**
1. Open **Terminal** (Applications → Utilities → Terminal).
2. Type `cd ` (with a space after it), drag the GlassHome folder into the Terminal window, press **Enter**.

**Windows**
1. Open the GlassHome folder in File Explorer.
2. Click the address bar, type `powershell` and press **Enter**. A blue window opens in that folder.

## Step 5 · Install

Replace `192.168.1.50` with your Fire TV's IP address.

**Mac**
```bash
sh scripts/install.sh 192.168.1.50
```

**Windows**
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\install.ps1 192.168.1.50
```

The first time, the TV asks **"Allow USB debugging?"**: choose **Always allow from this computer**
and press OK. When the script says *Done*, press **Home** on the remote: GlassHome appears.

What `install` does: it saves the settings it may change (in the `backups` folder and on the TV),
installs the app, turns on the "GlassHome - Home button" service without touching any other
accessibility service, and prints the screensaver setting before and after to show it did not change.

> **Mac only:** if macOS says *"adb" cannot be opened because the developer cannot be verified*,
> open **System Settings → Privacy & Security** and click **Open Anyway**, then run the command again.

---

## Using it with the remote

| Button | What it does |
| --- | --- |
| ◀ ▶ ▲ ▼ | move around; the focused tile lifts with a small bounce and a highlight |
| OK | open the app |
| Hold OK, or ☰ Menu | app menu: **Move** (Sposta), add to / remove from the dock, **Tile style** (Sistema icona…), hide, App info |
| ▼ from the dock | **Recent** apps, then **All apps** |
| ▲ from the dock | top bar: **Search**, **Settings**, **Amazon Home**, **Options** |
| ☰ Menu (not on a tile) | Options |
| Back | close a menu, or go back to the dock |
| Home | always back to GlassHome, on the dock |

- **Move an app in the dock:** hold OK → Sposta (Move) → ◀ ▶ → OK.
- **Hide an app:** in All apps, hold OK → Nascondi dal cassetto (Hide from drawer). Show it again from Opzioni → App nascoste (Options → Hidden apps).
  The app stays installed and works as usual.
- **Search** opens Fire TV's own search (Amazon's "Find" tab): press ▼ and OK to type.
  Alexa still works from the remote's microphone button (Fire OS doesn't let other apps start it).
- **Settings** has shortcuts to Network, Remotes & Bluetooth, Applications, My Fire TV and All settings.
- **Options:** wallpaper, wallpaper of the day, tile corner radius, hidden apps, memory
  ("close background apps"), weather, clear recents, about.

> The on-screen texts are in Italian in this version (Cerca = Search, Impostazioni = Settings, Opzioni = Options, Recenti = Recent, Tutte le app = All apps).

## Optional extras (from the computer)

Use `sh scripts/NAME.sh <IP> …` on a Mac, or
`powershell -ExecutionPolicy Bypass -File .\scripts\NAME.ps1 <IP> …` on Windows.

| What | Mac example |
| --- | --- |
| Weather for your city | `sh scripts/weather.sh 192.168.1.50 "London"` (turn off: `off`) |
| Your own photo as wallpaper (resized to 1080p) | `sh scripts/wallpaper.sh 192.168.1.50 ~/Pictures/photo.jpg` |
| List the installed apps' package names | `sh scripts/tile.sh 192.168.1.50 list` |
| Custom 16:9 tile for an app | `sh scripts/tile.sh 192.168.1.50 com.netflix.ninja netflix.png` |
| Back up your setup | `sh scripts/backup-config.sh 192.168.1.50` |
| Restore the latest backup | `sh scripts/restore-config.sh 192.168.1.50` |
| Check the screensaver still works (~5 min) | `sh scripts/test-screensaver.sh 192.168.1.50` |

## Undo everything

```bash
sh scripts/restore-defaults.sh 192.168.1.50 --uninstall
```
(Windows: `powershell -ExecutionPolicy Bypass -File .\scripts\restore-defaults.ps1 192.168.1.50 -Uninstall`)

This removes **only** GlassHome's service and app and puts back the saved settings. Without
`--uninstall` it keeps the app but gives the Home button back to Amazon.

Without a computer: **Settings → Applications → Manage Installed Applications → GlassHome →
Uninstall**. When you are done with the scripts, you can turn **ADB debugging** off again.

## Updating

Download the new `.apk` from Releases into `dist`, then run `install` again. Your dock and settings
are kept.

## Troubleshooting

- **"Couldn't connect"**: the computer and the Fire TV must be on the same Wi-Fi; check that ADB
  debugging is on and the IP is right (it can change after a router restart).
- **The TV never asked to allow debugging**: wake the TV (press a button) and run the command again.
- **After a Fire OS update the Home button opens Amazon's home again**: run `install` again.
  GlassHome is always available from the apps list too.
- **The screensaver doesn't start**: GlassHome never changes it. Run `test-screensaver` to check
  (it changes the screensaver delay to 1 minute only during the test and then puts it back).

## Privacy

No ads, no tracking, no account. The only internet connection is the optional weather (Open-Meteo),
and only after you set a city. Your backups stay on your computer.

---

## For developers

- Plain Java and Android views, no libraries, no Gradle: `./build.sh` (Mac/Linux) or `.\build.ps1`
  (Windows) builds `dist/GlassHome-<version>.apk` with JDK 17 and Android SDK
  (`platforms;android-34`, `build-tools;34.0.0`). The first build creates **your own** signing key
  in `~/keystores/` and its password in `keystore.properties` (both git-ignored): **back them up**,
  Android only accepts updates signed with the same key.
- Code: `app/src/com/glasshome/launcher/` — `HomeScreen` (UI), `TileView` (focus effects), `Glass`
  (simulated glass), `HomeService` (Home button and screensaver coexistence), `tiles/` (tile
  sources: custom file → app TV banner → generated tile; add a new `TileSource` to extend it).
- Before pushing to a public repository run `./scripts/check-publish.sh`: it checks for keys,
  passwords, home IPs, e-mail addresses and personal paths.

MIT licence. Fonts, icons and photos keep their own licences: see
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
