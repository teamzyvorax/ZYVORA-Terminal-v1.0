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
import java.io.FileOutputStream

/** ZYVORA terminal: Termux-inspired architecture with an original UI and a real PTY. */
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
    private var linuxReady = false

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
        prepareLinuxRuntime()
        createSession("main")
        showKeyboard()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(10,3,6,3); setBackgroundColor(panel) }
        val brand = TextView(this).apply { text = "ZYVORA ULTRA\nAdvanced Android Terminal"; setTextColor(green); textSize = 12f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
        header.addView(brand, LinearLayout.LayoutParams(0,52,1f))
        header.addView(toolButton("+","New session") { createSession("session-${sessions.size+1}") })
        header.addView(toolButton("S","Sessions") { showSessions() })
        header.addView(toolButton("F","Files") { showFileManager(homeDir()) })
        header.addView(toolButton("⋮","Tools") { showTools() })
        root.addView(header, LinearLayout.LayoutParams(-1,58))
        sessionBar = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(5,2,5,2); setBackgroundColor(Color.rgb(4,7,9)) }
        root.addView(sessionBar, LinearLayout.LayoutParams(-1,38))
        terminalView = TerminalView(this,null).apply {
            setBackgroundColor(Color.BLACK); setTextSize(14); setTypeface(Typeface.MONOSPACE); setTerminalViewClient(this@MainActivity); isFocusableInTouchMode=true
            setOnFocusChangeListener { _, hasFocus -> if (hasFocus) showKeyboard() }
        }
        root.addView(terminalView, LinearLayout.LayoutParams(-1,0,1f))
        val keys = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(panel); setPadding(2,2,2,2) }
        addExtraRow(keys,listOf("ESC","TAB","CTRL","ALT","SHIFT","↑","↓","←","→","HOME"))
        addExtraRow(keys,listOf("END","PGUP","PGDN","^C","^D","^L","^Z","COPY","PASTE","/"))
        addExtraRow(keys,listOf("-","_","|","~","`","$","&",";",":","("))
        addExtraRow(keys,listOf(")","{","}","[","]","[]","\\","'","\"","."))
        root.addView(keys,LinearLayout.LayoutParams(-1,136))
        statusText = TextView(this).apply { textSize=10f; typeface=Typeface.MONOSPACE; setTextColor(muted); gravity=Gravity.CENTER_VERTICAL; setPadding(8,0,8,0); setBackgroundColor(Color.rgb(3,6,8)) }
        root.addView(statusText,LinearLayout.LayoutParams(-1,24))
        setContentView(root)
    }

    private fun toolButton(label:String, description:String, action:()->Unit)=TextView(this).apply { text=label; contentDescription=description; textSize=18f; typeface=Typeface.DEFAULT_BOLD; setTextColor(white); gravity=Gravity.CENTER; setPadding(9,0,9,0); setOnClickListener{action()} }

    private fun addExtraRow(parent:LinearLayout, labels:List<String>) {
        val row=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
        labels.forEach { label ->
            val b=TextView(this).apply { text=label; textSize=if(label.length>4)8f else 10f; typeface=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD); setTextColor(if((label=="CTRL"&&ctrl)||(label=="ALT"&&alt)||(label=="SHIFT"&&shift))green else white); gravity=Gravity.CENTER; setPadding(1,0,1,0); setOnClickListener{handleExtraKey(label)} }
            row.addView(b,LinearLayout.LayoutParams(0,34,1f))
        }
        parent.addView(row,LinearLayout.LayoutParams(-1,34))
    }

    private fun handleExtraKey(key:String){
        val s=current?.session?:return
        when(key){
            "ESC"->writeBytes(s,byteArrayOf(27)); "TAB"->writeBytes(s,byteArrayOf(9)); "CTRL"->ctrl=!ctrl; "ALT"->alt=!alt; "SHIFT"->shift=!shift
            "↑"->writeText(s,"\u001b[A"); "↓"->writeText(s,"\u001b[B"); "→"->writeText(s,"\u001b[C"); "←"->writeText(s,"\u001b[D"); "HOME"->writeText(s,"\u001b[H"); "END"->writeText(s,"\u001b[F")
            "PGUP"->writeText(s,"\u001b[5~"); "PGDN"->writeText(s,"\u001b[6~"); "^C"->writeBytes(s,byteArrayOf(3)); "^D"->writeBytes(s,byteArrayOf(4)); "^L"->writeBytes(s,byteArrayOf(12)); "^Z"->writeBytes(s,byteArrayOf(26)); "PASTE"->pasteClipboard(s)
            "COPY"->Toast.makeText(this,"Select terminal text to copy",Toast.LENGTH_SHORT).show(); else->writeText(s,key)
        }
        terminalView.requestFocus(); showKeyboard(); updateStatus()
    }
    private fun writeText(s:TerminalSession,t:String)=writeBytes(s,t.toByteArray(Charsets.UTF_8))
    private fun writeBytes(s:TerminalSession,b:ByteArray)=s.write(b,0,b.size)

    private fun prepareLinuxRuntime(){
        try{
            val root=File(filesDir,"linux/rootfs")
            val marker=File(root,".zyvora-ready")
            if(marker.exists() && File(filesDir,"linux/proot-arm64").canExecute()){ linuxReady=true; return }
            Toast.makeText(this,"Preparing ZYVORA Linux runtime…",Toast.LENGTH_LONG).show()
            root.parentFile?.mkdirs(); root.mkdirs()
            val archive=File(filesDir,"linux/rootfs.tar.gz")
            assets.open("userspace/zyvora-alpine-aarch64.tar.gz").use{input->FileOutputStream(archive).use{output->input.copyTo(output)}}
            val proot=File(filesDir,"linux/proot-arm64")
            assets.open("runtime/proot-arm64").use{input->FileOutputStream(proot).use{output->input.copyTo(output)}}
            proot.setExecutable(true,false)
            val p=ProcessBuilder("/system/bin/toybox","tar","-xzf",archive.absolutePath,"-C",root.absolutePath).redirectErrorStream(true).start()
            val output=p.inputStream.bufferedReader().use{it.readText()}; val code=p.waitFor()
            if(code!=0) throw IllegalStateException("rootfs extraction failed: $output")
            marker.writeText("ZYVORA Linux userspace ready")
            linuxReady=true
            archive.delete()
            Toast.makeText(this,"ZYVORA Linux userspace ready",Toast.LENGTH_SHORT).show()
        }catch(e:Exception){
            linuxReady=false
            Toast.makeText(this,"Linux runtime unavailable: ${e.message}",Toast.LENGTH_LONG).show()
        }
    }

    private fun createSession(name:String){
        val home=homeDir()
        val command:String
        val args:Array<String>
        val env:Array<String>
        if(linuxReady){
            val root=File(filesDir,"linux/rootfs")
            val proot=File(filesDir,"linux/proot-arm64")
            command=proot.absolutePath
            val a=mutableListOf("-0","-r",root.absolutePath,"-w","/root")
            if(File("/sdcard").exists()) a.addAll(listOf("-b","/sdcard:/sdcard"))
            a.addAll(listOf("/bin/bash","-l"))
            args=a.toTypedArray()
            env=arrayOf("HOME=/root","PWD=/root","PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin","SHELL=/bin/bash","TERM=xterm-256color","LANG=C.UTF-8","TMPDIR=/tmp","ZYVORA_TERMINAL=1","ZYVORA_LINUX=alpine")
        }else{
            command="/system/bin/sh"; args=arrayOf("-i"); env=arrayOf("HOME=${home.absolutePath}","PWD=${home.absolutePath}","PATH=/system/bin:/system/xbin:/vendor/bin","SHELL=/system/bin/sh","TERM=xterm-256color","LANG=C.UTF-8","TMPDIR=${cacheDir.absolutePath}","ZYVORA_TERMINAL=1")
        }
        val session=TerminalSession(command,if(linuxReady) home.absolutePath else home.absolutePath,args,env,4000,this)
        val item=SessionItem(session,name); sessions.add(item); switchSession(item)
    }

    private fun switchSession(item:SessionItem){ current=item; terminalView.attachSession(item.session); updateSessionBar(); updateStatus(); terminalView.requestFocus(); showKeyboard() }
    private fun updateSessionBar(){
        sessionBar.removeAllViews(); sessions.forEachIndexed{index,item->
            val tab=TextView(this).apply{text=" ${index+1}:${item.name} ";textSize=10f;typeface=Typeface.MONOSPACE;setTextColor(if(item==current)green else muted);gravity=Gravity.CENTER;setPadding(7,0,7,0);setOnClickListener{switchSession(item)};setOnLongClickListener{renameSession(item);true}}
            sessionBar.addView(tab,LinearLayout.LayoutParams(0,34,1f))
        }
    }
    private fun showSessions(){AlertDialog.Builder(this).setTitle("ZYVORA Sessions").setItems(sessions.map{it.name}.toTypedArray()){_,w->switchSession(sessions[w])}.setPositiveButton("New"){_,_->createSession("session-${sessions.size+1}")}.setNegativeButton("Close Current"){_,_->closeCurrentSession()}.show()}
    private fun renameSession(item:SessionItem){val e=EditText(this).apply{setText(item.name);selectAll()};AlertDialog.Builder(this).setTitle("Rename session").setView(e).setPositiveButton("Save"){_,_->e.text.toString().trim().takeIf{it.isNotEmpty()}?.let{item.name=it};updateSessionBar()}.setNegativeButton("Cancel",null).show()}
    private fun closeCurrentSession(){val i=current?:return;i.session.finishIfRunning();sessions.remove(i);if(sessions.isEmpty())createSession("main")else switchSession(sessions.last())}

    private fun showTools(){
        val items=arrayOf("Command Browser","Setup Storage","SSH Profiles","Runtime Status","App Files","About")
        AlertDialog.Builder(this).setTitle("ZYVORA ULTRA Tools").setItems(items){_,w->when(w){0->showCommandBrowser();1->setupStorage();2->showSshProfiles();3->showRuntimeStatus();4->showFileManager(homeDir());5->showAbout()}}.show()
    }
    private fun showCommandBrowser(){AlertDialog.Builder(this).setTitle("Command Browser").setMessage("LINUX\nls cd pwd mkdir cp mv rm cat grep find\n\nSHELL\necho printf clear env export which whoami uname ps kill\n\nNETWORK\nping curl wget ssh scp ss\n\nDEVELOPMENT\npython3 pip node npm git\n\nPACKAGE\npkg update\npkg install <package>\npkg search <package>\n\nReal PTY only. Missing commands are never faked.").setPositiveButton("Close",null).show()}
    private fun showRuntimeStatus(){
        val m="PTY: READY\nANSI/VT: READY\nSessions: ${sessions.size}\nLinux userspace: ${if(linuxReady)"READY (Alpine ARM64)" else "NOT READY"}\nPackage command: ${if(linuxReady)"pkg -> apk" else "unavailable"}\nShell: ${if(linuxReady)"/bin/bash via PRoot" else "/system/bin/sh"}"
        AlertDialog.Builder(this).setTitle("Runtime Status").setMessage(m).setPositiveButton("OK",null).show()
    }

    private fun setupStorage(){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply{addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)},REQUEST_STORAGE)}
    @Deprecated("Legacy result callback retained for minSdk compatibility")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);if(requestCode==REQUEST_STORAGE&&resultCode==RESULT_OK){data?.data?.let{uri->try{contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)}catch(_:Exception){};storageTree=uri;getPreferences(MODE_PRIVATE).edit().putString("storage_tree",uri.toString()).apply();Toast.makeText(this,"Storage access saved",Toast.LENGTH_SHORT).show()}}}

    private fun showFileManager(start:File){fileManagerCwd=start;renderFileManager()}
    private fun renderFileManager(){val d=fileManagerCwd?:homeDir();val es=d.listFiles()?.sortedWith(compareBy<File>{!it.isDirectory}.thenBy{it.name.lowercase()})?:emptyList();val labels=mutableListOf("../");es.forEach{labels.add(if(it.isDirectory)"[DIR] ${it.name}/" else "[FILE] ${it.name}")};AlertDialog.Builder(this).setTitle("Files • ${d.name.ifEmpty{"home"}}").setItems(labels.toTypedArray()){_,w->if(w==0)d.parentFile?.let{fileManagerCwd=it;renderFileManager()}else{val e=es[w-1];if(e.isDirectory){fileManagerCwd=e;renderFileManager()}else openEditor(e)}}.setPositiveButton("New"){_,_->showNewFileDialog(d)}.setNeutralButton("Storage"){_,_->setupStorage()}.setNegativeButton("Close",null).show()}
    private fun showNewFileDialog(d:File){val e=EditText(this).apply{hint="file or folder name"};AlertDialog.Builder(this).setTitle("Create").setView(e).setPositiveButton("File"){_,_->e.text.toString().trim().takeIf{it.isNotEmpty()}?.let{File(d,it).createNewFile()};renderFileManager()}.setNeutralButton("Folder"){_,_->e.text.toString().trim().takeIf{it.isNotEmpty()}?.let{File(d,it).mkdirs()};renderFileManager()}.setNegativeButton("Cancel",null).show()}
    private fun openEditor(f:File){if(f.length()>2_000_000){Toast.makeText(this,"File too large",Toast.LENGTH_SHORT).show();return};val e=EditText(this).apply{setText(try{f.readText()}catch(_:Exception){""});gravity=Gravity.TOP or Gravity.START;typeface=Typeface.MONOSPACE;inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;minLines=14};AlertDialog.Builder(this).setTitle("Editor • ${f.name}").setView(e).setPositiveButton("Save"){_,_->try{f.writeText(e.text.toString());Toast.makeText(this,"Saved",Toast.LENGTH_SHORT).show()}catch(x:Exception){Toast.makeText(this,"Save failed: ${x.message}",Toast.LENGTH_LONG).show()}}.setNegativeButton("Close",null).show()}

    private fun showSshProfiles(){val p=getPreferences(MODE_PRIVATE);val set=p.getStringSet("ssh_profiles",emptySet())?.toMutableSet()?:mutableSetOf();val values=(if(set.isEmpty())emptyList() else set.toList())+listOf("+ Add SSH profile");AlertDialog.Builder(this).setTitle("SSH Profiles").setItems(values.toTypedArray()){_,w->if(w==values.lastIndex)addSshProfile(set)else connectSsh(values[w])}.setNegativeButton("Close",null).show()}
    private fun addSshProfile(set:MutableSet<String>){val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(20,4,20,4)};val u=EditText(this).apply{hint="username"};val h=EditText(this).apply{hint="host"};val p=EditText(this).apply{hint="port (22)";inputType=InputType.TYPE_CLASS_NUMBER};box.addView(u);box.addView(h);box.addView(p);AlertDialog.Builder(this).setTitle("Add SSH profile").setView(box).setPositiveButton("Save"){_,_->val user=u.text.toString().trim();val host=h.text.toString().trim();val port=p.text.toString().trim().ifEmpty{"22"};if(user.isNotEmpty()&&host.isNotEmpty()){set.add("$user@$host:$port");getPreferences(MODE_PRIVATE).edit().putStringSet("ssh_profiles",set).apply()}}.setNegativeButton("Cancel",null).show()}
    private fun connectSsh(profile:String){val at=profile.indexOf('@');val c=profile.lastIndexOf(':');if(at>0&&c>at)writeText(current?.session?:return,"ssh -p ${profile.substring(c+1)} ${profile.substring(0,at)}@${profile.substring(at+1,c)}\n")}
    private fun pasteClipboard(s:TerminalSession){val c=getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager;if(c.hasPrimaryClip())writeText(s,c.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty())}
    private fun showAbout(){AlertDialog.Builder(this).setTitle("ZYVORA ULTRA Terminal").setMessage("Advanced Android Terminal\n\nReal PTY + ANSI terminal emulator\nEmbedded ARM64 Alpine Linux userspace\nPRoot runtime\nMultiple sessions\nFile manager + editor\nSSH profiles\nNo fake command output\nNo login • No signup\n\nTermux terminal-view is used as an external library under its applicable license.").setPositiveButton("OK",null).show()}

    private fun homeDir():File=File(filesDir,"home").apply{mkdirs()}
    private fun updateStatus(){statusText.text=" PTY • ${current?.name?:"-"} • ${if(linuxReady)"ZYVORA Linux/Alpine" else "/system/bin/sh"} • ${if(ctrl)"CTRL " else ""}${if(alt)"ALT" else ""}"}
    private fun showKeyboard(){terminalView.postDelayed{terminalView.requestFocus();(getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(terminalView,InputMethodManager.SHOW_IMPLICIT)},200}

    override fun onTextChanged(s:TerminalSession){if(s==current?.session)terminalView.postInvalidate()}
    override fun onTitleChanged(s:TerminalSession)=Unit
    override fun onSessionFinished(s:TerminalSession)=updateStatus()
    override fun onCopyTextToClipboard(s:TerminalSession,text:String){(getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("ZYVORA Terminal",text))}
    override fun onPasteTextFromClipboard(s:TerminalSession?){s?.let{pasteClipboard(it)}}
    override fun onBell(s:TerminalSession)=Unit
    override fun onColorsChanged(s:TerminalSession)=terminalView.postInvalidate()
    override fun onTerminalCursorStateChange(state:Boolean)=Unit
    override fun getTerminalCursorStyle():Int=0
    override fun logError(tag:String,message:String)=Unit
    override fun logWarn(tag:String,message:String)=Unit
    override fun logInfo(tag:String,message:String)=Unit
    override fun logDebug(tag:String,message:String)=Unit
    override fun logVerbose(tag:String,message:String)=Unit
    override fun logStackTraceWithMessage(tag:String,message:String,e:Exception)=Unit
    override fun logStackTrace(tag:String,e:Exception)=Unit
    override fun onScale(scale:Float):Float=scale
    override fun onSingleTapUp(e:MotionEvent){terminalView.requestFocus();showKeyboard()}
    override fun shouldBackButtonBeMappedToEscape():Boolean=true
    override fun shouldEnforceCharBasedInput():Boolean=true
    override fun shouldUseCtrlSpaceWorkaround():Boolean=true
    override fun isTerminalViewSelected():Boolean=true
    override fun copyModeChanged(copyMode:Boolean)=Unit
    override fun onKeyDown(keyCode:Int,e:KeyEvent,s:TerminalSession):Boolean=false
    override fun onKeyUp(keyCode:Int,e:KeyEvent):Boolean=false
    override fun onLongPress(event:MotionEvent):Boolean=false
    override fun readControlKey():Boolean=ctrl
    override fun readAltKey():Boolean=alt
    override fun readShiftKey():Boolean=shift
    override fun readFnKey():Boolean=fn
    override fun onCodePoint(codePoint:Int,ctrlDown:Boolean,s:TerminalSession):Boolean=false
    override fun onEmulatorSet()=Unit
    override fun onDestroy(){sessions.forEach{it.session.finishIfRunning()};sessions.clear();super.onDestroy()}
    companion object{private const val REQUEST_STORAGE=401}
}
