// SPDX-License-Identifier: Apache-2.0

use std::{
    collections::HashSet,
    fs::File,
    io::{self, Read, Write},
    path::Path,
};

use serde::Serialize;
use sha2::{Digest, Sha256};
use tempfile::NamedTempFile;
use uuid::Uuid;
use zip::{CompressionMethod, ZipWriter, write::SimpleFileOptions};

uniffi::setup_scaffolding!();

#[derive(Clone, Debug, PartialEq, Serialize, uniffi::Record)]
pub struct CaptureLocation {
    pub latitude: f64,
    pub longitude: f64,
    pub accuracy_meters: f64,
    pub age_millis: i64,
    pub precision: String,
}

#[derive(Clone, Debug, PartialEq, Serialize, uniffi::Record)]
pub struct CaptureMetadata {
    pub id: String,
    pub captured_at_millis: i64,
    pub utc_offset_minutes: i32,
    pub note: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub location: Option<CaptureLocation>,
}

#[derive(Clone, Debug, PartialEq, uniffi::Record)]
pub struct CaptureEntry {
    pub metadata: CaptureMetadata,
    pub sha256: String,
    pub jpeg_path: String,
}

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum ProofError {
    #[error("{reason}")]
    InvalidMetadata { reason: String },
    #[error("Could not read or write files: {reason}")]
    Storage { reason: String },
    #[error("Could not build the evidence ZIP: {reason}")]
    Archive { reason: String },
    #[error("Photo {id} has changed since it was saved")]
    OriginalChanged { id: String },
}

impl From<io::Error> for ProofError {
    fn from(error: io::Error) -> Self {
        Self::Storage {
            reason: error.to_string(),
        }
    }
}

impl From<zip::result::ZipError> for ProofError {
    fn from(error: zip::result::ZipError) -> Self {
        Self::Archive {
            reason: error.to_string(),
        }
    }
}

impl From<serde_json::Error> for ProofError {
    fn from(error: serde_json::Error) -> Self {
        Self::Archive {
            reason: error.to_string(),
        }
    }
}

#[uniffi::export]
pub fn new_photo_id() -> String {
    Uuid::new_v4().to_string()
}

#[uniffi::export]
pub fn inspect_capture(
    metadata: CaptureMetadata,
    jpeg_path: String,
) -> Result<CaptureEntry, ProofError> {
    validate_metadata(&metadata)?;
    let sha256 = copy_hashed(File::open(&jpeg_path)?, io::sink())?;
    Ok(CaptureEntry {
        metadata,
        sha256,
        jpeg_path,
    })
}

#[uniffi::export]
pub fn check_original(entry: CaptureEntry) -> Result<bool, ProofError> {
    validate_hash(&entry.sha256)?;
    Ok(copy_hashed(File::open(entry.jpeg_path)?, io::sink())? == entry.sha256)
}

#[derive(Serialize)]
struct Manifest<'a> {
    schema_version: u32,
    project_id: &'a str,
    project_name: &'a str,
    time_source: &'static str,
    photos: Vec<ManifestPhoto<'a>>,
}

#[derive(Serialize)]
struct ManifestPhoto<'a> {
    #[serde(flatten)]
    capture: CaptureMetadata,
    sha256: &'a str,
    path: String,
}

