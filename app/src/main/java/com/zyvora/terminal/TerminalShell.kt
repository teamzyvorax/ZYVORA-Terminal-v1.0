package com.zyvora.terminal

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class TerminalShell(private val context: Context) {
    val home = File(context.filesDir, "home").apply { mkdirs() }
    private var cwd = home.canonicalFile
    @Volatile private var currentProcess: Process? = null

    fun prompt(): String = "~/${relativeCwd()} $ "

    fun execute(raw: String): String {
        val input = raw.trim()
        if (input.isEmpty()) return ""
        if (input == "clear") return "\u000C"
        if (input == "exit") return "__EXIT__"

        val parts = input.split(Regex("\\s+"), limit = 2)
        return when (parts[0].lowercase(Locale.ROOT)) {
            "help" -> helpText()
            "neofetch" -> neofetch()
            "whoami" -> "zyvora"
            "pwd" -> cwd.absolutePath
            "cd" -> changeDirectory(parts.getOrNull(1)?.trim()?.ifEmpty { "~" } ?: "~")
            "date" -> SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.getDefault()).format(Date())
            "version" -> "ZYVORA Terminal 1.2 • Android ${android.os.Build.VERSION.RELEASE}"
            "about" -> "ZYVORA ULTRA Terminal\nNative Android terminal workspace\nNo login • No signup • Offline-first"
            else -> runShell(input)
        }
    }

    fun interrupt() {
        currentProcess?.destroy()
        currentProcess = null
    }

    private fun runShell(command: String): String {
        return try {
            val process = ProcessBuilder("/system/bin/sh", "-c", command)
                .directory(cwd)
                .redirectErrorStream(true)
                .apply {
                    environment()["HOME"] = home.absolutePath
                    environment()["PWD"] = cwd.absolutePath
                    environment()["PATH"] = "/system/bin:/system/xbin:/vendor/bin"
                }
                .start()
            currentProcess = process
            val finished = process.waitFor(15, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                currentProcess = null
                return "zyvora: command timed out (15s)"
            }
            val text = process.inputStream.bufferedReader().use { it.readText() }.trimEnd()
            currentProcess = null
            text.ifEmpty { if (process.exitValue() == 0) "" else "command failed (exit ${process.exitValue()})" }
        } catch (e: Exception) {
            currentProcess = null
            "zyvora: ${e.message ?: "command execution failed"}"
        }
    }

    private fun changeDirectory(target: String): String {
        val expanded = when {
            target == "~" -> home
            target.startsWith("~/") -> File(home, target.removePrefix("~/"))
            target.startsWith("/") -> File(target)
            else -> File(cwd, target)
        }
        val resolved = try { expanded.canonicalFile } catch (_: Exception) { return "cd: invalid path" }
        if (!resolved.exists() || !resolved.isDirectory) return "cd: no such directory: $target"
        if (!isInsideHome(resolved)) return "cd: access denied: $target"
        cwd = resolved
        return ""
    }

    private fun isInsideHome(file: File): Boolean {
        val root = home.canonicalPath
        val path = file.canonicalPath
        return path == root || path.startsWith(root + File.separator)
    }

    private fun relativeCwd(): String {
        val root = home.canonicalPath
        val path = cwd.canonicalPath
        return if (path == root) "home" else "home/" + path.removePrefix(root + File.separator)
    }

    private fun helpText() = """
ZYVORA ULTRA TERMINAL
────────────────────────────────
Built-in:
  help       Show this help
  clear      Clear terminal
  pwd        Show current directory
  cd <dir>   Change directory
  neofetch   Device information
  version    Terminal version
  about      About ZYVORA Terminal

Android shell:
  ls mkdir touch cat rm cp mv
  echo date whoami uname
  find grep head tail wc
  sleep printf test

Keyboard:
  ↑ ↓ history   ← → cursor   CTRL+C interrupt
  HOME END      TAB           PGUP PGDN

Workspace: $HOME
""".trimIndent()

    private fun neofetch() = """
ZYVORA ULTRA
─────────────
OS       Android ${android.os.Build.VERSION.RELEASE}
Device   ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}
Shell    /system/bin/sh
Terminal ZYVORA Terminal
Home     ${home.absolutePath}
""".trimIndent()
}
