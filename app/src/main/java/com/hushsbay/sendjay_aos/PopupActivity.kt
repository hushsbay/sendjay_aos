package com.hushsbay.sendjay_aos

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.view.*
import android.webkit.*
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.google.gson.JsonObject
import com.hushsbay.sendjay_aos.common.Const
import com.hushsbay.sendjay_aos.common.HttpFuel
import com.hushsbay.sendjay_aos.common.KeyChain
import com.hushsbay.sendjay_aos.common.LogHelper
import com.hushsbay.sendjay_aos.common.RxToDown
import com.hushsbay.sendjay_aos.common.UserInfo
import com.hushsbay.sendjay_aos.common.Util
import com.hushsbay.sendjay_aos.data.RxEvent
import com.hushsbay.sendjay_aos.databinding.ActivityPopupBinding
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.log4j.Logger
import org.json.JSONObject

class PopupActivity : Activity() {

    private lateinit var curContext: Activity
    private lateinit var logger: Logger
    private lateinit var connManager: ConnectivityManager
    private lateinit var uInfo: UserInfo
    private var disposableMsg: Disposable? = null
    private var disposableMain: Disposable? = null

    private lateinit var binding: ActivityPopupBinding
    private var isOnCreate = true
    private lateinit var authJson: JsonObject //Gson

