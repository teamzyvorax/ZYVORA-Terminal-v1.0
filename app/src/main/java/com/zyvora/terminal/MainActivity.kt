package com.zyvora.terminal

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*

class MainActivity : Activity() {
    private lateinit var output: TextView
    private lateinit var input: EditText
    private lateinit var prompt: TextView
    private lateinit var shell: TerminalShell
    private val history = mutableListOf<String>()
    private var historyIndex = -1

    private val bg = Color.rgb(4, 7, 10)
    private val panel = Color.rgb(8, 12, 16)
    private val green = Color.rgb(0, 255, 170)
    private val white = Color.rgb(225, 240, 232)
    private val muted = Color.rgb(135, 150, 145)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        shell = TerminalShell(this)
        buildUi()
        printLine("ZYVORA ULTRA TERMINAL v1.1")
        printLine("Type 'help' for commands.")
        printLine("Keyboard ready • Tap the command line to type.\n")
        input.requestFocus()
        input.postDelayed({ showKeyboard() }, 250)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(10, 8, 10, 6)
        }

        val title = TextView(this).apply {
            text = "ZYVORA ULTRA  •  TERMINAL"
            textSize = 15f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(green)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(4, 0, 4, 6)
        }
        root.addView(title, LinearLayout.LayoutParams(-1, 44))

        output = TextView(this).apply {
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setTextColor(white)
            setBackgroundColor(Color.BLACK)
            setPadding(12, 12, 12, 12)
            setTextIsSelectable(true)
            isFocusable = false
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(output, ViewGroup.LayoutParams(-1, -1))
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val extraKeys = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(panel)
            setPadding(2, 3, 2, 3)
        }
        addKeyRow(extraKeys, listOf("ESC", "TAB", "CTRL", "ALT", "↑", "↓", "←", "→", "HOME", "END"))
        addKeyRow(extraKeys, listOf("PGUP", "PGDN", "/", "~", "|", "-", "_", "COPY", "PASTE"))
        root.addView(extraKeys, LinearLayout.LayoutParams(-1, 72))

        val commandRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(panel)
            setPadding(4, 2, 4, 2)
        }
        prompt = TextView(this).apply {
            text = "~/home $ "
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
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        }
        val run = TextView(this).apply {
            text = "ENTER"
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(green)
            gravity = Gravity.CENTER
            setPadding(10, 0, 10, 0)
            setOnClickListener { execute() }
        }
        commandRow.addView(prompt, LinearLayout.LayoutParams(-2, 52))
        commandRow.addView(input, LinearLayout.LayoutParams(0, 52, 1f))
        commandRow.addView(run, LinearLayout.LayoutParams(70, 52))
        root.addView(commandRow)

        setContentView(root)

        input.setOnEditorActionListener { _, _, _ -> execute(); true }
        input.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> { historyMove(-1); true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { historyMove(1); true }
                KeyEvent.KEYCODE_TAB -> { insertAtCursor("\t"); true }
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
                textSize = if (label.length > 4) 10f else 11f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                setTextColor(white)
                gravity = Gravity.CENTER
                setPadding(2, 0, 2, 0)
                setOnClickListener { handleExtraKey(label) }
                setBackgroundColor(Color.TRANSPARENT)
            }
            row.addView(b, LinearLayout.LayoutParams(0, 33, 1f))
        }
        parent.addView(row, LinearLayout.LayoutParams(-1, 34))
    }

    private fun handleExtraKey(key: String) {
        when (key) {
            "ESC" -> insertAtCursor("\u001b")
            "TAB" -> insertAtCursor("\t")
            "CTRL" -> insertAtCursor("\u0003")
            "ALT" -> insertAtCursor("\u001b")
            "↑" -> historyMove(-1)
            "↓" -> historyMove(1)
            "←" -> moveCursor(-1)
            "→" -> moveCursor(1)
            "HOME" -> input.setSelection(0)
            "END" -> input.setSelection(input.text.length)
            "PGUP" -> scrollOutput(true)
            "PGDN" -> scrollOutput(false)
            "COPY" -> copyInput()
            "PASTE" -> pasteInput()
            else -> insertAtCursor(key)
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
        historyIndex = (historyIndex + delta).coerceIn(0, history.lastIndex + 1)
        input.setText(if (historyIndex <= history.lastIndex) history[historyIndex] else "")
        input.setSelection(input.text.length)
    }

    private fun copyInput() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("ZYVORA Terminal", input.text.toString()))
    }

    private fun pasteInput() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        if (cm.hasPrimaryClip()) insertAtCursor(cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() ?: "")
    }

    private fun scrollOutput(up: Boolean) {
        val parent = output.parent as? ScrollView ?: return
        if (up) parent.fullScroll(View.FOCUS_UP) else parent.fullScroll(View.FOCUS_DOWN)
    }

    private fun execute(): Boolean {
        val command = input.text.toString().trim()
        if (command.isEmpty()) return false
        history.add(command)
        historyIndex = history.size
        printLine("zyvora$ $command")
        val result = shell.execute(command)
        if (result == "\u000C") output.text = "" else if (result.isNotEmpty()) printLine(result)
        updatePrompt()
        input.text.clear()
        input.requestFocus()
        showKeyboard()
        return true
    }

    private fun updatePrompt() {
        prompt.text = "~/home $ "
    }

    private fun printLine(text: String) {
        output.append(if (output.text.isEmpty()) text else "\n$text")
        output.post { (output.parent as? ScrollView)?.fullScroll(View.FOCUS_DOWN) }
    }

    private fun showKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
    }
}
