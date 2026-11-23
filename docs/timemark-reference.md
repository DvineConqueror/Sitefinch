# Timemark Android package findings

| Input | Package | Build | SHA-256 |
| --- | --- | --- | --- |
| `Timemark_com.oceangalaxy.camera.new_100032500_Cd54h.apk` | `com.oceangalaxy.camera.new` | `v10.0.325` / `100032500` | `d5cf7aac0f1a30f59641438fcdb8e1c51cb31da59525e85321d67e47cdc68a48` |
| Earlier APKMirror bundle | `com.oceangalaxy.camera.new` | `v10.0.60` / `100006000` | `c11981dbe46586089bdd4f290d5a49af9aa17a4e9877a29a9b0cacc2ed58d9d6` |

## Package and bytecode evidence

REA classified the newer attachment as an APK. Its archive inspection retained 4,923 occurrences with zero reported integrity contradictions. The parent Evidence ID is `ev_0ceb5b3b4c1f4fc8ddf1ef5b710cd8e6313139bde893c2a9e304fddf82467d2e`. The Android provider reported that the optional JADX MCP JAR was not configured. Android SDK Build Tools 35.0.0 provided manifest badging, signing verification, and static DEX disassembly of `classes8.dex`.

The new APK contains nine DEX entries, CameraX 1.5.0 and a Hermes bytecode bundle with header version 96. Its manifest targets Android API 36 and names `PreviewActivity` as the launcher activity. The APK has no `lib/<abi>/*.so` members. The earlier split bundle carried React Native and Hermes native libraries in its ABI split. The new APK's signing block verified against signer certificate SHA-256 `96fcc4571a3c269d0bae4ca6fa786d236d8b61a691d58406c3805996f53f123f`. This identifies the signer for the inspected bytes.

| Question | Observation in v10.0.325 |
| --- | --- |
| Camera and templates | Launcher `PreviewActivity`, class definition at `classes8.dex+0x16d034`, references a public result template at `+0x68c194` and a team photo template at `+0x68d132`. `SubmitCompanyTemplateActivity` has a separate class definition at `+0x16e774`. |
| Local photo model | `LocalPhotoInfo` has a class definition at `classes8.dex+0x1628d4`. |
| Reporting | `PdfExporter` invokes Android Canvas bitmap rendering at `classes8.dex+0x649112`. |
| Archive writing | `ZipUtil.zipFiles` invokes `ZipOutputStream.putNextEntry` at `classes8.dex+0x799106`. The call establishes a ZIP helper. Its caller-to-UI linkage is unresolved. |

The package findings are static. Sitefinch uses its own capture and export implementation. Timemark's public product documentation describes its service features. [Timemark help](https://www.timemark.com/faq)

## Recorded interaction

The screen recording is 2:14 long. Frame review establishes the following visible interactions.

| Time | Observed interaction | Sitefinch implementation |
| --- | --- | --- |
| 0:14–0:32 | Camera, template gallery, template editor, and logo settings with position and crop | Searchable job presets and reusable saved styles, field editing, and gallery logo import with placement and crop preview |
| 0:35–0:41 | Size and text-color controls carry a Plus label. A separate row selects a palette. | Size, layout, position, accent and text color controls available to all users |
| 0:47–1:14 | Zoom selection and nearby address chooser, including the location-disabled state | Native camera zoom controls and editable address with optional Android lookup |
| 1:29–1:41 | Photo preview, markup tools, and share actions | Preview edits for stamp, pen, box, arrow, and text. Original and derivative sharing are separate actions. |
| 1:44–1:56 | Developer watermark with a subscription label, then batch export | Media has no developer attribution. Local projects export JPEGs with a ZIP manifest. |
| 1:59–2:13 | Video record, timer, pause and stop, then separate Clock and Checklist modes | Photo and video capture, with pause, stop, and opt-in microphone audio for video |

Later sample photos show a time-first layout with a job address and a separate, company-led completion stamp. Sitefinch provides a Clock layout and job presets as independent options. The reference screenshots, recorded photos, and logos stay outside the app and repository.

## Sitefinch v0.2.0 coverage

Sitefinch covers the local camera, stamp and sharing workflow. The equivalent to Timemark's paid clean-original and watermark-removal actions is sharing the saved original JPEG. A stamped copy can be adjusted after capture and carries only the selected job fields and logo. The recording shows Plus labels on size, text color and the address chooser. Sitefinch includes size and text-color controls and uses Android's address lookup with manual entry as a fallback. [Timemark plan comparison](https://www.timemark.com/pricing)

Sitefinch records device time and compares saved originals against a local SHA-256 digest. The local photo code identifies a record. Timemark additionally offers network-backed verification, Teamspace, public template sharing, address bookmarks, attendance, checklists, cloud backup and PDF, Excel, collage and KMZ exports. Those are outside the v0.2.0 feature set. [Timemark mobile feature guide](https://help.timemark.com/en/app/) · [Timemark photo verification](https://www.timemark.com/features/photo-verification)
