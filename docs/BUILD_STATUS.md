# Build Status

The repository is configured to trigger GitHub Actions on pushes to `main`.

The Android APK build is the source of truth for compile verification. Do not mark a release as verified until the Actions job completes successfully and an APK artifact is available.

## Current implementation
The terminal UI and local shell engine are committed to `main`.

## Next engineering milestone
Replace the command dispatcher with a native PTY backend so interactive programs and real shell processes can run.
