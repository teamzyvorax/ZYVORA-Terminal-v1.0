# Next: v2 Native Terminal

The next build should use a native PTY architecture rather than a simulated command dispatcher. Real Android terminal projects commonly combine a terminal emulator with an app-owned PTY/JNI layer; this enables interactive programs instead of only predefined commands.

Planned commands/features: bash/sh, tab completion, Ctrl/Alt/Tab/ESC keys, scrollback, multiple sessions, foreground service, and optional Linux userland.
