# Sitefinch

Android camera for documenting work by project. Capture photos and clips offline, add job notes, and share clean or annotated copies. Location is optional. Sitefinch labels device time and compares original bytes with the saved SHA-256 digest.

## Use the app

1. Open Camera, choose a project, and select Photo or Video.
2. Open Templates and select a preset or Clean photo. Customize the layout, size, text and accent colors, position, and detail fields. Use Logo to import a company logo and adjust its placement and crop. Save style adds it to your reusable library. Apply sets the stamp for capture or review.
3. The camera location button opens the address editor and optional GPS controls. Enter an address manually or use Android’s address lookup when available. The note button adds a note to the next capture.
4. Review and save. Photos retain their original JPEG and remember the chosen template. Photo details can share the original or a separate copy with a stamp, text, arrows, boxes, or pen marks. Copy edits stay in that preview unless you share them. An address written on the stamp is visible in the copy independently of the GPS coordinates switch.
5. Video renders the chosen stamp while recording. Enable the microphone when you want audio. Project ZIPs include original JPEGs and a manifest, with optional GPS metadata. Share clips individually.

Every stamp control is available without a subscription. Media carries no Sitefinch watermark. Records and saved styles live on this device. Share ZIPs you need to keep outside the app before clearing app data or uninstalling.

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
