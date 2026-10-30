package com.zyvora.terminal

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Window
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.io.File

/** Real PTY-backed ZYVORA ULTRA terminal. No simulated command output. */
class MainActivity : Activity(), TerminalSessionClient, TerminalViewClient {
    private lateinit var terminalView: TerminalView
    private lateinit var sessionBar: LinearLayout
    private lateinit var statusText: TextView
    private data class SessionItem(val session: TerminalSession, var name: String)
    private val sessions = mutableListOf<SessionItem>()
    private var current: SessionItem? = null
    private var ctrl = false
    private var alt = false
    private var shift = false
    private var fn = false
    private var fileManagerCwd: File? = null
    private var storageTree: Uri? = null

    private val bg = Color.rgb(2, 4, 6)
    private val panel = Color.rgb(7, 11, 14)
    private val green = Color.rgb(0, 255, 150)
    private val white = Color.rgb(235, 242, 239)
    private val muted = Color.rgb(125, 145, 137)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        storageTree = getPreferences(MODE_PRIVATE).getString("storage_tree", null)?.let(Uri::parse)
        buildUi()
        createSession("main")
        showKeyboard()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(10, 3, 6, 3)
            setBackgroundColor(panel)
        }
        val brand = TextView(this).apply {
            text = "ZYVORA ULTRA\nAdvanced Android Terminal"
            setTextColor(green)
            textSize = 12f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        header.addView(brand, LinearLayout.LayoutParams(0, 52, 1f))
        header.addView(toolButton("+", "New session") { createSession("session-${sessions.size + 1}") })
        header.addView(toolButton("S", "Sessions") { showSessions() })
        header.addView(toolButton("F", "Files") { showFileManager(homeDir()) })
        header.addView(toolButton("⋮", "Tools") { showTools() })
        root.addView(header, LinearLayout.LayoutParams(-1, 58))

        sessionBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(5, 2, 5, 2)
            setBackgroundColor(Color.rgb(4, 7, 9))
        }
        root.addView(sessionBar, LinearLayout.LayoutParams(-1, 38))

        terminalView = TerminalView(this, null).apply {
            setBackgroundColor(Color.BLACK)
            setTextSize(14)
            setTypeface(Typeface.MONOSPACE)
            setTerminalViewClient(this@MainActivity)
            isFocusableInTouchMode = true
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) showKeyboard()
            }
        }
        root.addView(terminalView, LinearLayout.LayoutParams(-1, 0, 1f))

        val extraKeys = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(panel)
            setPadding(2, 2, 2, 2)
        }
        addExtraRow(extraKeys, listOf("ESC", "TAB", "CTRL", "ALT", "SHIFT", "↑", "↓", "←", "→", "HOME"))
        addExtraRow(extraKeys, listOf("END", "PGUP", "PGDN", "^C", "^D", "^L", "^Z", "COPY", "PASTE", "/"))
        addExtraRow(extraKeys, listOf("-", "_", "|", "~", "`", "$", "&", ";", ":", "("))
        addExtraRow(extraKeys, listOf(")", "{", "}", "[", "]", "[]", "\u005c", "'", "\"", "."))
        root.addView(extraKeys, LinearLayout.LayoutParams(-1, 136))

        statusText = TextView(this).apply {
            text = " REAL PTY • Android shell • offline-first"
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setTextColor(muted)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 0, 8, 0)
            setBackgroundColor(Color.rgb(3, 6, 8))
        }
        root.addView(statusText, LinearLayout.LayoutParams(-1, 24))
        setContentView(root)
    }

    private fun toolButton(label: String, description: String, action: () -> Unit): TextView = TextView(this).apply {
        text = label
        contentDescription = description
        textSize = 18f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(white)
        gravity = Gravity.CENTER
        setPadding(9, 0, 9, 0)
        setOnClickListener { action() }
    }

    private fun addExtraRow(parent: LinearLayout, labels: List<String>) {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        labels.forEach { label ->
            val button = TextView(this).apply {
                text = label
                textSize = if (label.length > 4) 8f else 10f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                setTextColor(when {
                    label == "CTRL" && ctrl -> green
                    label == "ALT" && alt -> green
                    label == "SHIFT" && shift -> green
                    else -> white
                })
                gravity = Gravity.CENTER
                setPadding(1, 0, 1, 0)
                setOnClickListener { handleExtraKey(label) }
            }
            row.addView(button, LinearLayout.LayoutParams(0, 34, 1f))
        }
        parent.addView(row, LinearLayout.LayoutParams(-1, 34))
    }

    private fun handleExtraKey(key: String) {
        val session = current?.session ?: return
        when (key) {
            "ESC" -> writeBytes(session, byteArrayOf(27))
            "TAB" -> writeBytes(session, byteArrayOf(9))
            "CTRL" -> ctrl = !ctrl
            "ALT" -> alt = !alt
            "SHIFT" -> shift = !shift
            "↑" -> writeText(session, "\u001b[A")
            "↓" -> writeText(session, "\u001b[B")
            "→" -> writeText(session, "\u001b[C")
            "←" -> writeText(session, "\u001b[D")
            "HOME" -> writeText(session, "\u001b[H")
            "END" -> writeText(session, "\u001b[F")
            "PGUP" -> writeText(session, "\u001b[5~")
            "PGDN" -> writeText(session, "\u001b[6~")
            "^C" -> writeBytes(session, byteArrayOf(3))
            "^D" -> writeBytes(session, byteArrayOf(4))
            "^L" -> writeBytes(session, byteArrayOf(12))
            "^Z" -> writeBytes(session, byteArrayOf(26))
            "COPY" -> Toast.makeText(this, "Long-press terminal text to select/copy", Toast.LENGTH_SHORT).show()
            "PASTE" -> pasteClipboard(session)
            else -> writeText(session, key)
        }
        terminalView.requestFocus()
        showKeyboard()
        updateStatus()
    }

    private fun writeText(session: TerminalSession, text: String) {
        writeBytes(session, text.toByteArray(Charsets.UTF_8))
    }

    private fun writeBytes(session: TerminalSession, bytes: ByteArray) {
        session.write(bytes, 0, bytes.size)
    }

    private fun createSession(name: String) {
        val home = homeDir()
        val env = arrayOf(
            "HOME=${home.absolutePath}",
            "PWD=${home.absolutePath}",
            "PATH=/system/bin:/system/xbin:/vendor/bin",
            "SHELL=/system/bin/sh",
            "TERM=xterm-256color",
            "LANG=C.UTF-8",
            "TMPDIR=${cacheDir.absolutePath}",
            "ZYVORA_TERMINAL=1"
        )
        val session = TerminalSession("/system/bin/sh", home.absolutePath, arrayOf("-i"), env, 4000, this)
        val item = SessionItem(session, name)
        sessions.add(item)
        switchSession(item)
    }

    private fun switchSession(item: SessionItem) {
        current = item
        terminalView.attachSession(item.session)
        updateSessionBar()
        updateStatus()
        terminalView.requestFocus()
        showKeyboard()
    }

    private fun updateSessionBar() {
        sessionBar.removeAllViews()
        sessions.forEachIndexed { index, item ->
            val tab = TextView(this).apply {
                text = " ${index + 1}:${item.name} "
                textSize = 10f
                typeface = Typeface.MONOSPACE
                setTextColor(if (item == current) green else muted)
                gravity = Gravity.CENTER
                setPadding(7, 0, 7, 0)
                setOnClickListener { switchSession(item) }
                setOnLongClickListener {
                    renameSession(item)
                    true
                }
            }
            sessionBar.addView(tab, LinearLayout.LayoutParams(0, 34, 1f))
        }
    }

    private fun showSessions() {
        if (sessions.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("ZYVORA Sessions")
            .setItems(sessions.map { it.name }.toTypedArray()) { _, which -> switchSession(sessions[which]) }
            .setPositiveButton("New") { _, _ -> createSession("session-${sessions.size + 1}") }
            .setNegativeButton("Close Current") { _, _ -> closeCurrentSession() }
            .show()
    }

    private fun renameSession(item: SessionItem) {
        val edit = EditText(this).apply {
            setText(item.name)
            selectAll()
        }
        AlertDialog.Builder(this)
            .setTitle("Rename session")
            .setView(edit)
            .setPositiveButton("Save") { _, _ ->
                val value = edit.text.toString().trim()
                if (value.isNotEmpty()) item.name = value
                updateSessionBar()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun closeCurrentSession() {
        val item = current ?: return
        item.session.finishIfRunning()
        sessions.remove(item)
        if (sessions.isEmpty()) createSession("main") else switchSession(sessions.last())
    }

    private fun showTools() {
        val items = arrayOf("Command Browser", "Setup Storage", "SSH Profiles", "Runtime Status", "App Files", "About")
        AlertDialog.Builder(this)
            .setTitle("ZYVORA ULTRA Tools")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showCommandBrowser()
                    1 -> setupStorage()
                    2 -> showSshProfiles()
                    3 -> showRuntimeStatus()
                    4 -> showFileManager(homeDir())
                    5 -> showAbout()
                }
            }
            .show()
    }

    private fun showCommandBrowser() {
        val text = """
LINUX BASICS
ls  cd  pwd  mkdir  rmdir  touch  cp  mv  rm
cat  head  tail  less  more  grep  find  sort  uniq

SHELL
echo  printf  clear  reset  env  export  which
whoami  uname  date  df  du  chmod  ps  kill

NETWORK
ping  curl  wget  ssh  scp  ss

DEVELOPMENT
python  python3  pip  node  npm  git  gcc  g++  javac

PACKAGE LAYER (when a Linux userspace is installed)
pkg update
pkg install <package>
pkg search <package>
apt update
apt install <package>

SCRIPTING
./script.sh
python script.py
node app.js

All commands execute in the real PTY. Missing commands are never faked.
""".trimIndent()
        AlertDialog.Builder(this).setTitle("Command Browser").setMessage(text).setPositiveButton("Close", null).show()
    }

    private fun showRuntimeStatus() {
        val message = """
ZYVORA ULTRA Runtime

PTY terminal: READY
Android shell: /system/bin/sh
ANSI/VT terminal: READY
Multiple sessions: READY

This build never fabricates pkg/apt/python/node/git output.
Those commands run only when the active userspace provides the executable.
A separate Linux userspace/runtime is required for a full package ecosystem.
""".trimIndent()
        AlertDialog.Builder(this).setTitle("Runtime Status").setMessage(message).setPositiveButton("OK", null).show()
    }

    private fun setupStorage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_STORAGE)
    }

    @Deprecated("Legacy result callback retained for minSdk compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_STORAGE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }
                storageTree = uri
                getPreferences(MODE_PRIVATE).edit().putString("storage_tree", uri.toString()).apply()
                Toast.makeText(this, "Storage access saved", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showFileManager(start: File) {
        fileManagerCwd = start
        renderFileManager()
    }

    private fun renderFileManager() {
        val dir = fileManagerCwd ?: homeDir()
        val entries = dir.listFiles()?.sortedWith(
            compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }
        ) ?: emptyList()
        val labels = mutableListOf("../")
        entries.forEach { entry ->
            labels.add(if (entry.isDirectory) "[DIR] ${entry.name}/" else "[FILE] ${entry.name}")
        }
        AlertDialog.Builder(this)
            .setTitle("Files • ${dir.name.ifEmpty { "home" }}")
            .setItems(labels.toTypedArray()) { _, which ->
                if (which == 0) {
                    dir.parentFile?.let {
                        fileManagerCwd = it
                        renderFileManager()
                    }
                } else {
                    val entry = entries[which - 1]
                    if (entry.isDirectory) {
                        fileManagerCwd = entry
                        renderFileManager()
                    } else {
                        openEditor(entry)
                    }
                }
            }
            .setPositiveButton("New") { _, _ -> showNewFileDialog(dir) }
            .setNeutralButton("Storage") { _, _ -> setupStorage() }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showNewFileDialog(dir: File) {
        val edit = EditText(this).apply { hint = "file or folder name" }
        AlertDialog.Builder(this)
            .setTitle("Create")
            .setView(edit)
            .setPositiveButton("File") { _, _ ->
                edit.text.toString().trim().takeIf { it.isNotEmpty() }?.let { File(dir, it).createNewFile() }
                renderFileManager()
            }
            .setNeutralButton("Folder") { _, _ ->
                edit.text.toString().trim().takeIf { it.isNotEmpty() }?.let { File(dir, it).mkdirs() }
                renderFileManager()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openEditor(file: File) {
        if (file.length() > 2_000_000) {
            Toast.makeText(this, "File too large for built-in editor", Toast.LENGTH_SHORT).show()
            return
        }
        val editor = EditText(this).apply {
            setText(try { file.readText() } catch (_: Exception) { "" })
            gravity = Gravity.TOP or Gravity.START
            typeface = Typeface.MONOSPACE
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            minLines = 14
        }
        AlertDialog.Builder(this)
            .setTitle("Editor • ${file.name}")
            .setView(editor)
            .setPositiveButton("Save") { _, _ ->
                try {
                    file.writeText(editor.text.toString())
                    Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .setNeutralButton("Share") { _, _ -> shareFile(file) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun shareFile(file: File) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, try { file.readText().take(200_000) } catch (_: Exception) { "" })
            putExtra(Intent.EXTRA_TITLE, file.name)
        }
        startActivity(Intent.createChooser(intent, "Share ${file.name}"))
    }

    private fun showSshProfiles() {
        val prefs = getPreferences(MODE_PRIVATE)
        val profiles = prefs.getStringSet("ssh_profiles", emptySet())?.toMutableSet() ?: mutableSetOf()
        val values = if (profiles.isEmpty()) {
            arrayOf("+ Add SSH profile")
        } else {
            profiles.toTypedArray() + "+ Add SSH profile"
        }
        AlertDialog.Builder(this)
            .setTitle("SSH Profiles")
            .setItems(values) { _, which ->
                if (which == values.lastIndex) {
                    addSshProfile(profiles)
                } else {
                    connectSsh(values[which])
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun addSshProfile(profiles: MutableSet<String>) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 4, 20, 4)
        }
        val user = EditText(this).apply { hint = "username" }
        val host = EditText(this).apply { hint = "host" }
        val port = EditText(this).apply {
            hint = "port (22)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        box.addView(user)
        box.addView(host)
        box.addView(port)
        AlertDialog.Builder(this)
            .setTitle("Add SSH profile")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val u = user.text.toString().trim()
                val h = host.text.toString().trim()
                val p = port.text.toString().trim().ifEmpty { "22" }
                if (u.isNotEmpty() && h.isNotEmpty()) {
                    profiles.add("$u@$h:$p")
                    getPreferences(MODE_PRIVATE).edit().putStringSet("ssh_profiles", profiles).apply()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun connectSsh(profile: String) {
        val at = profile.indexOf('@')
        val colon = profile.lastIndexOf(':')
        if (at > 0 && colon > at) {
            val user = profile.substring(0, at)
            val host = profile.substring(at + 1, colon)
            val port = profile.substring(colon + 1)
            writeText(current?.session ?: return, "ssh -p $port $user@$host\n")
            terminalView.requestFocus()
            showKeyboard()
        }
    }

    private fun pasteClipboard(session: TerminalSession) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (clipboard.hasPrimaryClip()) {
            val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
            writeText(session, text)
        }
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle("ZYVORA ULTRA Terminal")
            .setMessage("Advanced Android Terminal\n\nYOUR TERMINAL. YOUR POWER.\n\nReal PTY + ANSI terminal emulator\nNative Android shell\nMultiple sessions\nTerminal extra keys\nIntegrated file manager\nSSH profile helper\nCommand browser\nNo login • No signup")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun homeDir(): File = File(filesDir, "home").apply { mkdirs() }

    private fun updateStatus() {
        statusText.text = " PTY • ${current?.name ?: "-"} • /system/bin/sh • ${if (ctrl) "CTRL " else ""}${if (alt) "ALT" else ""}"
    }

    private fun showKeyboard() {
        terminalView.postDelayed({
            terminalView.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT)
        }, 250)
    }

    override fun onTextChanged(changedSession: TerminalSession) {
        if (changedSession == current?.session) terminalView.postInvalidate()
    }
    override fun onTitleChanged(changedSession: TerminalSession) = Unit
    override fun onSessionFinished(finishedSession: TerminalSession) = updateStatus()
    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("ZYVORA Terminal", text))
    }
    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        session?.let { pasteClipboard(it) }
    }
    override fun onBell(session: TerminalSession) = Unit
    override fun onColorsChanged(session: TerminalSession) = terminalView.postInvalidate()
    override fun onTerminalCursorStateChange(state: Boolean) = Unit
    override fun getTerminalCursorStyle(): Int = 0
    override fun logError(tag: String, message: String) = Unit
    override fun logWarn(tag: String, message: String) = Unit
    override fun logInfo(tag: String, message: String) = Unit
    override fun logDebug(tag: String, message: String) = Unit
    override fun logVerbose(tag: String, message: String) = Unit
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) = Unit
    override fun logStackTrace(tag: String, e: Exception) = Unit

    override fun onScale(scale: Float): Float = scale
    override fun onSingleTapUp(e: MotionEvent) {
        terminalView.requestFocus()
        showKeyboard()
    }
    override fun shouldBackButtonBeMappedToEscape(): Boolean = true
    override fun shouldEnforceCharBasedInput(): Boolean = true
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = true
    override fun isTerminalViewSelected(): Boolean = true
    override fun copyModeChanged(copyMode: Boolean) = Unit
    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean = false
    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false
    override fun onLongPress(event: MotionEvent): Boolean = false
    override fun readControlKey(): Boolean = ctrl
    override fun readAltKey(): Boolean = alt
    override fun readShiftKey(): Boolean = shift
    override fun readFnKey(): Boolean = fn
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false
    override fun onEmulatorSet() = Unit

    override fun onDestroy() {
        sessions.forEach { it.session.finishIfRunning() }
        sessions.clear()
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_STORAGE = 401
    }
}
