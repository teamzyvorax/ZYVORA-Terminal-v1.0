package com.zyvora.terminal

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var output: TextView
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var prompt: TextView
    private lateinit var shell: TerminalShell
    private val history = mutableListOf<String>()
    private var historyIndex = 0
    private var ctrlArmed = false
    private val executor = Executors.newSingleThreadExecutor()

    private val bg = Color.rgb(3, 5, 7)
    private val panel = Color.rgb(9, 12, 15)
    private val green = Color.rgb(0, 255, 150)
    private val white = Color.rgb(232, 240, 236)
    private val muted = Color.rgb(120, 140, 132)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        shell = TerminalShell(this)
        buildUi()
        printLine("Welcome to ZYVORA Terminal")
        printLine("")
        printLine("ZYVORA ULTRA • Android shell workspace")
        printLine("Type 'help' for commands.")
        printLine("")
        updatePrompt()
        input.requestFocus()
        input.postDelayed({ showKeyboard() }, 350)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        val title = TextView(this).apply {
            text = "  ZYVORA ULTRA TERMINAL"
            textSize = 14f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(green)
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.rgb(5, 8, 10))
        }
        root.addView(title, LinearLayout.LayoutParams(-1, 38))

        scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.BLACK)
        }
        output = TextView(this).apply {
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setTextColor(white)
            setPadding(10, 10, 10, 10)
            setTextIsSelectable(true)
            isFocusable = false
            setLineSpacing(0f, 1.05f)
        }
        scroll.addView(output, ViewGroup.LayoutParams(-1, -1))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val extraKeys = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(panel)
            setPadding(2, 2, 2, 2)
        }
        addKeyRow(extraKeys, listOf("ESC", "TAB", "CTRL", "ALT", "↑", "↓", "←", "→", "HOME", "END"))
        addKeyRow(extraKeys, listOf("PGUP", "PGDN", "^C", "/", "~", "|", "-", "_", "COPY", "PASTE"))
        root.addView(extraKeys, LinearLayout.LayoutParams(-1, 72))

        val commandRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(panel)
            setPadding(5, 1, 5, 2)
        }
        prompt = TextView(this).apply {
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setTextColor(green)
            setPadding(2, 0, 2, 0)
        }
        input = EditText(this).apply {
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(muted)
            hint = "type command..."
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(4, 0, 4, 0)
            showSoftInputOnFocus = true
            isFocusableInTouchMode = true
            imeOptions = EditorInfo.IME_ACTION_DONE
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        val enter = TextView(this).apply {
            text = "↵"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(green)
            gravity = Gravity.CENTER
            setPadding(10, 0, 10, 0)
            setOnClickListener { execute() }
        }
        commandRow.addView(prompt, LinearLayout.LayoutParams(-2, 50))
        commandRow.addView(input, LinearLayout.LayoutParams(0, 50, 1f))
        commandRow.addView(enter, LinearLayout.LayoutParams(55, 50))
        root.addView(commandRow)
        setContentView(root)

        input.setOnEditorActionListener { _, _, _ -> execute(); true }
        input.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> { historyMove(-1); true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { historyMove(1); true }
                KeyEvent.KEYCODE_TAB -> { insertAtCursor("\t"); true }
                KeyEvent.KEYCODE_ENTER -> { execute(); true }
                else -> false
            }
        }
        input.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) showKeyboard() }
    }

    private fun addKeyRow(parent: LinearLayout, keys: List<String>) {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        keys.forEach { label ->
            val b = TextView(this).apply {
                text = label
                textSize = if (label.length > 4) 9f else 11f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                setTextColor(if (label == "CTRL" && ctrlArmed) green else white)
                gravity = Gravity.CENTER
                setPadding(1, 0, 1, 0)
                setOnClickListener { handleExtraKey(label) }
                setBackgroundColor(Color.TRANSPARENT)
            }
            row.addView(b, LinearLayout.LayoutParams(0, 34, 1f))
        }
        parent.addView(row, LinearLayout.LayoutParams(-1, 34))
    }

    private fun handleExtraKey(key: String) {
        when (key) {
            "ESC" -> insertAtCursor("\u001b")
            "TAB" -> insertAtCursor("\t")
            "CTRL" -> { ctrlArmed = !ctrlArmed; printLine(if (ctrlArmed) "[CTRL] armed" else "[CTRL] off") }
            "ALT" -> insertAtCursor("\u001b")
            "^C" -> { shell.interrupt(); printLine("^C") }
            "↑" -> historyMove(-1)
            "↓" -> historyMove(1)
            "←" -> moveCursor(-1)
            "→" -> moveCursor(1)
            "HOME" -> input.setSelection(0)
            "END" -> input.setSelection(input.text.length)
            "PGUP" -> scroll.smoothScrollTo(0, 0)
            "PGDN" -> scroll.fullScroll(View.FOCUS_DOWN)
            "COPY" -> copyInput()
            "PASTE" -> pasteInput()
            else -> {
                if (ctrlArmed && key.length == 1) {
                    val c = key[0].uppercaseChar().code
                    insertAtCursor((c - 64).toChar().toString())
                    ctrlArmed = false
                } else insertAtCursor(key)
            }
        }
        input.requestFocus()
        showKeyboard()
    }

    private fun insertAtCursor(value: String) {
        val start = input.selectionStart.coerceAtLeast(0)
        val end = input.selectionEnd.coerceAtLeast(0)
        input.text.replace(minOf(start, end), maxOf(start, end), value)
        input.setSelection((minOf(start, end) + value.length).coerceAtMost(input.text.length))
    }

    private fun moveCursor(delta: Int) {
        val pos = (input.selectionStart + delta).coerceIn(0, input.text.length)
        input.setSelection(pos)
    }

    private fun historyMove(delta: Int) {
        if (history.isEmpty()) return
        historyIndex = (historyIndex + delta).coerceIn(0, history.size)
        input.setText(if (historyIndex < history.size) history[historyIndex] else "")
        input.setSelection(input.text.length)
    }

    private fun copyInput() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("ZYVORA Terminal", input.text.toString()))
    }

    private fun pasteInput() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (cm.hasPrimaryClip()) insertAtCursor(cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() ?: "")
    }

    private fun execute() {
        val command = input.text.toString().trim()
        if (command.isEmpty()) return
        history.add(command)
        historyIndex = history.size
        printLine(prompt.text.toString().trimEnd() + command)
        input.text.clear()
        input.isEnabled = false
        executor.execute {
            val result = shell.execute(command)
            runOnUiThread {
                input.isEnabled = true
                when (result) {
                    "\u000C" -> output.text = ""
                    "__EXIT__" -> printLine("Session closed. Type a command to start again.")
                    else -> if (result.isNotEmpty()) printLine(result)
                }
                updatePrompt()
                input.requestFocus()
                showKeyboard()
            }
        }
    }

    private fun updatePrompt() {
        prompt.text = shell.prompt()
    }

    private fun printLine(text: String) {
        output.append(if (output.text.isEmpty()) text else "\n$text")
        output.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun showKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun onDestroy() {
        shell.interrupt()
        executor.shutdownNow()
        super.onDestroy()
    }
}
