// SPDX-License-Identifier: Apache-2.0

use std::{
    fs,
    io::{Cursor, Read},
    path::Path,
};

use proof_core::{
    CaptureEntry, CaptureLocation, CaptureMetadata, ProofError, check_original, inspect_capture,
    new_photo_id, write_project_zip,
};
use serde_json::Value;
use zip::ZipArchive;

const JPEG: &[u8] = b"\xff\xd8capture bytes\xff\xd9";

fn capture(path: &Path) -> CaptureEntry {
    fs::write(path, JPEG).unwrap();
    let metadata = CaptureMetadata {
        id: new_photo_id(),
        captured_at_millis: 1_800_000_000_000,
        utc_offset_minutes: 480,
        note: "Replaced pipe".into(),
        location: Some(CaptureLocation {
            latitude: 15.5,
            longitude: 120.25,
            accuracy_meters: 45.0,
            age_millis: 900,
            precision: "approximate".into(),
        }),
    };
    inspect_capture(metadata, path.to_str().unwrap().into()).unwrap()
}

fn export(entry: CaptureEntry, include_location: bool, file: &Path) -> Result<(), ProofError> {
    write_project_zip(
        "project-id".into(),
        "Workshop".into(),
        vec![entry],
        include_location,
        file.to_str().unwrap().into(),
    )
}

fn open_zip(path: &Path) -> ZipArchive<Cursor<Vec<u8>>> {
    ZipArchive::new(Cursor::new(fs::read(path).unwrap())).unwrap()
}

#[test]
fn archive_preserves_capture_and_can_exclude_location() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("original.jpg");
    let entry = capture(&path);

    for include_location in [false, true] {
        let output = dir.path().join(format!("export-{include_location}.zip"));
        export(entry.clone(), include_location, &output).unwrap();
        let mut archive = open_zip(&output);
        assert_eq!(archive.len(), 2);

        let manifest: Value =
            serde_json::from_reader(archive.by_name("manifest.json").unwrap()).unwrap();
        let photo = &manifest["photos"][0];
        assert_eq!(manifest["schema_version"], 1);
        assert_eq!(manifest["project_name"], "Workshop");
        assert_eq!(manifest["time_source"], "device");
        assert_eq!(photo["id"], entry.metadata.id);
        assert_eq!(photo["sha256"], entry.sha256);
        assert_eq!(photo["utc_offset_minutes"], 480);
        assert!(photo.get("jpeg_path").is_none());
        assert_eq!(photo.get("location").is_some(), include_location);
        if include_location {
            assert_eq!(photo["location"]["age_millis"], 900);
            assert_eq!(photo["location"]["precision"], "approximate");
        }

        let name = format!("photos/{}.jpg", entry.metadata.id);
        assert_eq!(photo["path"], name);
        let mut original = Vec::new();
        archive
            .by_name(&name)
            .unwrap()
            .read_to_end(&mut original)
            .unwrap();
        assert_eq!(original, JPEG);
    }
    assert_eq!(fs::read(path).unwrap(), JPEG);
}

#[test]
fn changed_photo_fails_integrity_and_leaves_no_partial_archive() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("original.jpg");
    let entry = capture(&path);
    assert!(check_original(entry.clone()).unwrap());

    fs::write(&path, b"changed the original").unwrap();
    assert!(!check_original(entry.clone()).unwrap());
    let archive = dir.path().join("archive.zip");
    assert!(matches!(
        export(entry, true, &archive),
        Err(ProofError::OriginalChanged { .. })
    ));
    assert!(!archive.exists());
    assert_eq!(fs::read_dir(dir.path()).unwrap().count(), 1);
}

#[test]
fn existing_output_is_preserved_and_unsafe_ids_are_rejected() {
    let dir = tempfile::tempdir().unwrap();
    let mut entry = capture(&dir.path().join("original.jpg"));
    let archive = dir.path().join("archive.zip");
    fs::write(&archive, b"existing export").unwrap();

    assert!(matches!(
        export(entry.clone(), false, &archive),
        Err(ProofError::Storage { .. })
    ));
    assert_eq!(fs::read(&archive).unwrap(), b"existing export");

    entry.metadata.id = "../outside".into();
    assert!(matches!(
        export(entry, false, &archive),
        Err(ProofError::InvalidMetadata { .. })
    ));
    assert_eq!(fs::read(&archive).unwrap(), b"existing export");
}
