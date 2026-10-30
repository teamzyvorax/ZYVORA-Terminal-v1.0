package com.zyvora.terminal

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TerminalShell(private val context: Context) {
    private val home = File(context.filesDir, "home").apply { mkdirs() }
    private var cwd = home

    fun execute(raw: String): String {
        val input = raw.trim()
        if (input.isEmpty()) return ""
        val p = input.split(Regex("\\s+"))
        return when (p[0].lowercase(Locale.ROOT)) {
            "help" -> "ZYVORA Terminal commands:\n  help clear echo pwd ls cd mkdir touch cat rm date whoami uname neofetch\n\nWorkspace: ${home.absolutePath}"
            "clear" -> "\u000C"
            "echo" -> input.removePrefix(p[0]).trim()
            "pwd" -> cwd.absolutePath
            "ls" -> cwd.listFiles()?.joinToString("\n") { if (it.isDirectory) "${it.name}/" else it.name } ?: ""
            "cd" -> cd(p.getOrNull(1) ?: home.absolutePath)
            "mkdir" -> createDir(p.getOrNull(1))
            "touch" -> createFile(p.getOrNull(1))
            "cat" -> readFile(p.getOrNull(1))
            "rm" -> remove(p.getOrNull(1))
            "date" -> SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.getDefault()).format(Date())
            "whoami" -> "zyvora"
            "uname" -> "Android ${android.os.Build.VERSION.RELEASE} ${android.os.Build.SOC_MANUFACTURER} ${android.os.Build.MODEL}"
            "neofetch" -> "ZYVORA ULTRA\n──────────────\nOS      Android ${android.os.Build.VERSION.RELEASE}\nDevice  ${android.os.Build.MODEL}\nShell   ZYVORA Terminal\nHome    ${home.absolutePath}"
            else -> "zyvora: command not found: ${p[0]}"
        }
    }

    private fun cd(target: String): String {
        val next = if (target == "~") home else if (target.startsWith("/")) File(target) else File(cwd, target)
        val resolved = next.canonicalFile
        if (!resolved.exists() || !resolved.isDirectory) return "cd: no such directory: $target"
        if (!resolved.path.startsWith(home.canonicalPath)) return "cd: access denied"
        cwd = resolved
        return ""
    }

    private fun createDir(name: String?): String {
        if (name == null) return "mkdir: missing operand"
        return if (File(cwd, name).mkdirs()) "" else "mkdir: cannot create '$name'"
    }

    private fun createFile(name: String?): String {
        if (name == null) return "touch: missing operand"
        return try { File(cwd, name).createNewFile(); "" } catch (_: Exception) { "touch: cannot create '$name'" }
    }

    private fun readFile(name: String?): String {
        if (name == null) return "cat: missing operand"
        val f = File(cwd, name)
        return if (f.isFile) f.readText() else "cat: $name: No such file"
    }

    private fun remove(name: String?): String {
        if (name == null) return "rm: missing operand"
        val f = File(cwd, name)
        return if (f.exists() && f.deleteRecursively()) "" else "rm: cannot remove '$name'"
    }
}
