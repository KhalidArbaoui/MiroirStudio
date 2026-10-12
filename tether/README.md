# MIROIR Tether

USB tethering for Android tablets, built for MIROIR Studio's food and product work.
Shoot on a **Nikon Z8** or **Sony A7 III** and every frame appears on the Samsung tablet in seconds,
with exposure and colour tools so the tablet shows what Lightroom will show.

## Phase 1 — features

| Feature | Details |
|---|---|
| USB-C tethering | Nikon Z (PTP + Nikon extensions), Sony Alpha (PC Remote). Other PTP cameras: basic support. |
| Shoot from camera or tablet | Big shutter button; shots fired on the camera body also transfer. |
| RAW + JPEG | Shows the full-size JPEG. RAW-only: shows the camera's embedded preview (marked **RAW preview**). |
| Card keeps a copy | Nikon records to the card; Sony uses *PC+Camera*. "Copy RAW to tablet" is optional. |
| Histogram | RGB + luminance, Lightroom-style clipping triangles, % clipped highlights/shadows. |
| Clipping overlay | Red = blown highlights, blue = crushed shadows. |
| Focus 100% | Tap anywhere: a true 1:1 crop from the full-resolution file. |
| Grey card | Tap a grey card/white plate: shows warm/cool and green/magenta cast. |
| Neutral viewer | Black / grey / light neutral backgrounds for judging colour. |
| EXIF bar | Shutter, aperture, ISO, EV, focal length, WB, lens; warns if the JPEG is Adobe RGB. |
| Log | Built-in connection log with a Share button — send it when something fails. |

Photos are saved on the tablet in `Android/data/studio.miroir.tether/files/Pictures/MIROIR Tether/<session>/`.

## Install on the tablet

1. On the tablet, open this repo's **Releases** page and download the newest `MIROIR-Tether-N.apk`.
2. Open it and allow *Install unknown apps* for the browser/My Files when Android asks.
3. New builds install over the old one (same signing key), settings are kept.

## Camera setup

**Nikon Z8:** USB-C straight to the tablet (no hub) · Airplane mode ON · USB power delivery OFF ·
RAW + JPEG fine (L) · sRGB · Tone mode SDR · Active D-Lighting OFF.

**Sony A7 III:** USB Connection → **PC Remote** · PC Remote Function → Still Img. Save Dest. → **PC+Camera** ·
RAW+J PC Save Img → RAW & JPEG · Airplane mode ON · sRGB · DRO/Auto HDR OFF · use the USB-C port.

## Matching Lightroom

The tablet shows the camera's JPEG; Lightroom renders the RAW with its own profile. To make them match:

1. Lightroom Classic → Preferences → Presets → **Raw Defaults → Camera Settings** (uses the Camera Matching
   profile that corresponds to your Picture Control / Creative Style). Or set Profile → *Camera Matching → Camera Standard*
   in your import preset, with the camera on *Standard*.
2. Turn **off** in-camera dynamic-range tricks (Active D-Lighting / DRO) — Lightroom ignores them.
3. White balance *As Shot*; fix WB in camera using the grey-card tool.
4. Samsung: Display → Screen mode → **Natural**, Eye comfort shield off, fixed brightness ~70–80%.
5. Do one side-by-side test at the start of each job. Expect very close, not pixel-identical.

## Development

```
core/   Pure Kotlin: PTP protocol, Nikon & Sony drivers, histogram, RAW preview extraction (unit-tested)
app/    Android app (Jetpack Compose): USB transport, viewer, tools
```

- `./gradlew -PcoreOnly :core:test` — protocol tests, no Android SDK needed.
- `./gradlew :app:assembleDebug` — APK (needs the Android SDK). CI builds it on every push and publishes a release.

`app/debug.keystore` is a shared development key so CI builds update each other. Create a private release key
before publishing to Google Play.

## Roadmap

- **Phase 2:** live view on the tablet; change ISO / shutter / aperture / WB from the tablet; star ratings;
  export picks to the Samsung Gallery; Lightroom-matching correction profile calibrated from a ColorChecker.
