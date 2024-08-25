package com.hushsbay.sendjay_aos

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.Button
import android.widget.EditText
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import com.google.gson.JsonObject
import com.hushsbay.sendjay_aos.common.Const
import com.hushsbay.sendjay_aos.common.HttpFuel
import com.hushsbay.sendjay_aos.common.KeyChain
import com.hushsbay.sendjay_aos.common.KeyboardVisibilityChecker
import com.hushsbay.sendjay_aos.common.LogHelper
import com.hushsbay.sendjay_aos.common.NotiCenter
import com.hushsbay.sendjay_aos.common.RxToDown
import com.hushsbay.sendjay_aos.common.RxToRoom
import com.hushsbay.sendjay_aos.common.SocketIO
import com.hushsbay.sendjay_aos.common.UserInfo
import com.hushsbay.sendjay_aos.common.Util
import com.hushsbay.sendjay_aos.data.RxEvent
import com.hushsbay.sendjay_aos.databinding.ActivityMainBinding
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.*
import org.apache.log4j.Logger
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.net.URL

//socket.io는 json(org.json.JSONObect) 사용. Fuel은 gson(com.google.gson.JsonObject) 사용
//onCreate -> onStart -> onResume -> onPause -> onStop -> onDestroy

class MainActivity : Activity() {

    companion object { //See ChatService.kt
        var isOnTop = true
        var stopServiceByLogout = false
    }

    private lateinit var curContext: Activity
    private lateinit var logger: Logger
    private lateinit var pm: PowerManager
    private lateinit var connManager: ConnectivityManager
    private lateinit var uInfo: UserInfo
    private var disposableMsg: Disposable? = null
    private var disposableMain: Disposable? = null
    private var disposableRoom: Disposable? = null
    private lateinit var imm: InputMethodManager
    private lateinit var keyboardVisibilityChecker: KeyboardVisibilityChecker

    private lateinit var binding: ActivityMainBinding
    private lateinit var authJson: JsonObject //Gson

    private var filePathCallbackMain: ValueCallback<Array<Uri?>>? = null //webview file chooser
    private var filePathCallbackRoom: ValueCallback<Array<Uri?>>? = null //webview file chooser
    private val FILE_RESULT_MAIN = 100 //webview file chooser
    private val FILE_RESULT_ROOM = 101 //webview file chooser

    private val MEMBER_RESULT = 300
    private val INVITE_RESULT = 400

    private val RETRY_DELAY = 1000L //3000L
    private val RETRY_TIMEOUT = 10000L //30000L

    private var isFromNoti = false
    private var isOnCreate = true
    private var retried = false
    private var gType = "" //for wvRoom
    private var gRoomid = "" //for wvRoom
    private var gOrigin = "" //for wvRoom
    private var gObjStr = "" //for wvRoom
    private var roomidForChatService = "" //for wvRoom

    //권한 허용 : https://velog.io/@alsgus92/Android-Runtime-%EA%B6%8C%ED%95%9CPermission-%EC%9A%94%EC%B2%AD-Flow-%EB%B0%8F-Tutorial
    //런타임권한(protectionlevel = "dangerous")에 관한 것이며 일반권한이나 서명권한이 아닌 경우이며 사용자에게 권한부여 요청을 필요로 함
    //AndroidManifest.xml에 있는 uses-permission은 일반적인 것과 위험한 권한으로 나뉘는데 위험한 권한은 아래와 같이 checkPermission()이 필요함
    //그런데, 구글링하면 어던 것이 위험한 권한인지 구분은 되나 실제로는 녹녹치 않으므로 모든 권한을 요청해 버리면 수월함 (아래 권한은 Manifest에 있는 모든 권한을 요청하는 것임
    //막상 해보니, POST_NOTIFICATIONS만 빼고 모두 막아도 무방함 (권한##0)
    //SYSTEM_ALERT_WINDOW와 SCHEDULE_EXACT_ALARM : 여기서 해결되지 않아 onCreate()에서 권한#1/권한#2로 구현해 허용받음
    private val REQUEST_PERMISSION = 100
    private var permissions = listOf(
        //android.Manifest.permission.INTERNET, android.Manifest.permission.FOREGROUND_SERVICE,
        //android.Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC, android.Manifest.permission.ACCESS_NETWORK_STATE,
        //android.Manifest.permission.RECEIVE_BOOT_COMPLETED, android.Manifest.permission.VIBRATE,
        //android.Manifest.permission.SYSTEM_ALERT_WINDOW, android.Manifest.permission.SCHEDULE_EXACT_ALARM
        android.Manifest.permission.POST_NOTIFICATIONS //권한##0
    )

