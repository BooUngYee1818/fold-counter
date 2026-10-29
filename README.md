# Fold Counter (폴더블 카운터)

[한국어](README.ko.md) | **English**

Counts **one every time a foldable phone is folded and unfolded**, and shows the number as a live caption in your stream (OBS / XSplit).
Built for every foldable, old and new: flip style, book style, dual screen and tri-fold.

```
[phone app A] ─┐
[phone app B] ─┼─ same Wi-Fi ─> [PC hub]  ─> http://localhost:8787/overlay  (OBS / XSplit browser source)
[phone app C] ─┘                    ├─> /admin   (rename devices, adjust counts, caption style)
                                    └─> data/text/<device>.txt  (for text sources)
```

> **Status**: Android app + PC hub are released. An **iPhone test build** (unsigned IPA) is included; it has not been verified on a real foldable iPhone yet.
> An APK is Android-only and cannot be installed on an iPhone. See the "iPhone" section below.

| Folder | What it is |
|---|---|
| `hub/` | PC hub server (Node.js, no dependencies). Overlay, admin page, OBS auto-connect, fake-phone simulator |
| `android/` | Android app (Kotlin), Android 9 and up |
| `ios/` | iPhone app (SwiftUI, iOS 17 and up), test build |
| `.github/workflows/` | GitHub Actions: builds the APK and the iPhone IPA on every push |

## Privacy
- **No account, no login, no ads, no analytics, no external server.**
- The phone app sends only the **device name, model and count**, and only to your own hub on the same Wi-Fi.
- The device identifier is a random ID created when the app is installed. IMEI, phone number and advertising ID are never read.
- App permissions: Internet (LAN talk with the hub), foreground service (keep detecting while folded), notifications (running indicator). Nothing else.

## Quick start

### 1. Run the PC hub
1. Install [Node.js](https://nodejs.org) LTS (Windows / macOS).
2. Double-click `hub/start-hub.bat` (Windows) or `hub/start-hub.command` (macOS). From a terminal: `cd hub && node server.js`.
3. If the Windows firewall asks, **allow on private networks**.
4. Open `http://localhost:8787/admin` in a browser.

### 2. Add the caption to OBS / XSplit
- **OBS**: Add source → Browser → URL `http://localhost:8787/overlay` → width 1920, height 1080.
- **XSplit**: Add source → Webpage → same URL.
- In the admin page, "Caption style" changes size, colors, alignment and the credit position; the URL updates with it. There is also a per-device URL so each device can be placed separately.
- Prefer a text source? Point "read from file" at `hub/data/text/<device name>.txt`.

### 3. Install the phone app (Android)
1. Download the APK from this repository's **Releases** page (or the latest build's Artifacts in the Actions tab). Copy it to the phone and install (allow apps from unknown sources). Developers can `adb install`.
2. First launch: confirm the device name → check that the hub was found automatically → **Start counting**.
3. A black screen with a number appears. Fold and unfold: the number goes up on the phone and in the stream caption.

### 4. Right before going live: block the buttons
Neither Android nor iOS lets an app block the Home or power buttons by itself, so the OS "pin app" feature is used.
- Tap the screen **5 times quickly** → menu → **Pin this app**. Confirm the system prompt.
- Some phones need "Pin windows" (Screen pinning) enabled once under Settings › Security; the app tells you and opens the settings.
- To unpin: hold Back and Recents together (gesture navigation: swipe up and hold).
- The power button cannot be blocked, but detection and counting continue even while the screen is off.

## Phone app features
- **Menu**: 5 quick taps → Pin app / Diagnostics / Settings / Test (fold → unfold once) / Reset count to 0
- **Diagnostics**: which detection method is active, live hinge angle, hub connection, recent events
- **Settings**: device name, hub auto/manual, detection method, closed/open angle thresholds, tri-fold rule, credit idle time, credit text
- **Credit**: after a few idle minutes (default 3) with no touch or movement, "Fold Counter / made by YouTube @CEO_BooungYee-TeCh" appears at the top of the screen; it disappears when the phone moves
- **Language**: Korean if the phone language is Korean, otherwise English (automatic)
- **Count never lost**: stored on the phone and on the hub. Survives app kills and hub restarts. Folds that happen while disconnected are queued and sent when the hub is back

## Fold detection
The app picks whatever works on the device. Every method is normalized to open/closed, and the count goes up at the **closed → open** moment.

