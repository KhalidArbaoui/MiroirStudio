# MIROIR Tether — progress & handoff

_Last updated: 13 Oct 2026 · latest tested build: **11** · build B (crop overlays + client view) awaiting test_

Android app (Samsung tablet) for USB tethering of the **Nikon Z8** and **Sony A7 III**, built for MIROIR Studio's
food and product shoots.

- **Code:** `tether/` on branch `claude/camera-tethering-mobile-app-g2yi23` of `KhalidArbaoui/MiroirStudio`
- **Install builds:** https://github.com/KhalidArbaoui/MiroirStudio/releases (newest at the top, `MIROIR-Tether-N.apk`)
- Every push to the branch runs the tests and publishes a new APK release automatically.

## Status

| Feature | Z8 | A7 III |
|---|---|---|
| USB tethering, full-quality image, Focus 100% | ✅ | ✅ |
| Histogram, clipping overlay, grey-card WB check | ✅ | ✅ |
| Ratings, picks, Save picks to Gallery, pick list for Lightroom | ✅ | ✅ |
| Camera control: shutter / aperture / ISO / EV / WB | ✅ | ✅ (aperture needs lens ring on A) |
| Live view + AF + grid + live histogram | ✅ | ✅ |
| Pro UI: neutral greys, tether bar, tool rail, exposure meter | ✅ | ✅ |
| Crop overlays 4:5 / 1:1 / 9:16 / 16:9 (tool rail, works in live view) | 🧪 | 🧪 |
| Client view: full-screen photo + Approve button (screen icon, top bar) | 🧪 | 🧪 |

## Camera setup card (keep in the bag)

**Nikon Z8**
- Airplane mode ON · USB power delivery OFF
- RAW + JPEG fine (L) · sRGB · Tone mode SDR · Active D-Lighting OFF
- Custom setting **d8 "Apply settings to live view" ON** (otherwise live view/meter don't show the real exposure)

**Sony A7 III**
- USB Connection = **PC Remote** · USB Power Supply **OFF**
- PC Remote Function → Still Img. Save Dest. = **PC+Camera**
- **Live View Display → Setting Effect ON** (otherwise live view looks bright while the photo is dark)
- DRO / Auto HDR OFF · sRGB · use the USB-C port
- **Sigma 24-70: aperture ring on A**, or the tablet can't change the aperture

**Tablet & Lightroom**
- Samsung: Display → Screen mode **Natural**, Eye comfort shield off, fixed brightness ~70–80 %
- Open the app first, then plug in the camera; choose MIROIR Tether if Samsung asks "Open with…"
- Lightroom Classic: Preferences → Presets → **Raw Defaults → Camera Settings**

## Lessons from testing

- Samsung's USB stack rejects 1 MB reads → the app uses 16 KB reads (fixed).
- RAW-only shooting needs the full RAW transfer for a sharp preview; **RAW + JPEG is fastest**.
- Sony needs dial "clicks" for shutter/aperture/EV and ISO; the app learns the direction automatically.
- Sony reports aperture read-only when the lens ring isn't on A (log showed `0x5007 set=false`).
- Cameras pause live view while focusing; the app now waits up to 4 s before giving up.

## Next steps (pick one tomorrow)

1. **First real shoot** with the app (recommended) — note anything slow or missing.
2. **Test build B:** crop button on the tool rail cycles 4:5 → 1:1 → 9:16 → 16:9 → off; screen icon in the
   top bar opens the client view (Back or ✕ exits). Client "Approve" = Pick, so it lands in the pick list.
3. **Phase 2d — Lightroom match:** only if a side-by-side comparison shows visible colour differences.
4. **Own repository:** create `miroir-tether` in GitHub Desktop (File → New Repository → Publish, private),
   then move `tether/` there. Don't merge this branch into the website's `main`.
5. Business: package "Live tethered shoot — approve every dish on set" as a premium offer.

## Smaller backlog

- Filmstrip: bigger thumbnails option, reject flag
- Capture feedback: screen flash + shutter sound
- Before/after compare of two shots
- Auto-reconnect after a USB drop
- Release signing key before any Play Store upload (`app/debug.keystore` is a shared dev key)

## Developer notes

- `core/` = pure Kotlin (PTP protocol, Nikon/Sony drivers, histogram, RAW preview, settings) — run
  `./gradlew -PcoreOnly :core:test` (25 tests, no Android SDK needed).
- `app/` = Android/Compose UI: `ui/TetherApp.kt` (layout), `ui/TetherBar.kt`, `ui/Viewer.kt`, `ui/Panels.kt`,
  `ui/Icons.kt`, `ui/Theme.kt`; camera loop in `tether/TetherViewModel.kt`; USB in `usb/UsbPtpTransport.kt`.
- When something fails on a camera: in the app tap **Log (≡) → Share log** and send it — every fix so far came
  from the log.
