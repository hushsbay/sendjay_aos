package com.hushsbay.sendjay_aos

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.os.Bundle
import android.webkit.*
import com.hushsbay.sendjay_aos.common.*
import com.hushsbay.sendjay_aos.databinding.ActivityLocalhtmlBinding
//import kotlinx.android.synthetic.main.activity_localhtml.*
//import kotlinx.android.synthetic.main.activity_main.*
//import kotlinx.android.synthetic.main.activity_popup.*
import org.apache.log4j.Logger
import java.io.InputStream

class zvoid_LocalHtmlActivity : Activity() {

    private lateinit var curContext: Activity
    private lateinit var logger: Logger
    private lateinit var connManager: ConnectivityManager

    private lateinit var binding: ActivityLocalhtmlBinding

    //private lateinit var uInfo: UserInfo
    //private var disposableMsg: Disposable? = null
    //private var disposableMain: Disposable? = null

    //private lateinit var authJson: JsonObject //Gson

    //var gOrigin = ""
    //var gObjStr = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLocalhtmlBinding.inflate(layoutInflater)
        setContentView(binding.root) //setContentView(R.layout.activity_localhtml)
        logger = LogHelper.getLogger(applicationContext, this::class.simpleName)
        WebView.setWebContentsDebuggingEnabled(true)
        curContext = this@zvoid_LocalHtmlActivity
        connManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        //uInfo = UserInfo(curContext)
        //1) origin = "/popup?type=image&msgid=" + msgid + "&body=" + body, objStr = "" : from jay_chat.js
        //gOrigin = intent.getStringExtra("origin")!!
        //gObjStr = intent.getStringExtra("objStr")!!
        //var popup_version = KeyChain.get(curContext, Const.KC_WEBVIEW_POPUP_VERSION) ?: ""
        //if (popup_version.startsWith("clear_cache")) {
        //    wvPopup.clearCache(true)
        //    wvPopup.clearHistory()
        //    popup_version = popup_version.replace("clear_cache", "")
        //    KeyChain.set(curContext, Const.KC_WEBVIEW_POPUP_VERSION, popup_version)
        //}
        //btnRetry.setOnClickListener {
        //    procAutoLogin() { setupWebViewPopup(gOrigin) }
        //}
        //btnSave.setOnClickListener {
        //    Util.loadUrl(wvPopup, "save")
        //}
        //procAutoLogin() { setupWebViewPopup(gOrigin) }
        setupWebViewLocalHtml()
    }

    //override fun onDestroy() {
    //    super.onDestroy()
    //    disposableMsg?.dispose()
    //    disposableMain?.dispose()
    //}

//    private fun procAutoLogin(callback: () -> Unit = {}) {
//        val logTitle = object{}.javaClass.enclosingMethod?.name!!
//        CoroutineScope(Dispatchers.Main).launch {
//            try {
//                val autoLogin = KeyChain.get(curContext, Const.KC_AUTOLOGIN) ?: ""
//                if (autoLogin == "Y") {
//                    authJson = HttpFuel.get(curContext, "${Const.DIR_ROUTE}/login/verify").await()
//                    if (authJson.get("code").asString == Const.RESULT_OK) {
//                        uInfo = UserInfo(curContext, authJson)
//                        callback()
//                    } else {
//                        Util.alert(curContext, "Login Error : ${authJson.get("msg").asString}", logTitle)
//                    }
//                } else {
//                    Util.alert(curContext, "Login needed", logTitle)
//                }
//            } catch (e: Exception) {
//                logger.error("$logTitle: ${e.toString()}")
//                Util.procException(curContext, e, logTitle)
//            }
//        }
//    }

//    private fun toggleDispRetry(show: Boolean) {
//        if (show) {
//            wvPopup.visibility = View.GONE
//            btnRetry.visibility = View.VISIBLE
//            txtRmks.visibility = View.VISIBLE
//            txtUrl.visibility = View.VISIBLE
//        } else {
//            wvPopup.visibility = View.VISIBLE
//            btnRetry.visibility = View.GONE
//            txtRmks.visibility = View.GONE
//            txtUrl.visibility = View.GONE
//        }
//    }

    private fun setupWebViewLocalHtml() { //(urlStr: String?=null) {
        Util.setupWebView(curContext, connManager, binding.wvLocalHtml) //Util.setupWebView(curContext, connManager, wvLocalHtml)
        //toggleDispRetry(false)
        //wvPopup.settings.supportZoom() //meta name="viewport" content=~ setting needed
        //wvPopup.settings.builtInZoomControls = true
//        binding.wvLocalHtml.addJavascriptInterface(WebInterfacePopup(), "AndroidLocalHtml") //wvLocalHtml.addJavascriptInterface(WebInterfacePopup(), "AndroidLocalHtml")
        //if (urlStr == null) return
        //wvLocalHtml.loadUrl(Const.URL_PUBLIC + urlStr)
        val fin: InputStream = assets.open("local.html")
        val buffer = ByteArray(fin.available())
        fin.read(buffer)
        fin.close()
        binding.wvLocalHtml.loadData(String(buffer), "text/html", "UTF-8")
    }

//    inner class WebInterfacePopup {
//
//        @JavascriptInterface
//        fun close() { //from ~.html => 메소드 한개라도 없으면 binding.wvLocalHtml.addJavascriptInterface()에서 컴파일 오류나므로 붙임
//            CoroutineScope(Dispatchers.Main).launch {
//                setResult(RESULT_CANCELED)
//                finish()
//            }
//        }
//
//    }

}