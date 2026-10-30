package com.zyvora.terminal

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalShellTest {
    @Test fun helpListsCommands() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val result = TerminalShell(context).execute("help")
        assertTrue(result.contains("mkdir"))
        assertTrue(result.contains("neofetch"))
    }
}
