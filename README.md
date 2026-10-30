# ZYVORA Terminal v1.0

A zero-cost, Android-first terminal app with a ZYVORA ULTRA cyber terminal interface and a local sandboxed command workspace.

## Current v1 features

- ZYVORA ULTRA terminal UI
- No sign-up or login
- Local-first private workspace inside the app
- Command history
- `help`, `clear`, `echo`, `pwd`, `ls`, `cd`, `mkdir`, `touch`, `cat`, `rm`, `rmdir`
- `date`, `whoami`, `uname`, `neofetch`, `about`
- Safe filesystem boundary: commands cannot escape the app's ZYVORA home directory
- GitHub Actions APK build

## Important scope

This v1 is **Termux-style**, not a replacement for the full Termux Linux distribution. Android sandboxing means a true Linux userland with packages such as Python, Node.js, Git, SSH and apt requires a deeper native userland/PTY architecture. The v1 foundation is intentionally small, offline-friendly and safe.

## Build for free

1. Open the repository on GitHub.
2. Go to **Actions**.
3. Select **Build ZYVORA Terminal APK**.
4. Run it with **workflow_dispatch**, or push to `main`.
5. Download `ZYVORA-Terminal-debug-apk` from the completed workflow run.

The workflow uses Android Gradle Plugin 9.4.0 and Gradle 9.6.0.

## Next roadmap

- Real PTY terminal engine
- Terminal tabs/sessions
- Copy/paste and volume-key shortcuts
- ANSI colors and richer terminal rendering
- Optional package/userland layer
- Git + SSH tooling
- Release APK signing and GitHub Releases automation

## License

MIT. See `LICENSE`.
