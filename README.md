# Work Photo Log

Android camera for documenting work by project. Capture offline, add a job note, and share a clean original, a separate stamped copy, or a project ZIP. Location is optional. The app labels device time and compares original bytes with the saved SHA-256 digest.

## Use the app

1. Open Camera, choose a project, then take a photo.
2. Review it, add a note, and save. The app keeps the unbranded JPEG in its private library.
3. Open the photo in Projects to share its original or an optional job stamp. Export a project ZIP to include its original JPEGs and manifest. The ZIP dialog lets you choose whether to include GPS.

The records live on this device. Share ZIPs you need to keep outside the app before clearing app data or uninstalling.

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
