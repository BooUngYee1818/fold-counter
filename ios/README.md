# iOS app (for foldable iPhones) — test build

[한국어](README.ko.md) | **English**

SwiftUI, iOS 17 and up. Open `FoldCounter.xcodeproj` in Xcode. It speaks the same hub API as the Android app, so both show up in the same caption.

## Build and install
- **On a real iPhone from Xcode**: open the project → select the FoldCounter target → Signing & Capabilities → choose your Apple ID under Team → connect the iPhone → Run. A free Apple ID needs a reinstall every 7 days; a paid developer account can distribute through TestFlight.
- **Unsigned IPA from the command line**:
  ```bash
  cd ios && xcodebuild -project FoldCounter.xcodeproj -target FoldCounter -sdk iphoneos -configuration Release CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO SYMROOT="$(pwd)/build" build && rm -rf build/Payload && mkdir build/Payload && cp -R build/Release-iphoneos/FoldCounter.app build/Payload/ && (cd build && zip -qr ../FoldCounter-unsigned.ipa Payload)
  ```
  Install the IPA with Sideloadly / AltStore, signed with your own Apple ID. GitHub Actions produces the same IPA on every push.
- **Simulator**: download an iOS simulator runtime in Xcode → Settings → Components, then Run on any iPhone simulator. A hub running on the same Mac is found automatically; if not, use `127.0.0.1`.

## What has been verified
- Compiles with Xcode 26.6 / iOS 26.5 SDK; unsigned IPA builds.
- Not yet run on a real device or in the simulator. Not yet verified on a physical foldable iPhone.

## Fold detection (design)
| Role | Android | iOS |
|---|---|---|
| Fold detection | hinge angle sensor → screen state | `WindowFoldMonitor`: 1) switching between the small cover screen and the big inner screen (window size change) 2) the app being put to sleep by folding (scene phase). If Apple ships a fold API, only this file needs to change |
| Count storage | SharedPreferences (commit) | `UserDefaults` + immediate `synchronize` |
| Hub protocol | HttpURLConnection + UDP broadcast | `URLSession` + BSD-socket UDP broadcast. Needs the Local Network permission (`NSLocalNetworkUsageDescription`) that iOS asks for on first launch |
| Button blocking | App pinning (Lock Task) | Guided Access. The app cannot turn it on, only **detect** it: `UIAccessibility.isGuidedAccessEnabled` + `guidedAccessStatusDidChangeNotification`. When off, the screen shows "triple-click the side button → Start"; when on, the hint disappears |
| Keep screen on | keepScreenOn | `UIApplication.shared.isIdleTimerDisabled = true` |
| Device name | model-code table | `utsname.machine` (e.g. `iPhone18,3`) → product-name table. Since iOS 16 `UIDevice.name` returns just "iPhone" without a special entitlement, so unknown models show the identifier and can be renamed in the hub |
| Idle credit | accelerometer | `CoreMotion` accelerometer + touches |
| Language | values / values-ko | String catalog (en, ko), follows the device language |
| Menu | 5 taps | same (5 taps) |

## Hub API (same as Android)
- `POST /api/register` `{deviceId, name, model, manufacturer, platform:"ios", osVersion, detector, appVersion, count}`
- `POST /api/fold` `{deviceId, delta}`
- `POST /api/heartbeat` `{deviceId, detector, posture}` every 5 s
- Discovery: UDP broadcast `FOLDCOUNTER_DISCOVER` to port 47777 → reply `FOLDCOUNTER_HUB <port>`

## Privacy
Same as Android. No account, no analytics, no external server. The device ID is a random UUID created at install.

## Next steps
1. Verify hub connection, menu, test fold and the Guided Access hint on a simulator or a regular iPhone.
2. On a real foldable iPhone, tune the rule in `Detection/WindowFoldMonitor.swift` (80 % width threshold, app sleep). Replace that file once Apple publishes a fold API.