#[uniffi::export]
pub fn write_project_zip(
    project_id: String,
    project_name: String,
    entries: Vec<CaptureEntry>,
    include_location: bool,
    destination: String,
) -> Result<(), ProofError> {
    if project_id.trim().is_empty() || project_name.trim().is_empty() {
        return Err(invalid("A project ID and name are required"));
    }
    if entries.is_empty() {
        return Err(invalid("The project has no photos to export"));
    }

    let mut manifest = Manifest {
        schema_version: 1,
        project_id: &project_id,
        project_name: &project_name,
        time_source: "device",
        photos: Vec::with_capacity(entries.len()),
    };
    let mut ids = HashSet::with_capacity(entries.len());
    for entry in &entries {
        validate_metadata(&entry.metadata)?;
        validate_hash(&entry.sha256)?;
        if !ids.insert(&entry.metadata.id) {
            return Err(invalid("A photo appears more than once in the export"));
        }

        let mut capture = entry.metadata.clone();
        if !include_location {
            capture.location = None;
        }
        let path = format!("photos/{}.jpg", capture.id);
        manifest.photos.push(ManifestPhoto {
            capture,
            sha256: &entry.sha256,
            path,
        });
    }

    let destination = Path::new(&destination);
    let directory = destination
        .parent()
        .filter(|parent| !parent.as_os_str().is_empty())
        .unwrap_or_else(|| Path::new("."));
    let mut temporary = NamedTempFile::new_in(directory)?;
    let mut archive = ZipWriter::new(temporary.as_file_mut());
    let options = SimpleFileOptions::default().compression_method(CompressionMethod::Stored);
    for (entry, photo) in entries.iter().zip(&manifest.photos) {
        archive.start_file(&photo.path, options)?;
        let original = File::open(&entry.jpeg_path)?;
        if copy_hashed(original, &mut archive)? != entry.sha256 {
            return Err(ProofError::OriginalChanged {
                id: photo.capture.id.clone(),
            });
        }
    }
    archive.start_file("manifest.json", options)?;
    {
        // Batch the serializer's small writes before they reach the ZIP entry and file.
        let mut manifest_writer = io::BufWriter::new(&mut archive);
        serde_json::to_writer_pretty(&mut manifest_writer, &manifest)?;
        manifest_writer.write_all(b"\n")?;
        manifest_writer.flush()?;
    }
    archive.finish()?.sync_all()?;
    temporary
        .persist_noclobber(destination)
        .map_err(|error| error.error)?;
    Ok(())
}

fn validate_metadata(metadata: &CaptureMetadata) -> Result<(), ProofError> {
    if !Uuid::parse_str(&metadata.id).is_ok_and(|id| id.to_string() == metadata.id) {
        return Err(invalid("Photo ID must be a canonical UUID"));
    }
    if !(-1080..=1080).contains(&metadata.utc_offset_minutes) {
        return Err(invalid("UTC offset is outside the supported range"));
    }
    if metadata.note.len() > 2000 {
        return Err(invalid("Photo note is longer than 2,000 UTF-8 bytes"));
    }
    if let Some(location) = &metadata.location
        && (!(-90.0..=90.0).contains(&location.latitude)
            || !(-180.0..=180.0).contains(&location.longitude)
            || !location.accuracy_meters.is_finite()
            || location.accuracy_meters < 0.0
            || location.age_millis < 0
            || !matches!(location.precision.as_str(), "precise" | "approximate"))
    {
        return Err(invalid(
            "Location coordinates, accuracy, age, or precision are invalid",
        ));
    }
    Ok(())
}

fn validate_hash(hash: &str) -> Result<(), ProofError> {
    if hash.len() != 64
        || !hash
            .bytes()
            .all(|byte| matches!(byte, b'0'..=b'9' | b'a'..=b'f'))
    {
        return Err(invalid("Original photo hash must be lowercase SHA-256"));
    }
    Ok(())
}

fn invalid(reason: &str) -> ProofError {
    ProofError::InvalidMetadata {
        reason: reason.to_owned(),
    }
}

fn copy_hashed(mut reader: impl Read, mut writer: impl Write) -> io::Result<String> {
    let mut hasher = Sha256::new();
    let mut buffer = [0_u8; 64 * 1024];
    loop {
        let count = match reader.read(&mut buffer) {
            Err(error) if error.kind() == io::ErrorKind::Interrupted => continue,
            result => result?,
        };
        if count == 0 {
            break;
        }
        let chunk = &buffer[..count];
        writer.write_all(chunk)?;
        hasher.update(chunk);
    }
    Ok(format!("{:x}", hasher.finalize()))
}