| Priority | Method | Devices | Notes |
|---|---|---|---|
| 1 | Hinge angle sensor | Almost every foldable on Android 11+ (Z Fold2/Flip and later, Pixel Fold, Surface Duo, OnePlus Open, …) | Closed below 30°, open above 150° (adjustable). Half-folded "flex mode" is not counted |
| 2 | Screen state | Old devices without the sensor (1st-gen Galaxy Fold, razr 2019, …) | Screen off or switching to the cover screen counts as closed. The power button also counts as a fold here, so use it with app pinning |

- Devices that open to 360° (Surface Duo) count as closed only near 0°.
- Tri-fold: choose in Settings whether "any hinge closed" or "all hinges closed" means closed.
- Huawei HarmonyOS NEXT (China-only Mate XT etc.) cannot run Android apps at all and is out of scope. EMUI devices work with the APK.

## Testing without a foldable
- **Fake-phone simulator**: `cd hub && node tools/simulate.js 3` registers 3 fake phones that send folds every few seconds. Use it to check the overlay, the admin page and the OBS connection.
- **In-app test mode**: on any Android phone, the menu item "Test: simulate fold → unfold" exercises the whole path (app → hub → caption).
- **Emulator**: in Android Studio's Device Manager, create a foldable (Pixel Fold, 7.6" Fold-in, …); it has a hinge angle sensor. Extended controls → Virtual sensors → Hinge angle folds and unfolds it. From the command line, `adb emu sensor set hinge-angle0 0` (fold) then `adb emu sensor set hinge-angle0 180` (unfold) adds one. Inside the emulator, UDP discovery cannot reach the PC, so enter `10.0.2.2` as the hub address in the app settings.
- The Android app in this repository was verified on an Android 16 Pixel Fold emulator: fold counting, hub sync, app pinning, credit overlay, Korean/English switching. The iPhone app was verified up to compiling and producing an unsigned IPA. Neither has been tested on a physical foldable yet.

## OBS auto-connect (optional)
When a phone connects to the hub, a browser source is created and enabled in OBS automatically.
1. OBS 28 or later: Tools → WebSocket Server Settings → Enable.
2. `cd hub && node obs-bridge.js --password <password>` (options: `--scene "<scene>"`, `--hide-when-idle` to hide the caption when no phone is connected).
XSplit has no public automation API, so add the webpage source manually.

## Adding a logo
Drop a green-screen (chroma key) logo image in; the green background is removed automatically.
- Stream caption: `hub/public/logo.png`
- Phone screen (credit panel): `android/app/src/main/assets/logo.png`, then rebuild

## Building it yourself
- **Hub**: no build step. Node.js 18 or later (22 or later for the OBS bridge).
- **Android**: open `android/` in Android Studio → Run. Or `cd android && ./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
- **GitHub Actions**: every push builds the APK and the iPhone IPA. Pushing a tag that starts with `v` (for example `v0.1.0`) attaches both to a Release automatically.
- **iPhone**: open `ios/FoldCounter.xcodeproj` in Xcode → choose your Apple ID team → connect the iPhone → Run. Details in `ios/README.md`.

## iPhone (test build)
The iPhone app lives in `ios/` and has the same screens and hub protocol as the Android app, so it appears in the same caption.

- **Install**: an iPhone cannot install a file the way Android installs an APK. Two options:
  1. Open `ios/FoldCounter.xcodeproj` in Xcode → Signing & Capabilities → pick your Apple ID team → connect the iPhone → Run. A free Apple ID needs a reinstall every 7 days.
  2. Take `FoldCounter-ios-*-unsigned.ipa` from Releases and sideload it with a tool such as Sideloadly or AltStore, signed with your own Apple ID.
- **Fold detection**: iOS has no public hinge sensor API, so the app treats the switch between the cover screen and the inner screen, and the app being put to sleep by folding, as "closed". It has not been verified on a real foldable iPhone yet. On a regular iPhone, the menu's test item exercises the path to the hub.
- **Blocking buttons**: Guided Access. The app detects whether it is off and shows "triple-click the side button → Start"; the hint disappears once it is on. Enable Settings › Accessibility › Guided Access once.
- The first launch asks for the **Local Network** permission. It is needed to find the hub; please allow it.

## Troubleshooting
- **Hub not found**: make sure the PC and the phone are on the same Wi-Fi and that Node is allowed through the PC firewall. If the router has "AP isolation / client isolation" on, type the LAN address shown in the admin page into the app settings.
- **Cannot pin the app**: enable Settings › Security › Pin windows (Screen pinning).
- **Count does not go up**: check in Diagnostics that the angle changes. If the device does not open fully, lower the "open" threshold from 150 to about 130. Without a sensor the app falls back to screen state automatically.
- **Want a different caption name**: edit the name field in the admin page (it updates on the phone too).

## License
MIT License. See `LICENSE`.

---
Made by YouTube @CEO_BooungYee-TeCh
