package com.zyvora.terminal

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {
    private lateinit var output: TextView
    private lateinit var input: EditText
    private lateinit var shell: TerminalShell
    private val history = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        shell = TerminalShell(this)
        buildUi()
        printLine("ZYVORA ULTRA TERMINAL v1.0")
        printLine("Type 'help' for commands.\n")
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(5, 7, 12))
            setPadding(18, 18, 18, 12)
        }
        val title = TextView(this).apply {
            text = "ZYVORA ULTRA  •  TERMINAL"
            textSize = 16f
            setTextColor(Color.rgb(0, 255, 170))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 14)
        }
        output = TextView(this).apply {
            textSize = 14f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(Color.rgb(210, 255, 235))
            setBackgroundColor(Color.rgb(8, 12, 18))
            setPadding(14, 14, 14, 14)
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this).apply {
            addView(output, ViewGroup.LayoutParams(-1, -2))
        }
        root.addView(title, LinearLayout.LayoutParams(-1, 52))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val prompt = TextView(this).apply {
            text = "zyvora$ "
            textSize = 14f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(Color.rgb(0, 255, 170))
        }
        input = EditText(this).apply {
            textSize = 14f
            typeface = android.graphics.Typeface.MONOSPACE
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            hint = "command..."
            setBackgroundColor(Color.TRANSPARENT)
        }
        val run = Button(this).apply {
            text = "RUN"
            setOnClickListener { execute() }
        }
        row.addView(prompt)
        row.addView(input, LinearLayout.LayoutParams(0, 58, 1f))
        row.addView(run, LinearLayout.LayoutParams(92, 58))
        root.addView(row)
        setContentView(root)
        input.setOnEditorActionListener { _, _, _ -> execute(); true }
    }

    private fun execute(): Boolean {
        val command = input.text.toString().trim()
        if (command.isEmpty()) return false
        history.add(command)
        printLine("zyvora$ $command")
        val result = shell.execute(command)
        if (result == "\u000C") output.text = "" else if (result.isNotEmpty()) printLine(result)
        input.text.clear()
        return true
    }

    private fun printLine(text: String) {
        output.append(if (output.text.isEmpty()) text else "\n$text")
    }
}
