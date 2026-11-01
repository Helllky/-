package com.example.wave

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import android.provider.MediaStore
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.Toast
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

/* Скрипт, который выполняется до кода страницы: скачивание файлов, window.open, состояние плеера */
private const val SHIM = """
(function(){
var oc=HTMLAnchorElement.prototype.click;
HTMLAnchorElement.prototype.click=function(){
 var a=this;
 if(window.AndroidApp&&a.hasAttribute("download")&&/^blob:/.test(a.href)){
  var name=a.getAttribute("download")||"file";
  fetch(a.href).then(function(r){return r.blob()}).then(function(b){
   var id=String(Date.now())+"_"+Math.floor(Math.random()*1e6);
   AndroidApp.begin(id,name,b.type||"");
   var CH=786432,pos=0;
   (function next(){
    if(pos>=b.size){AndroidApp.end(id);return}
    var fr=new FileReader();
    fr.onload=function(){var s=String(fr.result);AndroidApp.chunk(id,s.slice(s.indexOf(",")+1));pos+=CH;next()};
    fr.onerror=function(){AndroidApp.fail("чтение файла")};
    fr.readAsDataURL(b.slice(pos,pos+CH));
   })();
  }).catch(function(e){try{AndroidApp.fail(String(e))}catch(x){}});
  return;
 }
 return oc.apply(this,arguments);
};
window.open=function(u){try{AndroidApp.openExternal(String(u))}catch(e){}return null};
function pl(f){return function(){try{AndroidApp.playing(f)}catch(e){}}}
document.addEventListener("playing",pl(true),true);
document.addEventListener("pause",pl(false),true);
document.addEventListener("ended",pl(false),true);
})();
"""

class MainActivity : Activity() {
    private lateinit var web: WebView
    private var fileCb: ValueCallback<Array<Uri>>? = null

    private class Session(val uri: Uri, val out: OutputStream, val name: String)
    private val sessions = ConcurrentHashMap<String, Session>()

