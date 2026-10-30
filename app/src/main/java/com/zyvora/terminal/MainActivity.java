package com.zyvora.terminal;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Scanner;

public class MainActivity extends Activity {
    private TextView terminal;
    private EditText input;
    private File home;
    private File cwd;
    private final ArrayList<String> history = new ArrayList<>();

    private static final int BG = Color.rgb(5, 5, 9);
    private static final int CYAN = Color.rgb(0, 240, 255);
    private static final int GREEN = Color.rgb(70, 255, 140);
    private static final int WHITE = Color.rgb(225, 235, 240);
    private static final int MUTED = Color.rgb(125, 145, 155);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        home = new File(getFilesDir(), "zyvora-home");
        if (!home.exists()) home.mkdirs();
        cwd = home;
        buildUi();
        printBanner();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(14, 10, 14, 10);

        TextView header = new TextView(this);
        header.setText("  ZYVORA TERMINAL  //  v1.0.0");
        header.setTextColor(CYAN);
        header.setTextSize(13);
        header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(Color.rgb(10, 16, 22));
        root.addView(header, new LinearLayout.LayoutParams(-1, 42));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        terminal = new TextView(this);
        terminal.setTextColor(WHITE);
        terminal.setTextSize(14);
        terminal.setTypeface(Typeface.MONOSPACE);
        terminal.setGravity(Gravity.TOP | Gravity.START);
        terminal.setPadding(2, 12, 2, 12);
        scroll.addView(terminal, new ScrollView.LayoutParams(-1, -1));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView prompt = new TextView(this);
        prompt.setText("$ ");
        prompt.setTextColor(GREEN);
        prompt.setTextSize(16);
        prompt.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        row.addView(prompt, new LinearLayout.LayoutParams(-2, 52));

