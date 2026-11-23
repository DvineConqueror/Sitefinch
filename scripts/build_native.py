"""Generate Kotlin bindings and build the Rust library for each Android ABI."""

import argparse
import os
from pathlib import Path
import platform
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]
TARGET_DIR = ROOT / "target"
ANDROID_TARGETS = {
    "arm64-v8a": "aarch64-linux-android",
    "x86_64": "x86_64-linux-android",
}
PACKAGE_ARGS = ["--locked", "--package", "proof-core", "--target-dir", str(TARGET_DIR)]


def cargo(*arguments, environment=None):
    subprocess.run(["cargo", *arguments], cwd=ROOT, env=environment, check=True)


def generate_bindings(output):
    host_library = {
        "Darwin": "libproof_core.dylib",
        "Linux": "libproof_core.so",
        "Windows": "proof_core.dll",
    }[platform.system()]
    cargo("build", *PACKAGE_ARGS, "--lib")
    cargo(
        "run", *PACKAGE_ARGS, "--features", "bindgen", "--bin", "uniffi-bindgen",
        "--", "generate", "--library", str(TARGET_DIR / "debug" / host_library),
        "--language", "kotlin", "--out-dir", str(output), "--no-format",
    )


def build_android(output, ndk_version, minimum_api, abis):
    sdk = os.environ.get("ANDROID_HOME")
    if sdk is None:
        raise SystemExit("Set ANDROID_HOME to the Android SDK directory.")
    host_tag, executable = {
        "Darwin": ("darwin-x86_64", "clang"),
        "Linux": ("linux-x86_64", "clang"),
        "Windows": ("windows-x86_64", "clang.exe"),
    }[platform.system()]
    toolchain = Path(sdk) / "ndk" / ndk_version / "toolchains" / "llvm" / "prebuilt" / host_tag
    clang = toolchain / "bin" / executable
    if not clang.is_file():
        raise SystemExit(f"Android NDK {ndk_version} is required. Missing {clang}.")

    for abi in abis:
        target = ANDROID_TARGETS[abi]
        environment = os.environ.copy()
        target_variable = target.upper().replace("-", "_")
        environment[f"CARGO_TARGET_{target_variable}_LINKER"] = str(clang)
        flags = [
            f"-Clink-arg=--target={target}{minimum_api}",
            f"-Clink-arg=--sysroot={toolchain / 'sysroot'}",
        ]
        old_flags = environment.get("CARGO_ENCODED_RUSTFLAGS")
        if old_flags:
            flags.insert(0, old_flags)
        environment["CARGO_ENCODED_RUSTFLAGS"] = "\x1f".join(flags)
        cargo("build", *PACKAGE_ARGS, "--lib", "--release", "--target", target, environment=environment)

        destination = output / abi
        destination.mkdir(parents=True, exist_ok=True)
        shutil.copy2(TARGET_DIR / target / "release" / "libproof_core.so", destination)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    bindings = commands.add_parser("bindings")
    bindings.add_argument("output", type=Path)
    android = commands.add_parser("android")
    android.add_argument("output", type=Path)
    android.add_argument("ndk_version")
    android.add_argument("minimum_api", type=int)
    android.add_argument("abis", nargs="+", choices=ANDROID_TARGETS)
    args = parser.parse_args()

    if args.command == "bindings":
        generate_bindings(args.output)
    else:
        build_android(args.output, args.ndk_version, args.minimum_api, args.abis)


if __name__ == "__main__":
    main()