    private fun checkPermission(permissionList: List<String>) {
        val requestList = ArrayList<String>()
        for (permission in permissionList) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) requestList.add(permission)
        }
        if (requestList.isNotEmpty()) ActivityCompat.requestPermissions(this, requestList.toTypedArray(), REQUEST_PERMISSION)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSION) {
            val deniedPermission = ArrayList<String>()
            for ((index, result) in grantResults.withIndex()) {
                if (result == PackageManager.PERMISSION_DENIED) deniedPermission.add(permissions[index])
            }
            if (deniedPermission.isNotEmpty()) { //여기 스낵바를 넣어 인터액션하면 좋을 것인데 일단 넘어감
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivityForResult(intent, REQUEST_PERMISSION)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            curContext = this@MainActivity
            checkPermission(permissions)
            //권한#1 SYSTEM_ALERT_WINDOW : https://greensky0026.tistory.com/222#google_vignette 백그라운드서비스와도 관련됨
            if (!Settings.canDrawOverlays(this)) { //!hasPermission으로 보면 됨
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
            }
            //권한#2 SCHEDULE_EXACT_ALARM
            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) { //!hasPermission으로 보면 됨
                val appDetail = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
                appDetail.addCategory(Intent.CATEGORY_DEFAULT)
                appDetail.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(appDetail)
            }
            if (!packageManager.canRequestPackageInstalls()) {
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            }
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root) //setContentView(R.layout.activity_main)
            logger = LogHelper.getLogger(applicationContext, this::class.simpleName)
            binding.wvMain.setBackgroundColor(0) //make it transparent (if not, white background will be shown)
            WebView.setWebContentsDebuggingEnabled(true) //for debug
            NotiCenter(curContext, packageName) //NotiCenter.invoke() //ChatService.kt onCreate()에서도 실행하고 있으나 여기서도 여러 메소드 사용중이므로 중복 호출
            isOnCreate = true
            stopServiceByLogout = false
            roomidForChatService = ""
            KeyChain.set(curContext, Const.KC_ROOMID_FOR_CHATSERVICE, "")
            KeyChain.set(curContext, Const.KC_SCREEN_STATE, "on")
            pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            connManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            keyboardVisibilityChecker = KeyboardVisibilityChecker(window, onShowKeyboard = { keyboardHeight ->
                if (roomidForChatService != "") Util.loadUrl(binding.wvRoom, "scrollToBottomFromWebView") //키보드 올라 오면 웹뷰내 채팅창 맨 아래로 스크롤
            })
            keyboardVisibilityChecker = KeyboardVisibilityChecker(window, onHideKeyboard = { })
            binding.btnRetry.setOnClickListener {
                if (!Util.chkIfNetworkAvailable(curContext, connManager, "toast")) return@setOnClickListener
                if (ChatService.state != Const.ServiceState.RUNNING) {
                    start()
                } else {
                    CoroutineScope(Dispatchers.Main).launch {
                        procLogin(true) {
                            Util.connectSockWithCallback(curContext, connManager) {
                                if (it.get("code").asString != Const.RESULT_OK) {
                                    Util.toast(curContext, it.get("msg").asString)
                                    return@connectSockWithCallback
                                }
                                retried = true
                                if (roomidForChatService != "") { //When wvRoom shown
                                    setupWebViewRoom(true)
                                } else {
                                    setupWebViewMain()
                                }
                            }
                        }
                    }
                }
            }
            disposableMsg?.dispose()
            disposableMsg = Util.procRxMsg(curContext)
            start()
        } catch (e: Exception) {
            logger.error("onCreate: ${e.toString()}")
            Util.procException(curContext, e, "onCreate")
        }
    }

    override fun onNewIntent(intent: Intent?) { //onNewIntent (from Notification) -> onResume (no onCreate)
        super.onNewIntent(intent)
        try {
            Util.connectSockWithCallback(curContext, connManager) {
                if (it.get("code").asString != Const.RESULT_OK) {
                    Util.toast(curContext, it.get("msg").asString)
                    return@connectSockWithCallback
                }
                intent?.let {
                    //MainActivity가 스택에 있을 때는 클릭시 챗방이 뜨는데, 강제종료후 서비스만 실행될 때는 MainActivity가 스택에 없어서 onNewIntent 이벤트가 발생되지 않는데
                    //그걸 openRoomWithNotiIntent()로 공통모듈화해서 onCreate()에서도 호출하게 함
                    if (!openRoomWithNotiIntent(it)) return@connectSockWithCallback
                }
            }
        } catch (e: Exception) {
            logger.error("onNewIntent: ${e.toString()}")
            Util.procException(curContext, e, "onNewIntent")
        }
    }

    override fun onBackPressed() { //super.onBackPressed()
        if (roomidForChatService != "") { //When wvRoom shown
            procCloseRoom()
        } else {
            if (binding.wvRoom.visibility == View.VISIBLE) {
                procCloseRoom()
            } else {
                moveTaskToBack(true)
            }
        }
    }

    override fun onResume() { //onCreate -> onResume
        super.onResume()
        try {
            isOnTop = true
            if (roomidForChatService == "") cancelUnreadNoti()
            if (!isOnCreate) {
                CoroutineScope(Dispatchers.Main).launch {
                    if (!chkUpdate(false)) return@launch
                    procLogin(true) {
                        Util.connectSockWithCallback(curContext, connManager)
                        val obj = Util.getStrObjFromUserInfo(uInfo)
                        if (roomidForChatService != "") {
                            Util.loadUrl(binding.wvRoom, "resumeWebView", obj, authJson.toString()) //main_common.js 참조
                            updateAllUnreads(isFromNoti)
                            if (isFromNoti) isFromNoti = false
                            Util.loadUrl(binding.wvRoom, "setFocusFromWebView", isOnTop.toString())
                        } else { //위 아래 resumeWebView는 토큰을 웹뷰로 전달하기 위해 추가한 루틴임. (전달하지 않으면 웹뷰에서 refreshToken()이 주기적으로 돌아도 그전에 만기되버릴 것임)
                            Util.loadUrl(binding.wvMain, "resumeWebView", obj, authJson.toString()) //main_common.js 참조
                        }
                    }
                }
            } else { //앱이 최초 실행되는 것이므로 onResume()에서 챗방이 열려 있지 않음 (roomidForChatService = "")
                isOnCreate = false
            }
        } catch (e: Exception) {
            logger.error("onResume: ${e.toString()}")
            Util.procException(curContext, e, "onResume")
        }
    }

    override fun onPause() {
        super.onPause()
        isOnTop = false
        if (roomidForChatService != "") Util.loadUrl(binding.wvRoom, "setFocusFromWebView", isOnTop.toString())
    }

    override fun onDestroy() {
        super.onDestroy()
        roomidForChatService = ""
        KeyChain.set(curContext, Const.KC_ROOMID_FOR_CHATSERVICE, "")
        disposableMsg?.dispose()
        disposableMain?.dispose()
        disposableRoom?.dispose()
        keyboardVisibilityChecker.detachKeyboardListeners()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        //if (resultCode != RESULT_OK) return //Do not uncomment this line (eg : filePathCallbackMain?.onReceiveValue should be executed all the time)
        try {
            if (requestCode == FILE_RESULT_MAIN) { //webviewMain의 file chooser
                filePathCallbackMain?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
                filePathCallbackMain = null
            } else if (requestCode == FILE_RESULT_ROOM) { //webviewRoom의 file chooser
                if (data != null) {
                    var list: Array<Uri?>? = null
                    if (data.clipData != null) { //handle multiple-selected files
                        list = data.clipData?.itemCount?.let { arrayOfNulls(it) } //val list = mutableListOf<Uri>()
                        val numSelectedFiles = data.clipData!!.itemCount
                        for (i in 0 until numSelectedFiles) {
                            list?.set(i, data.clipData!!.getItemAt(i).uri)
                        }
                        filePathCallbackRoom?.onReceiveValue(list)
                    } else { //if (data.data != null) { //handle single-selected file
                        filePathCallbackRoom?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
                    }
                } else {
                    filePathCallbackRoom?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
                }
                filePathCallbackRoom = null
            } else if (requestCode == MEMBER_RESULT || requestCode == INVITE_RESULT) {
                data?.apply {
                    val userids = this.getStringExtra("userids")
                    val usernms = this.getStringExtra("usernms")
                    val obj = """{
                        'userids' : '$userids', 'usernms' : '$usernms'
                    }"""
                    if (requestCode == MEMBER_RESULT) {
                        Util.loadUrl(binding.wvMain, "newchat", obj)
                    } else {
                        Util.loadUrl(binding.wvRoom, "invite", obj) //chat.html의 invite 함수 호출
                    }
                }
            } else {
                Util.log("onActivityResult", "Wrong result.")
            }
        } catch (e: Exception) {
            logger.error("onActivityResult: ${e.toString()}")
            Util.procException(curContext, e, "onActivityResult")
        }
    }

    private fun openRoomWithNotiIntent(notiIntent: Intent) : Boolean { //private fun openRoomWithNotiIntent(notiIntent: Intent, isOnCreate: Boolean) : Boolean {
        val type = notiIntent.getStringExtra("type") ?: ""
        if (type != "") {
            val roomid = notiIntent.getStringExtra("roomid") //roomid
            val origin = notiIntent.getStringExtra("origin") //noti
            val objStr = notiIntent.getStringExtra("objStr") //""
            isFromNoti = true
            procOpenRoom(type, roomid!!, origin!!, objStr!!)
            return true
        } else {
            return false
        }
    }

    private fun start() {
        val logTitle = object{}.javaClass.enclosingMethod?.name!!
        CoroutineScope(Dispatchers.Main).launch {
            if (!chkUpdate(true)) return@launch
            procLogin(false) {
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        val winid = Util.getRnd().toString() + "_" + Util.getCurDateTimeStr()
                        val param = JSONObject()
                        param.put("type", "set_new")
                        param.put("userkey", uInfo.userkey)
                        param.put("winid", winid)
                        val json = HttpFuel.post(curContext, "/msngr/chk_redis", param.toString()).await()
                        if (HttpFuel.isNetworkUnstableMsg(json)) {
                            Util.alert(curContext, Const.NETWORK_UNSTABLE, logTitle)
                        } else if (json.get("code").asString != Const.RESULT_OK) {
                            Util.alert(curContext, json.get("msg").asString, logTitle)
                            Util.clearKeyChainForLogout(curContext)
                        } else {
                            KeyChain.set(curContext, Const.KC_WINID, winid)
                            KeyChain.set(curContext, Const.KC_USERIP, json.get("userip").asString)
                            if (ChatService.serviceIntent == null) { //https://forest71.tistory.com/185
                                val intentNew = Intent(curContext, ChatService::class.java)
                                startForegroundService(intentNew) //ChatService가 단독으로 이미 살아있는 상태로 있고 Activity만 추가로 실행하는 경우도 있음
                            } //setupWebViewMain() 관련 : 웹뷰 열리자마자 chk_alive처럼 바로 소켓전송하는 이벤트가 있는데 ChatService.kt에서 소켓연결이 되고 나서 가능하므로
                            setupWebViewMain() //웹뷰에서 chk_alive 같은 이벤트는 소켓연결을 보장받고 나서 실행되어야 함
                            intent?.let {
                                //OnNewIntent()와는 달리 여기로 올 때는 사용자가 앱을 강제종료하고 나서 MainActivity 없이 ChatService만 살아있을 경우임
                                //이 경우, 도착한 노티를 사용자가 클릭하면 MainActivity가 Create되고 여기서 챗방을 추가로 열게 됨
                                openRoomWithNotiIntent(it)
                            }
                        }
                    } catch (e: Exception) {
                        logger.error("$logTitle: ${e.toString()}")
                        Util.procException(curContext, e, logTitle)
                        Util.clearKeyChainForLogout(curContext)
                    }
                }
            }
        }
    }

    private fun logoutApp() {
        Util.clearKeyChainForLogout(curContext)
        stopServiceByLogout = true
        curContext.stopService(Intent(curContext, ChatService::class.java))
        curContext.finish()
    }

    //채팅방(wvRoom)에서는 앱 업데이트 체크하지 않고 목록(wvMain) 화면에서만 하기 : onResume()에서 if (roomidForChatService == "") 일 때만
    private suspend fun chkUpdate(onStatusCreate: Boolean): Boolean {
        val logTitle = object{}.javaClass.enclosingMethod?.name!!
        try {
            if (!Util.chkIfNetworkAvailable(curContext, connManager, "")) {
                if (onStatusCreate) toggleDispRetry(true, "Main", logTitle, "", true)
                return false
            } //아래만 get 방식이고 나머지는 모두 post
            val json = HttpFuel.get(curContext, "${Const.URL_SERVER}/applist.json", null).await() //여기만 get 방식
            if (json.get("code").asString != Const.RESULT_OK) {
                if (HttpFuel.isNetworkUnstableMsg(json)) {
                    toggleDispRetry(true, "Main", logTitle, Const.NETWORK_UNSTABLE, true)
                } else {
                    Util.alert(curContext, json.get("code").asString + "\n" + json.get("msg").asString, logTitle)
                }
                return false
            }
            val jsonApp = json.getAsJsonObject(Const.VERSIONCHK_APP) //Util.log(json1.get("version").asString,"=====", BuildConfig.VERSION_NAME)
            val pInfo = packageManager.getPackageInfo(packageName, 0) //pInfo.versionName
            if (jsonApp.get("version").asString == pInfo.versionName) {
                val jsonEtc = json.getAsJsonObject(Const.VERSIONCHK_ETC)
                ChatService.gapSecOnDualMode = jsonEtc.get("gapsec").asString
                val main_version = json.get(Const.KC_WEBVIEW_MAIN_VERSION).asString
                val chat_version = json.get(Const.KC_WEBVIEW_CHAT_VERSION).asString
                val popup_version = json.get(Const.KC_WEBVIEW_POPUP_VERSION).asString
                val kc_main_version = KeyChain.get(curContext, Const.KC_WEBVIEW_MAIN_VERSION) ?: ""
                val kc_chat_version = KeyChain.get(curContext, Const.KC_WEBVIEW_CHAT_VERSION) ?: ""
                val kc_popup_version = KeyChain.get(curContext, Const.KC_WEBVIEW_POPUP_VERSION) ?: ""
                if (main_version != kc_main_version && kc_main_version != "") { //kc_main_version 빈칸 체크하지 않으면 웹뷰가 더서 웹페이지 내용이 로그인 이전에 실행되는 부분이 있어 체크 필요
                    binding.wvMain.clearCache(true)
                    binding.wvMain.clearHistory()
                    KeyChain.set(curContext, Const.KC_WEBVIEW_MAIN_VERSION, main_version)
                    setupWebViewMain()
                }
                if (chat_version != kc_chat_version && kc_chat_version != "") {
                    binding.wvRoom.clearCache(true)
                    binding.wvRoom.clearHistory()
                    KeyChain.set(curContext, Const.KC_WEBVIEW_CHAT_VERSION, chat_version)
                }
                if (popup_version != kc_popup_version) { //See PopupActivity.
                    KeyChain.set(curContext, Const.KC_WEBVIEW_POPUP_VERSION, "clear_cache" + popup_version)
                }
                return true
            } else {
                Util.toast(curContext, "App downloading for update.." + jsonApp.get("version").asString + "/" + pInfo.versionName)
                CoroutineScope(Dispatchers.IO).launch {
                    val filename = jsonApp.get("filename").asString
                    val path = jsonApp.get("path").asString //Util.log("@@@@", Const.URL_SERVER + path + filename)
                    URL(Const.URL_SERVER + path + "/" + filename).openStream().use { input ->
                        //Util.log(logTitle, input.readBytes().size.toString()) //주의 : readBytes로 먼저 읽고 아래에서 읽으면 0 byte 나옴
                        val file = File(curContext.filesDir, filename) //scoped storage internal(filesDir)
                        FileOutputStream(file).use { output ->
                            try {
                                input.copyTo(output)
                                val apkUri = FileProvider.getUriForFile(curContext,"$packageName.provider", file) //provider : See AndroidManifest.xml
                                val intent = Intent(Intent.ACTION_VIEW)
                                intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP) //NOT NEW_TASK. intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, false) was useless
                                curContext.startActivity(intent)
                            } catch (e1: Exception) {
                                Util.procException(curContext, e1, "App downloading")
                            }
                        }
                    }
                }
                return false
            }
        } catch (e: Exception) {
            logger.error("$logTitle: e ${e.toString()}")
            Util.procException(curContext, e, logTitle)
            return false
        }
    }

    private fun cancelUnreadNoti() {
        NotiCenter.manager?.let {
            if (NotiCenter.notiFound(Const.NOTI_ID_CHK_UNREAD)) it.cancel(Const.NOTI_ID_CHK_UNREAD)
        }
    }

    private fun procCloseRoom() {
        val logTitle = object{}.javaClass.enclosingMethod?.name!!
        try {
            roomidForChatService = ""
            KeyChain.set(curContext, Const.KC_ROOMID_FOR_CHATSERVICE, "")
            binding.wvRoom.visibility = View.GONE
            binding.wvRoom.loadUrl(Const.URL_PUBLIC + Const.PAGE_DUMMY + "?nocache=" + Util.getRnd())
            cancelUnreadNoti()
        } catch (e: Exception) {
            logger.error("$logTitle: ${e.toString()}")
            Util.procException(curContext, e, logTitle)
        }
    }

    private fun procOpenRoom(type: String, roomid: String, origin: String, objStr: String) {
        //type = "newFromMain" or "open" from javascript and origin = "portal" or "" from javascript
        gType = type
        gRoomid = roomid
        gOrigin = origin
        gObjStr = objStr
        setupWebViewRoom(false) //Util.log("procOpenRoom", gType+"==="+gRoomid+"==="+gOrigin+"==="+gObjStr)
    }

    private suspend fun procLogin(chkAuth: Boolean, callback: () -> Unit = {}) { //await() with Suspend function
        val logTitle = object{}.javaClass.enclosingMethod?.name!!
        try {
            var loginNeeded = false
            val autoLogin = KeyChain.get(curContext, Const.KC_AUTOLOGIN) ?: ""
            if (autoLogin == "Y") {
                val param = Util.setParamForAutoLogin(applicationContext)
                authJson = HttpFuel.post(curContext, "/auth/login", param.toString()).await()
                if (HttpFuel.isNetworkUnstableMsg(authJson)) {
                    Util.toast(curContext, Const.NETWORK_UNSTABLE) //Util.alert(curContext, Const.NETWORK_UNSTABLE, logTitle)
                    curContext.finish()
                    return
                } else if (authJson.get("code").asString != Const.RESULT_OK) {
                    Util.clearKeyChainForLogout(curContext)
                    Util.toast(curContext, authJson.get("msg").asString) //Util.alert(curContext, authJson.get("msg").asString, logTitle)
                    loginNeeded = true
                } else if (authJson.get("code").asString == Const.RESULT_OK) {
                    uInfo = UserInfo(curContext, authJson)
                } else {
                    loginNeeded = true
                }
            } else {
                loginNeeded = true
            }
            if (loginNeeded) {
                if (chkAuth) {
                    logoutApp()
                    return
                }
                val mDialogView = layoutInflater.inflate(R.layout.dialog_login, null) //val mDialogView = LayoutInflater.from(this).inflate(R.layout.dialog_login, null)
                val mBuilder = AlertDialog.Builder(curContext).setView(mDialogView).setTitle("Starting ${Const.TITLE}").setCancelable(false)
                val mAlertDialog = mBuilder.show()
                val btnLogin = mDialogView.findViewById<Button>(R.id.login)
                btnLogin.setOnClickListener { //mDialogView.login.setOnClickListener {
                    CoroutineScope(Dispatchers.Main).launch {
                        try {
                            val inUserid = mDialogView.findViewById<EditText>(R.id.userid)
                            val inPwd = mDialogView.findViewById<EditText>(R.id.pwd)
                            val param = JSONObject()
                            param.put("uid", inUserid.text.toString().trim())
                            param.put("pwd", inPwd.text.toString().trim())
                            val autokey_app = Util.getRnd().toString()
                            param.put("autokey_app", autokey_app)
                            param.put("kind", "app") //login.js호출시만 구분이 필요함
                            authJson = HttpFuel.post(curContext, "/auth/login", param.toString()).await()
                            if (HttpFuel.isNetworkUnstableMsg(authJson)) {
                                Util.alert(curContext, Const.NETWORK_UNSTABLE, logTitle)
                            } else if (authJson.get("code").asString != Const.RESULT_OK) {
                                Util.alert(curContext, authJson.get("msg").asString, logTitle)
                            } else {
                                KeyChain.set(curContext, Const.KC_AUTOLOGIN, "Y")
                                uInfo = UserInfo(curContext, authJson)
                                mAlertDialog.dismiss()
                                callback()
                            }
                        } catch (e1: Exception) {
                            logger.error("$logTitle: e1 ${e1.toString()}")
                            Util.procException(curContext, e1, logTitle)
                        }
                    }
                }
            } else {
                callback()
            }
        } catch (e: Exception) {
            logger.error("$logTitle: ${e.toString()}")
            Util.procException(curContext, e, logTitle)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun toggleDispRetry(show: Boolean, webview: String, urlStr: String? = null, errMsg: String? = null, noAutoRetry: Boolean? = null) {
        val logTitle = object{}.javaClass.enclosingMethod?.name!!
        if (show) {
            if (noAutoRetry == true) {
                binding.txtAuto.visibility = View.GONE
            } else {
                binding.txtAuto.visibility = View.VISIBLE
                CoroutineScope(Dispatchers.Main).launch {
                    withTimeout(RETRY_TIMEOUT) {
                        while (true) {
                            delay(RETRY_DELAY)
                            try {
                                var json = SocketIO.connect(curContext, connManager).await()
                                if (json.get("code").asString == Const.RESULT_OK) {
                                    binding.btnRetry.performClick() //if (btnRetry.visibility == View.VISIBLE) btnRetry.performClick()
                                    break
                                }
                            } catch (e: Exception) {
                                Util.toast(curContext, logTitle + ": " + e.toString())
                            }
                        }
                    }
                }
            }
            binding.wvRoom.visibility = View.GONE //if (webview == "Room") wvRoom.visibility = View.GONE
            binding.wvMain.visibility = View.GONE
            binding.btnRetry.visibility = View.VISIBLE
            binding.txtUrl.visibility = View.VISIBLE //binding.txtRmks.visibility = View.VISIBLE
            if (urlStr == null) {
                binding.txtUrl.text = ""
            } else {
                binding.txtUrl.text = "위치 : $urlStr\n$errMsg" //container.background = null //container.setBackgroundColor(Color.WHITE)
            }
        } else {
            if (webview == "Room") binding.wvRoom.visibility = View.VISIBLE
            binding.wvMain.visibility = View.VISIBLE
            binding.btnRetry.visibility = View.GONE
            binding.txtUrl.visibility = View.GONE //binding.txtRmks.visibility = View.GONE
            binding.txtUrl.text = ""
            binding.txtAuto.visibility = View.GONE
        }
    }

    private fun setupWebViewMain() {
        Util.setupWebView(binding.wvMain) //Util.log("###", wvMain.settings.userAgentString)
        binding.wvMain.addJavascriptInterface(WebInterfaceMain(), "AndroidMain") //Util.log("@@@@@@@@@@", wvMain.settings.cacheMode.toString())
        toggleDispRetry(false, "Main")
        binding.wvMain.webChromeClient = object : WebChromeClient() { //onConsoleMessage는 사용하지 않음 (alert가 뜨는데 모두 예상해서 커버하기 쉽지 않음
            override fun onShowFileChooser(webView: WebView, filePathCallback: ValueCallback<Array<Uri?>>, fileChooserParams: FileChooserParams): Boolean {
                filePathCallbackMain = filePathCallback
                val intent = fileChooserParams.createIntent()
                intent.action = Intent.ACTION_GET_CONTENT
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.type = "image/*"
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
                startActivityForResult(Intent.createChooser(intent, "Image Browser"), FILE_RESULT_MAIN)
                return true
            }
        }
        binding.wvMain.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val urlStr = request!!.url.toString()
                if (urlStr.startsWith(Const.URL_HOST)) {
                    if (!urlStr.contains(Const.PAGE_ROOM)) return false //ignore dummy page
                    if (urlStr.startsWith("tel:")) {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlStr))
                        startActivity(intent)
                    }
                    view!!.loadUrl(urlStr)
                } else { //ex) https://socket.io, https://naver.com ..
                    view!!.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(urlStr)))
                }
                return true //return super.shouldOverrideUrlLoading(view, request)
            }
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error) //https://gist.github.com/seongchan/752db643377f823950648d0bc80599c1
                val urlStr = request?.url?.toString() ?: "" //if (request != null) request.url.toString() else ""; //request?.url.toString() //Multiple request shown like jquery module.
                if (error != null && error.description != "" && urlStr.contains(Const.URL_JAY) && urlStr.contains(Const.PAGE_MAIN)) {
                    val errMsg = "${error.errorCode}/${error.description}" //val errMsg = "${error?.errorCode}/${error?.description}"
                    Util.log("webviewMain error", urlStr+"===="+errMsg)
                    toggleDispRetry(true, "Main", urlStr, errMsg)
                } else { //ajax : ex) -2/net::ERR_INTERNET_DISCONNECTED : Network not available
                    //Util.toast(curContext, "wvMain/${error?.errorCode}/${error?.description}/${urlStr}") //Multiple toast shown because of Multiple request
                }
            }
        } //Util.log("@@@@@@@@@@@", KeyChain.get(curContext, Const.KC_MODE_PUBLIC) + "${Const.PAGE_MAIN}?webview=and")
        binding.wvMain.loadUrl(Const.URL_PUBLIC + "${Const.PAGE_MAIN}?webview=and&nocache=" + Util.getRnd()) //not ios
    }

    private fun setupWebViewRoom(refresh: Boolean) {
        Util.setupWebView(binding.wvRoom)
        binding.wvRoom.addJavascriptInterface(WebInterfaceRoom(), "AndroidRoom")
        toggleDispRetry(false, "Room") //Util.log(refresh.toString()+"==="+gRoomid+"==="+roomidForChatService)
        if (!refresh && gRoomid != "" && gRoomid == roomidForChatService) return
        binding.wvRoom.webChromeClient = object : WebChromeClient() { //onConsoleMessage는 사용하지 않음 (alert가 뜨는데 모두 예상해서 커버하기 쉽지 않음
            override fun onShowFileChooser(webView: WebView, filePathCallback: ValueCallback<Array<Uri?>>, fileChooserParams: FileChooserParams): Boolean {
                filePathCallbackRoom = filePathCallback
                val intent = fileChooserParams.createIntent()
                intent.action = Intent.ACTION_GET_CONTENT
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.type = "*/*"
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                startActivityForResult(Intent.createChooser(intent, "File Browser"), FILE_RESULT_ROOM)
                return true
            }
        }
        binding.wvRoom.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val urlStr = request!!.url.toString()
                if (urlStr.startsWith(Const.URL_HOST)) {
                    if (!urlStr.contains(Const.PAGE_ROOM)) return false //ignore dummy page
                    view!!.loadUrl(urlStr)
                } else { //ex) https://socket.io, https://naver.com ..
                    view!!.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(urlStr)))
                }
                return true //return super.shouldOverrideUrlLoading(view, request)
            }
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                val urlStr = if (request != null) request.url.toString() else "" //request?.url.toString() //Multiple request might be seen like jquery module.
                if (error != null && error.description != "" && urlStr.contains(Const.URL_JAY) && urlStr.contains(Const.PAGE_ROOM)) {
                    val errMsg = "${error.errorCode}/${error.description}"
                    toggleDispRetry(true, "Room", urlStr, errMsg)
                } else { //ajax : ex) -2/net::ERR_INTERNET_DISCONNECTED : Network not available
                    //Util.toast(curContext, "wvRoom/${error?.errorCode}/${error?.description}/${urlStr}") //Multiple toast shown because of Multiple request
                }
            }
        }
        Util.setDownloadListener(curContext, binding.wvRoom)
        binding.wvRoom.loadUrl(Const.URL_PUBLIC + "${Const.PAGE_ROOM}?webview=and&type=$gType&roomid=$gRoomid&origin=$gOrigin&nocache=" + Util.getRnd())
    }

    private fun updateAllUnreads(isFromNoti: Boolean) { //private fun updateAllUnreads(init: Boolean, isFromNoti: Boolean) { //for room only
        val logTitle = object{}.javaClass.enclosingMethod?.name!!
        try {
            NotiCenter.mapRoomid[gRoomid]?.let { NotiCenter.manager!!.cancel(it) }
            NotiCenter.mapRoomid.remove(gRoomid)
            if (NotiCenter.mapRoomid.isEmpty()) NotiCenter.manager!!.cancel(Const.NOTI_ID_SUMMARY)
            Util.loadUrl(binding.wvRoom, "updateAllUnreadsFromWebView", isFromNoti.toString())
        } catch (e: Exception) {
            logger.error("$logTitle: ${e.toString()}")
            Util.procException(curContext, e, logTitle)
        }
    }

    inner class WebInterfaceMain {

        @JavascriptInterface
        fun procAfterOpenMain() {
            val logTitle = object{}.javaClass.enclosingMethod?.name!!
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    disposableMain?.dispose()
                    disposableMain = RxToDown.subscribe<RxEvent>().subscribe { //RxToDown.subscribe<RxEvent>().observeOn(AndroidSchedulers.mainThread()) {//to receive the event on main thread
                        //코들린에서의 소켓이벤트시 데이터를 웹뷰로 전달함
                        //따라서, 웹뷰내 페이지가 소켓데이터를 받을 준비가 되어 있어야 하는데 그 준비가 된 상태에서 호출하는 함수가 getFromWebViewSocket()임
                        //그 의미는, 여기 procAfterOpenMain()가 호출되기 전엔 RxToDown.post()하면 안된다는 것임 (챗방에서도 마찬가지)
                        //한편, RxMsg를 구독하는 것은 웹뷰와는 별도로 onCreate()에서 처리해야 ChatService.kt에서 사용싯점을 신경쓰지 않아도 되므로 onCreate()으로 이동시킴
                        CoroutineScope(Dispatchers.Main).launch {
                            var param: JSONObject?= null
                            try {
                                param = it.data as JSONObject //Util.log("@@Main", param.toString())
                                Util.loadUrlJson(binding.wvMain, "getFromWebViewSocket", param)
                                //Error('getFromWebViewSocket is not defined')는 여기서 catch되지 않고 위의 onConsoleMessage()에서 처리. getFromWebViewSocket가 javascript에 있는 함수이기 때문
                                //여기 procAfterOpenMain()은 main.html안의 jquery document.ready후에 getFromWebViewSocket 호출은 보장되지만
                                //RxEvent 데이터가 들어올 때 웹페이지 새로고침 등이 일어나면 그 순간에 getFromWebViewSocket이 없으므로 발생하는 오류임.
                                //하지만, 그 순간에 받지 못한 RxEvent 데이터는 새로고침후에 바로 가져오는 것이므로 문제되지 않을 것이므로 onConsoleMessage()에서 예외로 처리.
                            } catch (e: Exception) {
                                val msg = "${e.toString()}\n${it.ev}\n${param.toString()}"
                                logger.error("$logTitle: $msg")
                                Util.procExceptionStr(curContext, msg, logTitle)
                            }
                        }
                    }
                    val obj = Util.getStrObjFromUserInfo(uInfo) //Util.log("@@@@@@@@@@@", obj.toString()+"==="+authJson.toString())
                    Util.loadUrl(binding.wvMain, "startFromWebView", obj, authJson.toString())
                } catch (e1: Exception) {
                    logger.error("$logTitle: e1 ${e1.toString()}")
                    Util.procException(curContext, e1, logTitle)
                }
            }
        }

        @JavascriptInterface
        fun refreshToken(token: String) {
            KeyChain.set(curContext, Const.KC_TOKEN, token)
            uInfo.token = token
        }

        @JavascriptInterface
        fun reload() {
            CoroutineScope(Dispatchers.Main).launch {
                setupWebViewMain()
            }
        }

        @JavascriptInterface
        fun logout() {
            CoroutineScope(Dispatchers.Main).launch {
                logoutApp()
            }
        }

        @JavascriptInterface
        fun openRoom(type: String, roomid: String, origin: String, objStr: String) {
            val logTitle = object{}.javaClass.enclosingMethod?.name!!
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    Util.connectSockWithCallback(curContext, connManager) {
                        if (it.get("code").asString != Const.RESULT_OK) {
                            Util.toast(curContext, it.get("msg").asString)
                            return@connectSockWithCallback
                        }
                       procOpenRoom(type, roomid, origin, objStr)
                    }
                } catch (e: Exception) {
                    logger.error("$logTitle: ${e.toString()}")
                    Util.procException(curContext, e, logTitle)
                }
            }
        }

        @JavascriptInterface
        fun openPopup(origin: String, objStr: String) { //origin=urlStr
            val logTitle = object{}.javaClass.enclosingMethod?.name!!
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val pIntent = Intent(curContext, PopupActivity::class.java)
                    pIntent.putExtra("origin", origin)
                    pIntent.putExtra("objStr", objStr)
                    startActivityForResult(pIntent, MEMBER_RESULT)
                } catch (e: Exception) {
                    logger.error("$logTitle: ${e.toString()}")
                    Util.procException(curContext, e, logTitle)
                }
            }
        }

        @JavascriptInterface
        fun setNotiMobile() {
            var intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            intent.putExtra(Settings.EXTRA_CHANNEL_ID, Const.NOTICHANID_COMMON)
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            }
        }

        @JavascriptInterface
        fun showLog(num: Int) { //개발자 테스트용. logger.info("test")로 테스트 가능
            val logTitle = object{}.javaClass.enclosingMethod?.name!!
            CoroutineScope(Dispatchers.Main).launch {
                try { //File(path).walkTopDown().forEach { Util.log("=====", it.toString()) }
                    curContext.filesDir.let { it ->
                        val listFile = Util.getFiles(it)
                        if (listFile == null || listFile.size == 0) {
                            Util.toast(curContext, "Log files not exists.")
                            return@let
                        }
                        if (listFile.size - 1 < num) {
                            Util.toast(curContext, "listFile.size - 1 < num")
                            return@let
                        } //for (i in listFile.indices) { var name = listFile[i] }
                        val path = curContext.filesDir.toString() //https://stackoverflow.com/questions/55182578/how-to-read-plain-text-file-in-kotlin
                        val bufferedReader: BufferedReader = File(path + "/" + listFile[num]).bufferedReader()
                        val inputString = bufferedReader.use { it.readText() }
                        println(inputString)
                        val receiptHtml: String = "<div>${inputString}/<div>"
                        binding.wvMain.loadData(receiptHtml, "text/html; charset=utf-8", "UTF-8")
                    }
                } catch (e: Exception) {
                    logger.error("$logTitle: ${e.toString()}")
                    Util.procException(curContext, e, logTitle)
                }
            }
        }

        @JavascriptInterface
        fun deleteLog() { //개발자 테스트용
            val logTitle = object{}.javaClass.enclosingMethod?.name!!
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    curContext.filesDir.let { it ->
                        val listFile = Util.getFiles(it)
                        if (listFile == null || listFile.size == 0) {
                            Util.toast(curContext, "Log files not exists.")
                            return@let
                        }
                        val path = curContext.filesDir.toString()
                        for (i in listFile.indices) File(path + "/" + listFile[i]).delete()
                        Util.toast(curContext, "deleteLog done.")
                    }
                } catch (e: Exception) {
                    logger.error("$logTitle: ${e.toString()}")
                    Util.procException(curContext, e, logTitle)
                }
            }
        }

        @JavascriptInterface
        fun appInfo() {
            CoroutineScope(Dispatchers.Main).launch {
                val pInfo = packageManager.getPackageInfo(packageName, 0) //pInfo.versionName
                Util.toast(curContext, Const.TITLE + " " + pInfo.versionName + " by hushsbay@gmail.com")
            }
        }

    }

    inner class WebInterfaceRoom {

        @JavascriptInterface
        fun procAfterOpenRoom() {
            val logTitle = object{}.javaClass.enclosingMethod?.name!!
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val obj = Util.getStrObjFromUserInfo(uInfo)
                    Util.loadUrl(binding.wvRoom, "startFromWebView", obj, gObjStr)
                    disposableRoom?.dispose()
                    disposableRoom = RxToRoom.subscribe<RxEvent>().subscribe { //disposable = RxBus.subscribe<RxEvent>().observeOn(AndroidSchedulers.mainThread()) //to receive the event on main thread
                        CoroutineScope(Dispatchers.Main).launch {
                            var param: JSONObject?= null
                            try {
                                param = it.data as JSONObject //Util.log("@@Room", param.toString())
                                if (roomidForChatService != "") Util.loadUrlJson(binding.wvRoom, "getFromWebViewSocket", param)
                            } catch (e: Exception) {
                                val msg = "${e.toString()}\n${it.ev}\n${param.toString()}"
                                logger.error("$logTitle: $msg")
                                Util.procExceptionStr(curContext, msg, logTitle)
                            }
                        }
                    }
                    roomidForChatService = gRoomid
                    KeyChain.set(curContext, Const.KC_ROOMID_FOR_CHATSERVICE, gRoomid)
                    //updateAllUnreads(true, false) //웹뷰에서 수행되므로 여기서 굳이 수행하지 않아도 됨
                } catch (e1: Exception) {
                    logger.error("$logTitle: ${e1.toString()}")
                    Util.procException(curContext, e1, logTitle)
                }
            }
        }

        @JavascriptInterface
        fun putData(data: String) {
            val logTitle = object{}.javaClass.enclosingMethod?.name!!
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val json = JSONObject(data)
                    val type = json.getString("type")
                    if (type == "set_roomid") { //see sock_ev_create_room(dupchk) in jay_chat.js
                        gType = "open"
                        gRoomid = json.getString("roomid") //new roomid
                        roomidForChatService = gRoomid
                        KeyChain.set(curContext, Const.KC_ROOMID_FOR_CHATSERVICE, gRoomid)
                        gOrigin = ""
                        gObjStr = ""
                    }
                } catch (e: Exception) {
                    logger.error("$logTitle: ${e.toString()}")
                    Util.procException(curContext, e, logTitle)
                }
            }
        }

        @JavascriptInterface
        fun openPopup(origin: String, objStr: String) { //origin = popup.html or main.html
            val logTitle = object{}.javaClass.enclosingMethod?.name!!
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val pIntent = Intent(curContext, PopupActivity::class.java)
                    pIntent.putExtra("origin", origin)
                    pIntent.putExtra("objStr", objStr)
                    if (origin.contains(Const.PAGE_MAIN)) { //main.html
                        startActivityForResult(pIntent, INVITE_RESULT)
                    } else { //popup.html
                        startActivity(pIntent)
                    }
                } catch (e: Exception) {
                    logger.error("$logTitle: ${e.toString()}")
                    Util.procException(curContext, e, logTitle)
                }
            }
        }

        @JavascriptInterface
        fun closeRoom() {
            CoroutineScope(Dispatchers.Main).launch {
                if (roomidForChatService != "") procCloseRoom()
            }
        }

    }

}