    private fun toast(msg: String) { runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() } }

    private fun openExternal(url: String) {
        runOnUiThread {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            catch (e: Exception) { }
        }
    }

    private val chrome = object : WebChromeClient() {
        override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
            AlertDialog.Builder(this@MainActivity).setMessage(message)
                .setPositiveButton("OK") { _, _ -> result?.confirm() }
                .setOnCancelListener { result?.confirm() }.show()
            return true
        }

        override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
            AlertDialog.Builder(this@MainActivity).setMessage(message)
                .setPositiveButton("OK") { _, _ -> result?.confirm() }
                .setNegativeButton("Отмена") { _, _ -> result?.cancel() }
                .setOnCancelListener { result?.cancel() }.show()
            return true
        }

        override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
            val et = EditText(this@MainActivity)
            et.setSingleLine()
            et.setText(defaultValue ?: "")
            AlertDialog.Builder(this@MainActivity).setMessage(message).setView(et)
                .setPositiveButton("OK") { _, _ -> result?.confirm(et.text.toString()) }
                .setNegativeButton("Отмена") { _, _ -> result?.cancel() }
                .setOnCancelListener { result?.cancel() }.show()
            return true
        }

        override fun onShowFileChooser(view: WebView?, cb: ValueCallback<Array<Uri>>?, params: WebChromeClient.FileChooserParams?): Boolean {
            fileCb?.onReceiveValue(null)
            fileCb = cb
            val i = Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
            return try {
                startActivityForResult(Intent.createChooser(i, "Файл плейлиста"), 77)
                true
            } catch (e: Exception) {
                fileCb = null
                cb?.onReceiveValue(null)
                false
            }
        }
    }

    inner class Bridge {
        @JavascriptInterface
        fun begin(id: String, name: String, mime: String) {
            try {
                val mt = when {
                    name.endsWith(".mp3", true) -> "audio/mpeg"
                    name.endsWith(".json", true) -> "application/json"
                    mime.isNotEmpty() -> mime
                    else -> "application/octet-stream"
                }
                val v = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, mt)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Волна")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)
                val out = if (uri != null) contentResolver.openOutputStream(uri) else null
                if (uri == null || out == null) { toast("Не удалось создать файл"); return }
                sessions[id] = Session(uri, out, name)
            } catch (e: Exception) {
                toast("Не удалось сохранить: " + (e.message ?: ""))
            }
        }

        @JavascriptInterface
        fun chunk(id: String, b64: String) {
            try { sessions[id]?.out?.write(Base64.decode(b64, Base64.DEFAULT)) } catch (e: Exception) { }
        }

        @JavascriptInterface
        fun end(id: String) {
            val s = sessions.remove(id) ?: return
            try {
                s.out.close()
                val v = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                contentResolver.update(s.uri, v, null, null)
                toast("Сохранено: Загрузки/Волна/" + s.name)
            } catch (e: Exception) {
                toast("Не удалось сохранить: " + (e.message ?: ""))
            }
        }

        @JavascriptInterface
        fun fail(msg: String) {
            toast("Скачивание не удалось: $msg")
        }

        @JavascriptInterface
        fun openExternal(url: String) { this@MainActivity.openExternal(url) }

        @JavascriptInterface
        fun playing(on: Boolean) { PlaybackService.inst?.locks(on) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bg = Color.parseColor("#0B110E")
        window.statusBarColor = bg
        window.navigationBarColor = bg

        web = WebView(this)
        web.setBackgroundColor(bg)
        WebView.setWebContentsDebuggingEnabled(true)
        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        s.allowFileAccess = false
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.addJavascriptInterface(Bridge(), "AndroidApp")
        web.webChromeClient = chrome
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val u = request?.url ?: return false
                val sch = u.scheme ?: return false
                if ((sch == "http" || sch == "https") && u.host != "wave.local") {
                    openExternal(u.toString())
                    return true
                }
                return false
            }
        }
        setContentView(web)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        try { startForegroundService(Intent(this, PlaybackService::class.java)) } catch (e: Exception) { }

        val html = assets.open("index.html").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val page = html.replaceFirst("<head>", "<head><script>" + SHIM + "</script>")
        web.loadDataWithBaseURL("https://wave.local/", page, "text/html", "utf-8", null)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // закрыть открытое окно страницы, иначе свернуть приложение (музыка продолжает играть)
        web.evaluateJavascript(
            "(function(){var d=document.querySelector('dialog[open]');if(d){d.close();return 1}return 0})()"
        ) { r -> if (r != "1") moveTaskToBack(true) }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 77) {
            val r = if (resultCode == RESULT_OK && data != null)
                WebChromeClient.FileChooserParams.parseResult(resultCode, data) else null
            fileCb?.onReceiveValue(r)
            fileCb = null
        }
    }

    override fun onDestroy() {
        stopService(Intent(this, PlaybackService::class.java))
        web.destroy()
        super.onDestroy()
    }
}

/* Держит процесс живым, пока играет музыка при выключенном экране */
class PlaybackService : Service() {
    companion object { @Volatile var inst: PlaybackService? = null }

    private var wl: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        inst = this
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("wave", "Воспроизведение", NotificationManager.IMPORTANCE_LOW))
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, "wave")
            .setContentTitle("Волна")
            .setContentText("Музыка играет в фоне")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    fun locks(on: Boolean) {
        try {
            if (on) {
                if (wl == null) {
                    wl = getSystemService(PowerManager::class.java)
                        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "wave:play").apply { setReferenceCounted(false) }
                }
                if (wifi == null) {
                    wifi = (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                        .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "wave:wifi").apply { setReferenceCounted(false) }
                }
                wl?.acquire(6 * 60 * 60 * 1000L)
                wifi?.acquire()
            } else {
                wl?.let { if (it.isHeld) it.release() }
                wifi?.let { if (it.isHeld) it.release() }
            }
        } catch (e: Exception) { }
    }

    override fun onDestroy() {
        locks(false)
        inst = null
        super.onDestroy()
    }
}
