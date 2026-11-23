# Sitefinch

Android camera for documenting work by project. Capture photos and clips offline, add job notes, and share clean or annotated copies. Location is optional. Sitefinch labels device time and compares original bytes with the saved SHA-256 digest.

## Use the app

1. Open Camera, choose a project, and select Photo or Video.
2. Open Templates and select a preset or Clean photo. Explore more templates by job category. In Customize, switch individual stamp fields on or off and add a business card or styled note. In Style, pick the layout, background opacity, typeface, and text and clock sizes. Drag the stamp, logo, clock, or details on the preview, or use the move buttons. In Logo, import an image and choose its shape and crop. The Style controls let you apply colors from that image to the background, accents, and text. Save style adds it to your reusable library.
3. Turn on Location to fill the stamp address through Android’s address lookup. In **Customize → Details → Address**, choose which parts appear. Municipality/city and province/county are selected by default. Enter an optional manual address for captures with Location off or when lookup is unavailable. The note button adds a note to the next capture.
4. Tap the photo shutter. Sitefinch keeps the original JPEG in the project and automatically saves a stamped copy to Android Gallery under `Pictures/Sitefinch`. The Clean photo template saves the original JPEG to Gallery. Use **Share** on the capture preview to share the same photo, or dismiss the preview to keep shooting. Turn off Gallery saving in Photo settings. Photo details can still share the original or make a copy with stamps or markup.
5. Video renders the chosen stamp while recording. Enable the microphone when you want audio. Project ZIPs include original JPEGs and a manifest, with optional GPS metadata. Share clips individually.

Every stamp control is available without a subscription. Media carries no Sitefinch watermark. Records and saved styles live on this device. Share ZIPs you need to keep outside the app before clearing app data or uninstalling.

Map, coordinates, and altitude use the recorded location when available. The map is an offline regional locator. Compass uses the device’s magnetic heading. Weather is entered manually.

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

## Release

GitHub Actions checks the Rust core and Android app on pull requests. Tagged releases use a dedicated keystore in the protected `release` environment. Keep a backup of that keystore to sign future updates with the same identity.

Use `gh auth login --hostname github.com --web` to connect your GitHub CLI and `gh api user --jq .login` to verify its active account. Browser authorization and signing credentials belong to the release owner. The workflow publishes the ARM64 APK and SHA-256 checksum. The release environment accepts version tags and stores the signing secrets named in the release workflow.

## References

- [Timemark package investigation](docs/timemark-reference.md)
- [Apache License 2.0](LICENSE)
