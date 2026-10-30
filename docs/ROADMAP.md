# ZYVORA Terminal Roadmap

## v1.0
- Native Android shell-style interface
- App-private workspace
- Basic filesystem commands
- GitHub Actions APK build

## v2.0
- Native PTY backend
- Real interactive shell sessions
- Multiple tabs/sessions
- Terminal resize and scrollback
- Extra mobile keys: ESC, TAB, CTRL, ALT

## v3.0
- Optional Linux userland through a supported userspace environment
- Git, SSH, Python and package-management workflows
- Persistent foreground terminal service
- File manager and shared-storage integration

## Design rule
Do not fake command output. Commands should either execute locally or clearly report that a capability is unavailable.
