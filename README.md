# Sitefinch

Android camera for documenting work by project. Capture photos and clips offline, add job notes, and share clean or annotated copies. Location is optional. Sitefinch labels device time and compares original bytes with the saved SHA-256 digest.

## Use the app

1. Open Camera, choose a project, and select Photo or Video.
2. Open Templates and select a preset or Clean photo. Explore more templates by job category. In Customize, switch individual stamp fields on or off and add a business card or styled note. In Style, pick the layout, background opacity, typeface, and text and clock sizes. Drag the stamp, logo, clock, or details on the preview, or use the move buttons. In Logo, import an image and choose its shape and crop. The Style controls let you apply colors from that image to the background, accents, and text. Save style adds it to your reusable library.
3. Turn on Location to fill the stamp address through Android’s address lookup. If device Location is off, Sitefinch opens Android Settings so you can enable it. In **Customize → Details → Address**, choose which parts appear. Municipality/city and province/county are selected by default. Enter an optional manual address for captures with Location off or when lookup is unavailable. The note button adds a note to the next capture.
4. Tap the photo shutter. Sitefinch keeps the original JPEG in the project and automatically saves a stamped copy to Android Gallery under `Pictures/Sitefinch`. The Clean photo template saves the original JPEG to Gallery. Use **Share** on the capture preview to share the same photo, or dismiss the preview to keep shooting. Turn off Gallery saving in Photo settings. Photo details can still share the original or make a copy with stamps or markup.
5. Video renders the chosen stamp while recording. Enable the microphone when you want audio. Project ZIPs include original JPEGs and a manifest, with optional GPS metadata. Share clips individually.

**Templates → Explore** includes offline starting points for shipping, attendance, security, inspection, retail, and other field work. Pick a template to use as-is or customize it before applying.

Every stamp control is available without a subscription. Media carries no Sitefinch watermark. Records and saved styles live on this device. Open **Sitefinch settings → Library backup** to create a ZIP in a folder you choose. Restore merges its projects, originals, and saved styles into the current library. Keep a copy outside the device if you need to recover after losing it. The ZIP includes recorded location and is unencrypted.

Map, coordinates, and altitude use the recorded location when available. Street maps are available online from [OpenStreetMap](https://www.openstreetmap.org/copyright), with bundled street detail around Dagupan and Mangaldan and a regional map for offline use elsewhere. OpenStreetMap receives tile requests for the displayed area. To keep map rendering offline, turn off **Online street maps** in the template's **Capture details**. Compass uses the device’s magnetic heading. Weather is entered manually.

## Build and check

Use JDK 17 or 21, Python 3, Android SDK Platform 36, Build Tools 35.0.0, NDK 28.2.13676358, and Rust 1.99.1. Install the rustfmt and Clippy components for the Rust checks. Install the `aarch64-linux-android` and `x86_64-linux-android` Rust targets. Set `ANDROID_HOME` or `sdk.dir` in an untracked `local.properties`.

On Windows:

```powershell
cargo fmt --all -- --check
cargo clippy --locked --workspace --all-targets --all-features -- -D warnings
cargo test --locked --workspace
.\gradlew.bat :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
```

Use `./gradlew` on Linux or macOS. Connect a test phone or boot an Android x86_64 emulator before running `:app:connectedDebugAndroidTest`. On a physical phone, also check real capture and orientation, approximate and denied GPS, share grants, and interruption during save.

## References
- [Apache License 2.0](LICENSE)
