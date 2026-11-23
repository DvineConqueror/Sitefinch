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

This pass was static. Exact capture times, team synchronization, entitlement state, and backend verification behavior were outside the observed evidence. Work Photo Log uses its own capture and export implementation. Timemark's public product documentation describes the user-facing business features. [Timemark help](https://www.timemark.com/faq)
