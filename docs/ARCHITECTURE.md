# Architecture

v1.0 is intentionally small and safe: Kotlin Android UI + an app-private workspace + a command dispatcher. No root access and no remote command execution are required.

v2 should replace the dispatcher with an app-owned PTY/native layer. This is the architecture used by real Android terminal projects because interactive shells and terminal control sequences require a real process channel rather than a fake output renderer.