    var gOrigin = ""
    var gObjStr = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            curContext = this@PopupActivity
            binding = ActivityPopupBinding.inflate(layoutInflater)
            setContentView(binding.root) //setContentView(R.layout.activity_popup)
            logger = LogHelper.getLogger(applicationContext, this::class.simpleName)
            WebView.setWebContentsDebuggingEnabled(true)
            connManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            isOnCreate = true
            gOrigin = intent.getStringExtra("origin")!!
            gObjStr = intent.getStringExtra("objStr")!!
            var popup_version = KeyChain.get(curContext, Const.KC_WEBVIEW_POPUP_VERSION) ?: ""
            if (popup_version.startsWith("clear_cache")) {
                binding.wvPopup.clearCache(true)
                binding.wvPopup.clearHistory()
                popup_version = popup_version.replace("clear_cache", "")
                KeyChain.set(curContext, Const.KC_WEBVIEW_POPUP_VERSION, popup_version)
            }
            binding.btnRetry.setOnClickListener {
                if (!Util.chkIfNetworkAvailable(curContext, connManager, "toast")) return@setOnClickListener
                setupWebViewPopup(gOrigin)
            }
            binding.btnSave.setOnClickListener {
                Util.loadUrl(binding.wvPopup, "save")
            }
            disposableMsg?.dispose()
            disposableMsg = Util.procRxMsg(curContext)
            start()
        } catch (e: Exception) {
            logger.error("onCreate: ${e.toString()}")
            Util.procException(curContext, e, "onCreate")
        }
    }

    override fun onResume() { //onCreate -> onResume
        super.onResume()
        try {
            if (!isOnCreate) {
                CoroutineScope(Dispatchers.Main).launch {
                    procLogin() {
                        val obj = Util.getStrObjFromUserInfo(uInfo)
                        Util.loadUrl(binding.wvPopup, "resumeWebView", obj, authJson.toString()) //main_common.js 참조
                    }
                }
            } else {
                isOnCreate = false
            }
        } catch (e: Exception) {
            logger.error("onResume: ${e.toString()}")
            Util.procException(curContext, e, "onResume")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disposableMsg?.dispose()
        disposableMain?.dispose()
    }

    private fun start() {
        val logTitle = object{}.javaClass.enclosingMethod?.name!!
        CoroutineScope(Dispatchers.Main).launch {
            procLogin() {
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        setupWebViewPopup(gOrigin)
                    } catch (e: Exception) {
                        logger.error("$logTitle: ${e.toString()}")
                        Util.procException(curContext, e, logTitle)
                    }
                }
            }
        }
    }

    private suspend fun procLogin(callback: () -> Unit = {}) { //await() with Suspend function
        val logTitle = object{}.javaClass.enclosingMethod?.name!!
        try {
            val autoLogin = KeyChain.get(curContext, Const.KC_AUTOLOGIN) ?: ""
            if (autoLogin != "Y") {
                Util.alert(curContext, "자동로그인 상태가 아닙니다.", logTitle)
                return
            }
            val param = Util.setParamForAutoLogin(applicationContext)
            authJson = HttpFuel.post(curContext, "/auth/login", param.toString()).await()
            if (HttpFuel.isNetworkUnstableMsg(authJson)) {
                Util.toast(curContext, Const.NETWORK_UNSTABLE) //Util.alert(curContext, Const.NETWORK_UNSTABLE, logTitle)
                curContext.finish()
                return
            } else if (authJson.get("code").asString != Const.RESULT_OK) {
                Util.clearKeyChainForLogout(curContext)
                Util.toast(curContext, authJson.get("msg").asString) //Util.alert(curContext, authJson.get("msg").asString, logTitle)
                curContext.finish()
            } else if (authJson.get("code").asString == Const.RESULT_OK) {
                uInfo = UserInfo(curContext, authJson)
                callback()
            }
        } catch (e: Exception) {
            logger.error("$logTitle: ${e.toString()}")
            Util.procException(curContext, e, logTitle)
        }
    }

    private fun toggleDispRetry(show: Boolean) {
        if (show) {
            binding.wvPopup.visibility = View.GONE
            binding.btnRetry.visibility = View.VISIBLE
            binding.txtRmks.visibility = View.VISIBLE
            binding.txtUrl.visibility = View.VISIBLE
        } else {
            binding.wvPopup.visibility = View.VISIBLE
            binding.btnRetry.visibility = View.GONE
            binding.txtRmks.visibility = View.GONE
            binding.txtUrl.visibility = View.GONE
        }
    }

    private fun setupWebViewPopup(urlStr: String?=null) {
        Util.setupWebView(binding.wvPopup)
        toggleDispRetry(false)
        binding.wvPopup.settings.supportZoom() //meta name="viewport" content=~ setting needed
        binding.wvPopup.settings.builtInZoomControls = true
        binding.wvPopup.addJavascriptInterface(WebInterfacePopup(), "AndroidPopup")
        if (urlStr == null) return
        binding.wvPopup.webChromeClient = object: WebChromeClient() { //onConsoleMessage는 사용하지 않음 (alert가 뜨는데 모두 예상해서 커버하기 쉽지 않음
            //Belows are settings for fullscreen video in webview.
            //android:configChanges="keyboardHidden|orientation|screenSize" needed in AndroidManifest.xml
            //https://kutar37.tistory.com/entry/Android-webview%EC%97%90%EC%84%9C-HTML-video-%EC%A0%84%EC%B2%B4%ED%99%94%EB%A9%B4-%EC%9E%AC%EC%83%9D
            private var mCustomView: View? = null
            private var mCustomViewCallback: CustomViewCallback? = null
            private var mOriginalOrientation = 0
            private var mFullscreenContainer: FrameLayout? = null
            private val COVER_SCREEN_PARAMS = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            override fun onShowCustomView(view: View?, callback: CustomViewCallback) {
                if (mCustomView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                mOriginalOrientation = curContext.requestedOrientation
                val decor = curContext.window.decorView as FrameLayout
                mFullscreenContainer = FullscreenHolder(curContext)
                (mFullscreenContainer as FullscreenHolder).addView(view, COVER_SCREEN_PARAMS)
                decor.addView(mFullscreenContainer, COVER_SCREEN_PARAMS)
                mCustomView = view
                setFullscreen(true)
                mCustomViewCallback = callback
                super.onShowCustomView(view, callback)
            }
            override fun onShowCustomView(view: View?, requestedOrientation: Int, callback: CustomViewCallback) {
                this.onShowCustomView(view, callback)
            }
            override fun onHideCustomView() {
                if (mCustomView == null) return
                setFullscreen(false)
                val decor = curContext.window.decorView as FrameLayout
                decor.removeView(mFullscreenContainer)
                mFullscreenContainer = null
                mCustomView = null
                mCustomViewCallback!!.onCustomViewHidden()
                curContext.requestedOrientation = mOriginalOrientation
            }
            private fun setFullscreen(enabled: Boolean) {
                val win: Window = curContext.window
                val winParams: WindowManager.LayoutParams = win.getAttributes()
                val bits = WindowManager.LayoutParams.FLAG_FULLSCREEN
                if (enabled) {
                    winParams.flags = winParams.flags or bits
                } else {
                    winParams.flags = winParams.flags and bits.inv()
                    mCustomView?.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE)
                }
                win.attributes = winParams
            }
            inner class FullscreenHolder(ctx: Context) : FrameLayout(ctx) {
                override fun onTouchEvent(evt: MotionEvent): Boolean {
                    return true
                }
                init {
                    setBackgroundColor(ContextCompat.getColor(ctx, android.R.color.black))
                }
            }
        }
        binding.wvPopup.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val urlStr = request!!.url.toString() //if (!urlStr.contains(Const.PAGE_MAIN)) return false //ignore dummy page
                view!!.loadUrl(urlStr)
                return true //return super.shouldOverrideUrlLoading(view, request)
            }
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error) //https://gist.github.com/seongchan/752db643377f823950648d0bc80599c1
                val urlStr = request?.url?.toString() ?: "" //request?.url.toString() //Multiple request shown like jquery module.
                if (error != null && error.description != "" && urlStr.contains(Const.URL_JAY) && urlStr.contains(Const.PAGE_MAIN)) {
                    val errMsg = "${error.errorCode}/${error.description}"
                    Util.log("webview error", urlStr+"===="+errMsg)
                    toggleDispRetry(true)
                } else { //ajax : ex) -2/net::ERR_INTERNET_DISCONNECTED : Network not available
                    //Util.toast(curContext, "wvMain/${error?.errorCode}/${error?.description}/${urlStr}") //Multiple toast shown because of Multiple request
                }
            }
        }
        Util.setDownloadListener(curContext, binding.wvPopup)
        val urlStr1 = if (urlStr.startsWith(Const.DIR_PUBLIC)) urlStr.substring(Const.DIR_PUBLIC.length) else urlStr
        binding.wvPopup.loadUrl(Const.URL_PUBLIC + urlStr1)
    }

    inner class WebInterfacePopup {

        @JavascriptInterface
        fun procAfterOpenPopup() {
            val logTitle = object{}.javaClass.enclosingMethod?.name!!
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    disposableMain?.dispose()
                    disposableMain = RxToDown.subscribe<RxEvent>().subscribe { //disposable = RxBus.subscribe<RxEvent>().observeOn(AndroidSchedulers.mainThread()) //to receive the event on main thread
                        CoroutineScope(Dispatchers.Main).launch {
                            var param: JSONObject?= null
                            try {
                                param = it.data as JSONObject
                                Util.loadUrlJson(binding.wvPopup, "getFromWebViewSocket", param)
                            } catch (e: Exception) {
                                val msg = "${e.toString()}\n${it.ev}\n${param.toString()}"
                                logger.error("$logTitle: $msg")
                                Util.procExceptionStr(curContext, msg, logTitle)
                            }
                        }
                    }
                    val obj = Util.getStrObjFromUserInfo(uInfo)
                    val objStr = """{
                        'added' : '${Util.decodeUrl(gObjStr)}'
                    }"""
                    Util.loadUrl(binding.wvPopup, "startFromWebView", obj, objStr)
                } catch (e1: Exception) {
                    logger.error("$logTitle: ${e1.toString()}")
                    Util.procException(curContext, e1, logTitle)
                }
            }
        }

        @JavascriptInterface
        fun close() { //from index.html (for inviting)
            CoroutineScope(Dispatchers.Main).launch {
                setResult(RESULT_CANCELED)
                finish()
            }
        }

        @JavascriptInterface
        fun invite(useridArr: String, usernmArr: String) {
            val logTitle = object{}.javaClass.enclosingMethod?.name!!
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val rIntent = Intent()
                    rIntent.putExtra("userids", useridArr)
                    rIntent.putExtra("usernms", usernmArr)
                    setResult(RESULT_OK, rIntent)
                    finish()
                } catch (e: Exception) {
                    logger.error("$logTitle: ${e.toString()}")
                    Util.procException(curContext, e, logTitle)
                }
            }
        }

        @JavascriptInterface
        fun toggleSaveVisible(show: Boolean) { //from popup.html
            CoroutineScope(Dispatchers.Main).launch {
                if (show) {
                    binding.btnSave.visibility = View.VISIBLE
                } else {
                    binding.btnSave.visibility = View.GONE
                }
            }
        }

    }

}