        input = new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(WHITE);
        input.setHintTextColor(MUTED);
        input.setHint("type a command...");
        input.setTextSize(15);
        input.setTypeface(Typeface.MONOSPACE);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setBackgroundColor(Color.TRANSPARENT);
        row.addView(input, new LinearLayout.LayoutParams(0, 52, 1));
        root.addView(row, new LinearLayout.LayoutParams(-1, 52));

        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || (event != null && event.getKeyCode() == 66)) {
                runCommand(input.getText().toString());
                input.setText("");
                return true;
            }
            return false;
        });
        setContentView(root);
        input.requestFocus();
    }

    private void printBanner() {
        out("ZYVORA ULTRA TERMINAL\n" +
                "────────────────────────────────────────\n" +
                "Android terminal workspace • zero login • local-first\n" +
                "Type 'help' for commands.\n\n" + prompt());
    }

    private String prompt() {
        String path = cwd.equals(home) ? "~" : home.toPath().relativize(cwd.toPath()).toString();
        return "zyvora@android:" + (path.isEmpty() ? "~" : "~/" + path) + "$ ";
    }

    private void runCommand(String raw) {
        String command = raw.trim();
        if (command.isEmpty()) { out("\n" + prompt()); return; }
        history.add(command);
        out("\n" + prompt() + command + "\n" + execute(command) + "\n" + prompt());
    }

    private String execute(String raw) {
        String[] p = raw.split("\\s+", 2);
        String cmd = p[0].toLowerCase(Locale.ROOT);
        String arg = p.length > 1 ? p[1].trim() : "";
        try {
            switch (cmd) {
                case "help": return help();
                case "clear": terminal.setText(""); return "";
                case "echo": return arg;
                case "pwd": return cwd.getAbsolutePath();
                case "whoami": return "zyvora";
                case "date": return new SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.getDefault()).format(new Date());
                case "uname": return "Android ZYVORA-Terminal arm64";
                case "neofetch": return neofetch();
                case "history": return historyText();
                case "ls": return list(arg.isEmpty() ? cwd : resolve(arg));
                case "cd": return changeDir(arg);
                case "mkdir": return makeDir(arg);
                case "touch": return touch(arg);
                case "cat": return cat(arg);
                case "rm": return remove(arg);
                case "rmdir": return remove(arg);
                case "clearhistory": history.clear(); return "history cleared";
                case "about": return "ZYVORA Terminal v1.0.0\nA Termux-style local terminal UI for Android.\nNo account required.\n";
                case "exit": return "Session stays available. Use Android back to close the app.";
                default: return "command not found: " + cmd + "\nType 'help' to see available commands.";
            }
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }

    private String help() {
        return "ZYVORA TERMINAL COMMANDS\n" +
                "  help              show this menu\n" +
                "  clear             clear terminal\n" +
                "  echo <text>       print text\n" +
                "  pwd               show current directory\n" +
                "  ls [path]         list files\n" +
                "  cd <path>         change directory\n" +
                "  mkdir <name>      create directory\n" +
                "  touch <name>      create empty file\n" +
                "  cat <file>        read a text file\n" +
                "  rm <name>         remove a file/empty directory\n" +
                "  date              show device date\n" +
                "  whoami            show current user\n" +
                "  uname             show platform\n" +
                "  neofetch          show ZYVORA system card\n" +
                "  history           show command history\n" +
                "  clearhistory      clear command history\n" +
                "  about             app information\n";
    }

    private String neofetch() {
        return "      ZZZZZ     ZYVORA ULTRA\n" +
                "     Z     Z    ────────────\n" +
                "       ZZZ      OS: Android\n" +
                "      Z         Shell: ZYVORA\n" +
                "     ZZZZZ      Home: " + home.getAbsolutePath() + "\n" +
                "\nLocal-first • No login • v1.0.0";
    }

    private String historyText() {
        if (history.isEmpty()) return "(empty)";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < history.size(); i++) b.append(i + 1).append("  ").append(history.get(i)).append('\n');
        return b.toString().trim();
    }

    private File resolve(String path) {
        if (path.equals("~")) return home;
        File f = path.startsWith("/") ? new File(path) : new File(cwd, path);
        try { return f.getCanonicalFile(); } catch (IOException e) { return f; }
    }

    private boolean insideHome(File f) throws IOException {
        String h = home.getCanonicalPath();
        String x = f.getCanonicalPath();
        return x.equals(h) || x.startsWith(h + File.separator);
    }

    private String list(File dir) throws IOException {
        if (!insideHome(dir)) return "permission denied: outside ZYVORA home";
        if (!dir.exists()) return "ls: no such file or directory";
        if (!dir.isDirectory()) return dir.getName();
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) return "(empty)";
        StringBuilder b = new StringBuilder();
        for (File f : files) b.append(f.isDirectory() ? "[DIR] " : "      ").append(f.getName()).append('\n');
        return b.toString().trim();
    }

    private String changeDir(String arg) throws IOException {
        if (arg.isEmpty()) { cwd = home; return ""; }
        File f = resolve(arg);
        if (!insideHome(f)) return "permission denied";
        if (!f.exists() || !f.isDirectory()) return "cd: not a directory: " + arg;
        cwd = f.getCanonicalFile();
        return "";
    }

    private String makeDir(String arg) throws IOException {
        if (arg.isEmpty()) return "mkdir: missing operand";
        File f = resolve(arg);
        if (!insideHome(f)) return "permission denied";
        return f.mkdirs() ? "created: " + f.getName() : "mkdir: could not create directory";
    }

    private String touch(String arg) throws IOException {
        if (arg.isEmpty()) return "touch: missing file operand";
        File f = resolve(arg);
        if (!insideHome(f)) return "permission denied";
        return f.exists() || f.createNewFile() ? "" : "touch: failed";
    }

    private String cat(String arg) throws IOException {
        if (arg.isEmpty()) return "cat: missing file operand";
        File f = resolve(arg);
        if (!insideHome(f)) return "permission denied";
        if (!f.exists() || !f.isFile()) return "cat: no such file: " + arg;
        StringBuilder b = new StringBuilder();
        try (Scanner s = new Scanner(f)) { while (s.hasNextLine()) b.append(s.nextLine()).append('\n'); }
        return b.length() == 0 ? "" : b.substring(0, b.length() - 1);
    }

    private String remove(String arg) throws IOException {
        if (arg.isEmpty()) return "rm: missing operand";
        File f = resolve(arg);
        if (!insideHome(f)) return "permission denied";
        if (!f.exists()) return "rm: no such file: " + arg;
        if (f.isDirectory() && f.listFiles() != null && f.listFiles().length > 0) return "rm: directory not empty";
        return f.delete() ? "" : "rm: failed to remove " + arg;
    }

    private void out(String s) {
        terminal.append(s);
        terminal.post(() -> {
            View parent = (View) terminal.getParent();
            if (parent instanceof ScrollView) ((ScrollView) parent).fullScroll(View.FOCUS_DOWN);
        });
    }
}
