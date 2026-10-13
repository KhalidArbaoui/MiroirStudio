# MIROIR Tether — progress & handoff

_Last updated: 13 Oct 2026 · latest build: **22** · tested on the tablet up to build 21 (Z8 + A7 III)_

Android app (Samsung tablet, also phones) for USB tethering of the **Nikon Z8** and **Sony A7 III**, built for
MIROIR Studio's food and product shoots.

- **Code:** `tether/` on branch `claude/camera-tethering-mobile-app-g2yi23` of `KhalidArbaoui/MiroirStudio`
- **Install builds:** https://github.com/KhalidArbaoui/MiroirStudio/releases (newest at the top, `MIROIR-Tether-N.apk`)
- Every push to the branch runs the tests and publishes a new APK release automatically.
- **Design preview** (clickable replica, change the look here first, then port to the app):
  https://claude.ai/artifact/TJppPrCYMNb6hUkG9YEMhw — matches build 16; not yet updated for the
  settings pick list (build 19+) or the phone top bar (build 22).

## What the app does (all working)

| Area | Features |
|---|---|
| Connection | USB tethering, 16 KB reads for Samsung, **auto-reconnect** on replug / USB error (live view resumes), crash recorder + persistent log (≡ → Share log) |
| Review | Full-quality image, Focus 100% loupe, histogram, clipping overlay, grey-card WB check, pinch/double-tap zoom, viewer background K/G/L |
| Framing | **Crop overlays** 4:5 / 1:1 / 9:16 / 16:9, **drag the frame onto the subject** (double-tap re-centres), rule-of-thirds grid |
| Live view | Live view + live histogram, **tap-to-focus** (box: white → green locked / amber camera's own point / red failed), AF button, **reference overlay** of a previous dish with opacity slider, "Receiving…" notice during downloads |
| Orientation | Live view turns upright for a **vertical camera, both sides** (from the photo's orientation incl. RAW-only; manual 0°/90°/270° on the rail); rotation lock AUTO / LAND / PORT; portrait layout |
| Camera control | Shutter / aperture / ISO / EV / WB / Kelvin with ‹ › steps, **tap a value for a pick list** (full stops highlighted); exposure meter |
| Selects & delivery | Stars, Pick, filters, Save picks to Gallery, pick list for Lightroom, **Send to client** (WhatsApp / email), **client view** full-screen with Approve |
| Warnings | Camera battery 20 % / 10 %, card < 100 shots, shots left in the status chip |
| Branding | MIROIR Studio logo (top bar + client view), violet accent on neutral greys |
| Devices | Android 9+; tablet and phone layouts (phone: guide/log in ⋯ menu) |

## Camera notes

| | Nikon Z8 | Sony A7 III |
|---|---|---|
| Settings | Direct set, instant | Dial clicks (burst, ~1 s for a big jump); shutter/aperture/EV list from Sony's standard stops |
| Tap-to-focus | ✅ moves the point (needs AF-S + Single-point/Wide-area, not Auto-area) | Can't take a focus point over USB → amber box; set Flexible Spot on the camera, tablet AF focuses there |
| Aperture | ✅ | Needs the lens aperture ring on **A** ("Ring on A?" label) |
| Other bodies, same driver | Z9, Z6/Z7 II–III, Zf, DSLRs with live view (untested) | A7 IV, A7R/A7S, A9, A1, A6xxx with PC Remote (untested; newer bodies may support focus point) |
| Other brands | Fujifilm, Panasonic, OM, Pentax, Leica = basic PTP (photos come across). **Canon EOS not supported yet.** | |

## Camera setup card (keep in the bag)

**Nikon Z8**
- Airplane mode ON · USB power delivery OFF
- RAW + JPEG fine (L) · sRGB · Tone mode SDR · Active D-Lighting OFF
- Custom setting **d8 "Apply settings to live view" ON**
- Focus: **AF-S** + **Single-point** (tap-to-focus) · Auto image rotation ON

**Sony A7 III**
- USB Connection = **PC Remote** · USB Power Supply **OFF**
- PC Remote Function → Still Img. Save Dest. = **PC+Camera**
- **Live View Display → Setting Effect ON**
- DRO / Auto HDR OFF · sRGB · use the USB-C port
- **Sigma 24-70: aperture ring on A** · Focus Area **Flexible Spot (M)**, AF-S

**Tablet & Lightroom**
- Samsung: Display → Screen mode **Natural**, Eye comfort shield off, fixed brightness ~70–80 %
- Open the app first, then plug in the camera; choose MIROIR Tether if Samsung asks "Open with…"
- Lightroom Classic: Preferences → Presets → **Raw Defaults → Camera Settings**
- Shoot **RAW + JPEG**: faster previews, colours closer to Lightroom

## Lessons from testing

- Samsung's USB stack rejects 1 MB reads → 16 KB reads.
- RAW-only needs the full 50 MB NEF for a sharp preview (~2–3 s, live view pauses meanwhile). **RAW + JPEG is fastest.**
- The JPEG inside a RAW has no orientation → the app copies it from the RAW's TIFF header (tag 0x0112).
- Live view always streams landscape; the app turns it and maps taps back to sensor coordinates.
- Nikon tap-to-focus: ChangeAfArea (0x9205) in the live-view header's coordinate space, then AfDrive.
- Sony A7 III ignores absolute ISO/shutter sets → dial clicks; remembered after the first refusal.
- Sony reports aperture read-only when the lens ring isn't on A.
- Cameras pause live view while focusing; the app waits up to 4 s before giving up.
- Every fix so far came from the log: **≡ → Share log**.

## Roadmap — next session

**For the studio (recommended first)**
1. Use it on 2–3 real client shoots; note anything slow or missing.
2. Update the design preview with the pick list and phone layout.
3. Live view 100 % zoom + focus peaking (manual focus on glass / sauces).
4. Before/after compare of two shots; capture flash + shutter sound.
5. Dish shot list (tick off dishes, files named by dish).
6. Lightroom look (LUT) on previews — only if clients comment on colour.

**For other users (later)**
1. **Own release key** before anyone else installs (switching later forces an uninstall) — keystore as GitHub
   secrets, ~10 min with the owner.
2. **Canon EOS driver** (remote mode, GetEvent, RemoteRelease, live view) — needs a Canon body to test.
3. Settings screen (overlay opacity, rotation default, language); Fujifilm / Panasonic checks with real bodies.
4. Play Store: privacy policy, listing, screenshots, signed AAB, crash reporting · Arabic UI.
5. Own repository `miroir-tether` (don't merge this branch into the website's `main`).

**Business:** package "Live tethered shoot — approve every dish on set, matched menu frames, previews on WhatsApp
before we leave" as a premium line item. Keep the app in-house as an edge; test with 3–5 photographer friends
before thinking about the Play Store.

## Developer notes

- `core/` = pure Kotlin (PTP, Nikon/Sony/Generic drivers, crop, rotation, RAW orientation, Sony stops, histogram)
  — `./gradlew -PcoreOnly :core:test` (no Android SDK needed; this cloud machine can't download Gradle deps, CI runs them).
- `app/` = Android/Compose UI: `ui/TetherApp.kt` (layout, top bar, tool rail), `ui/TetherBar.kt` (settings bar +
  pick list), `ui/Viewer.kt` (review + live view, crop drag, AF box, overlay), `ui/Panels.kt`, `ui/ClientView.kt`,
  `ui/Guide.kt`, `ui/Icons.kt`, `ui/Theme.kt`; camera loop in `tether/TetherViewModel.kt`; USB in `usb/UsbPtpTransport.kt`;
  logo in `res/drawable/miroir_logo.xml` (from the Brand Board artifact).
- Design-preview source lives in the chat session scratchpad only; rebuild from the artifact (Artifact read) if needed.
