# Work Photo Log

Android field-photo app. Package `app.workphotolog.android`. Rust portability matters at the proof interface.

- Kotlin owns Android lifecycle, CameraX, Compose, Room, location permissions, and URI grants. Rust owns metadata validation, photo IDs, digest checks, and the ZIP manifest.
- Keep the original JPEG byte-for-byte. Render stamps into separate share files. Keep capture operational offline and allow location permission to be denied.
- Display device time and describe integrity checks as a digest comparison. Use original app assets and synthetic test data.
- Run `cargo fmt --all -- --check`, `cargo clippy --locked --workspace --all-targets -- -D warnings`, and `cargo test --locked --workspace`. Run Android checks and emulator smoke per README.
- Keep APK samples, build products, generated credentials, and captured photos out of Git.
