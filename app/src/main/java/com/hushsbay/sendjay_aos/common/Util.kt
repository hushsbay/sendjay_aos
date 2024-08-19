package com.hushsbay.sendjay_aos.common

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.UrlQuerySanitizer
import android.os.Environment
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.hushsbay.sendjay_aos.ChatService
import com.hushsbay.sendjay_aos.MainActivity
import com.hushsbay.sendjay_aos.data.RxEvent
import com.hushsbay.sendjay_aos.data.RxMsg
import io.reactivex.disposables.Disposable
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.abs

class Util {

    companion object {

        fun alert(context: Context, msg: String, title: String?=null, onYes: () -> Unit = {}, onNo: (() -> Unit)? = null, onCancel: (() -> Unit)? = null) {
            val builder = AlertDialog.Builder(context)
            val titleDisp = title ?: Const.TITLE
            val body = "[$titleDisp]\n"
            builder.setTitle(Const.TITLE)
            builder.setMessage(body + msg)
            builder.setPositiveButton("Yes") { _, _ -> onYes() } //{ dialog, which -> onYes() }
            if (onNo != null) builder.setNegativeButton("No") { _, _ -> onNo() } //{ dialog, which -> onNo() }
            if (onCancel != null) builder.setNeutralButton("Cancel") { _, _ -> onCancel() }
            val dialog: AlertDialog = builder.create()
            dialog.show()
        }

        fun toast(context: Context, msg: String) {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }

        fun log(title: String?=null, vararg exStr: String) {
            var titleDisp = title ?: Const.TITLE
            var str = ""
            for ((idx, item) in exStr.withIndex()) {
                if (idx == 0) {
                    str = item
                } else {
                    str += ", $item"
                }
            }
            Log.i("$titleDisp ########", str)
        }

        fun procException(context: Activity, e: Exception, title: String?=null) {
            alert(context, e.toString(), title)
            log(title, e.toString())
            e.printStackTrace()
        }

        fun procExceptionStr(context: Activity, msg: String, title: String?=null) {
            alert(context, msg, title)
            log(title, msg)
        }

        fun setParamForAutoLogin(context: Context): JSONObject {
            val param = JSONObject()
            param.put("uid", KeyChain.get(context, Const.KC_USERID))
            param.put("pwd", KeyChain.get(context, Const.KC_PWD))
            param.put("autokey_app", KeyChain.get(context, Const.KC_AUTOKEY_APP))
            param.put("autologin", "Y")
            param.put("kind", "app") //login.js 호출시만 구분 필요함. app.js에서도 동일 사용
            return param
        }

        suspend fun refreshTokenOrAutoLogin(context: Context): Deferred<JsonObject> {
            //*** 루프 돌면서 이 메소드를 쓰면 안됨 (서버재시작시 타임아웃 처리된 호출이 죽지 않고 그대로 서버로 호출되어 부하 증가됨)
            //*** 원인은 Http 호출 문제인 거 같은데 (웹뷰에서 ajax 호출시에도 동일한 현상) 정확한 원인 파악이 안된 상태임 (서버설정 문제??!)
            //*** sock.connect()시엔 refreshTokenOrAutoLogin() 사용하지 말고 socket에 아예 토큰과 비번을 실어서 동일한 처리를 하도록 함 (SocketIO.kt invoke() 참조)
            //알림받을 때처럼 토큰이 만기가 된 상황에서는 다시 토큰체크+자동로그인하는 것이 최선일 것임 (액티비티가 없는 상황에서만 사용하기. 액티비티는 로그인화면과 연동)
            //=> 주기적으로 토큰 갱신하는 것은 모바일 특성상 모두 커버하기 어렵고, 무조건 자동로그인하는 것보다는 더 나은 해법으로 판단됨
            //rest(http) 호출하는 모든 곳에 적용할 필요없는데, 소켓연결이나 onResume()에서는 아예 자동로그인하므로 그 직후에는 이 메소드가 필요없음
            //웹뷰로 토큰 전달시는 loadUrl(), sendToDownWhenConnDisconn()등을 통해 토큰을 넘기고 있음
            return CoroutineScope(Dispatchers.IO).async {
                val logTitle = object{}.javaClass.enclosingMethod?.name!!
                val param = JSONObject()
                param.put("userid", KeyChain.get(context, Const.KC_USERID))
                param.put("token", KeyChain.get(context, Const.KC_TOKEN))
                val json = HttpFuel.post(context, "/auth/refresh_token", param.toString()).await()
                if (json.get("code").asString == Const.RESULT_OK) {
                    KeyChain.set(context, Const.KC_TOKEN, json.get("token").asString)
                    json
                } else if (json.get("code").asString == Const.RESULT_TOKEN_EXPIRED) {
                    val param = setParamForAutoLogin(context) //토큰을 새로 얻으려고 자동로그인 하는 것임 (액티비티 없이 서비스만 실행되는 경우도 있음)
                    val authJson: JsonObject = HttpFuel.post(context, "/auth/login", param.toString()).await()
                    if (HttpFuel.isNetworkUnstableMsg(authJson)) {
                        showRxMsgInApp(Const.SOCK_EV_TOAST, Const.NETWORK_UNSTABLE + " : refreshTokenOrAutoLogin_0")
                    } else if (authJson.get("code").asString != Const.RESULT_OK) {
                        showRxMsgInApp(Const.SOCK_EV_TOAST,"$logTitle: ${authJson.get("msg").asString}")
                    } else if (authJson.get("code").asString == Const.RESULT_OK) {
                        KeyChain.set(context, Const.KC_TOKEN, authJson.get("token").asString)
                    }
                    authJson
                } else if (HttpFuel.isNetworkUnstableMsg(json)) {
                    showRxMsgInApp(Const.SOCK_EV_TOAST, Const.NETWORK_UNSTABLE + " : refreshTokenOrAutoLogin_1")
                    json//callback이 소용없음
                } else {
                    showRxMsgInApp(Const.SOCK_EV_TOAST,"$logTitle: ${json.get("msg").asString}")
                    json //토큰 만기가 나올 가능성이 많아짐
                }
            }
        }

        fun clearKeyChainForLogout(context: Context) {
            KeyChain.set(context, Const.KC_AUTOLOGIN, "")
            KeyChain.set(context, Const.KC_USERID, "")
            KeyChain.set(context, Const.KC_TOKEN, "")
        }

        fun chkIfNetworkAvailable(context: Activity, connManager: ConnectivityManager, type: String): Boolean {
            val nwCapa = connManager.getNetworkCapabilities(connManager.activeNetwork)
            return if (nwCapa != null && nwCapa.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                true //return true even if wifi is on and has errors
            } else {
                if (type == "toast") {
                    toast(context, Const.NETWORK_UNAVAILABLE + "##")
                } else if (type == "alert") {
                    alert(context, Const.NETWORK_UNAVAILABLE, Const.TITLE)
                }
                false
            }
        }

        fun chkWifi(connManager: ConnectivityManager): Boolean {
            val nwCapa = connManager.getNetworkCapabilities(connManager.activeNetwork)
            return nwCapa != null && nwCapa.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        }

        fun connectSockWithCallback(context: Context, connManager: ConnectivityManager, callback: (json: JsonObject) -> Unit = {}) {
            val logTitle = object{}.javaClass.enclosingMethod?.name!!
            //if (ChatService.isBeingSockChecked) return; ChatService.isBeingSockChecked = true; //다른 소켓통신이 여기서 막힐 수도 있으므로 사용하면 안됨
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    var json = SocketIO.connect(context, connManager).await()
                    val code = json.get("code").asString
                    if (code == Const.RESULT_OK) { //1) 미접속->접속일 경우 2) 접속된 상태가 계속되는 경우 2가지 모두 해당함
                        //1) 경우는 ChatService.kt에서 socket.io 고유 이벤트 잡아서 처리하고
                        //여기서는 2) 경우에 대해서만 처리. 2)는 계속 체크하는 의미로 1)과는 달리 SOCK_EV_MARK_AS_CONNECT로 처리함
                        sendToDownWhenConnDisconn(context, Const.SOCK_EV_MARK_AS_CONNECT)
                    } else {
                        sendToDownWhenConnDisconn(context, Socket.EVENT_DISCONNECT)
                        KeyChain.set(context, Const.KC_DT_DISCONNECT, getCurDateTimeStr(true)) //로깅(테스트) 목적
                    }
                    //SocketIO.connect()에서 리턴되는 객체나 값으로 갱신된 토큰을 받는 방법을 아직 파악하지 못했으므로
                    //소켓 연결 직후인 여기서는 만기전 토큰이 보장되어야 할 http 호출은 하지 말기로 함 (Const.SOCK_EV_REFRESH_TOKEN 이벤트에서 처리)
                    callback(json)
                } catch (e: Exception) {
                    sendToDownWhenConnDisconn(context, Socket.EVENT_DISCONNECT)
                    val jsonStr = """{ code : '${Const.RESULT_ERR}', msg : '$logTitle\n${e.toString()}' }"""
                    callback(Gson().fromJson(jsonStr, JsonObject::class.java))
                }
            }
        }

        fun procRxMsg(context: Activity, callback: ((type: String, msg: String) -> Unit)? = null): Disposable {
            return RxToDown.subscribe<RxMsg>().subscribe {
                CoroutineScope(Dispatchers.Main).launch { //Do not remove this line since Dispatchers.Main covers Dispatchers.IO which already called.
                    try {
                        val param = it.data as JSONObject
                        val msg = param.getString("msg")
                        when (it.type) {
                            Const.SOCK_EV_ALERT -> {
                                alert(context, msg)
                            }
                            Const.SOCK_EV_TOAST -> {
                                toast(context, msg)
                            }
                            else -> {
                                callback?.invoke(it.type, msg)
                            }
                        }
                    } catch (e: Exception) {
                        alert(context, e.toString(), "procRxMsg")
                    }
                }
            }
        }

        fun showRxMsgInApp(type: String, msg: String) { //RxToDown만 처리함
            //예) RxToDown.post(RxMsg(Const.SOCK_EV_TOAST, JSONObject().put("msg", Const.NETWORK_UNSTABLE)))
            //ChatService.kt에서도 toast 또는 alert 등을 표시하기 위해 만든 함수인데 RxToDown이 많이 쓰여서 코딩 편의상 더 단순하게 줄인 형태임
            //RxToDown.post()로 alert/toast 처리하는 것은 MainActivity.kt 등 UI에서는 굳이 사용할 필요없음
            RxToDown.post(RxMsg(type, JSONObject().put("msg", msg)))
        }

        fun sendToDownWhenConnDisconn(context: Context, ev: String) { //See Util.loadUrlJson(wvMain, "getFromWebViewSocket", param) in MainActivity.kt
            //RxToDown.post() 사용시 그전에 구독상태가 되어 있어야 하는데 그게 아니면 아래 메시지가 전달되지 않을 것임을 유의해야 함
            //구독 싯점은 MainActivity.kt의 procAfterOpenMain() 설명 참조
            try {
                val json = JSONObject()
                json.put("ev", ev)
                val json1 = JSONObject()
                json1.put("token", KeyChain.get(context, Const.KC_TOKEN)) //예) 재연결후 웹뷰에서 http호출이 바로 필요할 때 토큰 갱신
                json.put("data", json1)
                RxToDown.post(RxEvent(ev, json, "parent"))
                val roomidForService = KeyChain.get(context, Const.KC_ROOMID_FOR_CHATSERVICE) ?: ""
                if (roomidForService != "") RxToRoom.post(RxEvent(ev, json, roomidForService))
            } catch (ex: Exception) {
                log("sendToDownWhenConnDisconn", ex.toString())
            }
        }

        fun setupWebView(webview: WebView) {
            webview.settings.javaScriptEnabled = true
            webview.settings.javaScriptCanOpenWindowsAutomatically = true
            webview.settings.cacheMode = WebSettings.LOAD_NO_CACHE //LOAD_CACHE_ELSE_NETWORK, LOAD_DEFAULT,
            webview.settings.domStorageEnabled = true
            webview.addJavascriptInterface(WebInterface(), "AndroidCom")
        }

        //Use param with obj.toString() or Json-Type String (like """{ }"""). => Uncaught SyntaxError: Invalid or unexpected token
        fun loadUrl(webview: WebView, func: String, vararg param: String) {
            var strCall = "javascript:${func}('and'" //and means android (ios=ios)
            for (item in param) strCall += ", $item" //do not 'single quote' on $item
            strCall += ")"
            webview.loadUrl(strCall)
        }

        fun loadUrlJson(webview: WebView, func: String, param: JSONObject) { //not String but JSONObject (URLEncoding not needed)
            webview.loadUrl("javascript:${func}('and', ${param})") //and means android (ios=ios)
        }

        fun setDownloadListener(context: Activity, webview: WebView) {
            webview.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
                try { //See fileDownload() in chat.html. https://sysdocu.tistory.com/1329
                    var filename = ""
                    val fileUrl = Uri.parse(url)
                    val request = DownloadManager.Request(fileUrl)
                    request.setMimeType(mimeType)
                    request.addRequestHeader("cookie", CookieManager.getInstance().getCookie(url))
                    request.addRequestHeader("User-Agent", userAgent) //request.setDescription("Downloading file...")
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    val fileUrlStr = fileUrl.toString()
                    val fileUrlArr = fileUrlStr.split("?") //Util.log("@@@@@@@", fileUrl.toString(), "@@@@@", fileUrlArr[0])
                    if (fileUrlStr.contains("/get_msginfo")) { //https://sendjay.com:444/get_msginfo?type=imagetofile&msgid=xxxxx&ver=491107
                        val sanitizer = UrlQuerySanitizer("?" + fileUrlArr[1]) //? needed
                        filename = Const.APP_NAME + "_" + sanitizer.getValue("suffix") + ".png"
                        request.setTitle(filename)
                    } else { //proc_file //url => https://hushsbay.net:444/proc_file/20201223140444716000807636A1TPgWSYaA%2F1%2Flogo$$20210107232606745891.png?msgid=2021010808..
                        filename = getFilenameFromTalkBody(fileUrlArr[0]) //last dir => ~A%2F1%2F(~A/1/), file => logo$$xxxxx.png
                        request.setTitle(decodeUrl(filename)) //request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType))
                    } //request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, filename) //download ok! inside app (/Android/data/com.~)
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename) //public external dir
                    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    dm.enqueue(request)
                } catch (ex: Exception) {
                    log("setDownloadListener", ex.toString())
                }
            }
        }

        fun getStrObjFromUserInfo(uInfo: UserInfo): String {
            return """{
                'token' : '${uInfo.token}', 'userid' : '${uInfo.userid}', 'userkey' : '${uInfo.userkey}', 'usernm' : '${uInfo.usernm}',
                'orgcd' : '${uInfo.orgcd}', 'orgnm' : '${uInfo.orgnm}', 'toporgcd' : '${uInfo.toporgcd}', 'toporgnm' : '${uInfo.toporgnm}',
                'autokey_app' : '${uInfo.autokey_app}'
            }"""
        }

        fun getFilenameFromTalkBody(body: String): String { //body = 20210217201326649000714371IGqMWTScRw/1/selecton_oragne$$20210401031952243612.png##4115
            val fileArr = body.split(Const.DELI)
            val fileBrr = fileArr[0].split("/")
            val fileCrr = fileBrr[fileBrr.size - 1].split(Const.SUBDELI)
            val fileDrr = fileCrr[1].split(".")
            val ext = if (fileDrr.size == 1) "" else "." + fileDrr[1]
            return fileCrr[0] + ext
        }

        fun getTalkBodyCustom(type: String, body: String): String { //See displayTalkBodyCustom() in jay_common.js, too.
            return if (body == Const.CELL_REVOKED) {
                body
            } else if (type == "invite") {
                val arr = body.split(Const.DELI)
                if (arr.size >= 3) {
                    "초대 : ${arr[0]}" //"${arr[0]} invited by ${arr[2]}"
                } else {
                    "초대 : ${arr[0]}" //"${arr[0]} invited"
                }
            } else if (type == "image") {
                type
            } else if (type == "file" || type == "flink") {
                getFilenameFromTalkBody(body) //20210217201326649000714371IGqMWTScRw/1/selecton_oragne$$20210401031952243612.png##4115
            } else {
                body
            }
        }

        fun getRnd(min: Int?=null, max: Int?=null): Int {
            val _min = min ?: 0
            val _max = max ?: 1000000
            return (_min.._max).random()
        }

        fun getCurDateTimeStr(deli: Boolean?=null, millisec: Boolean?=null): String {
            val dt = LocalDateTime.now()
            val _date = dt.format(DateTimeFormatter.ISO_DATE)
            val _time = dt.format(DateTimeFormatter.ISO_TIME)
            var datetime = ""
            datetime = if (millisec == null || millisec == false) {
                _date + " " + _time.take(8) //yyyy-mm-dd hh:MM:ss
            } else {
                _date + " " + _time.padEnd(15, '0') //hh:MM:ss.123000
            }
            if (deli == null || deli == false) datetime = datetime.replace("""[-: .]""".toRegex(), "") //빈칸도 replace
            return datetime
        }

        fun decodeUrl(str: String): String {
            return URLDecoder.decode(str, "utf-8")
        }

        fun procConsoleMsg(context: Activity, msg: String, title: String) { //webview에서 onConsoleMessage는 사용하지 않음 (alert가 뜨는데 모두 예상해서 커버하기 쉽지 않음
            val logTitle = object{}.javaClass.enclosingMethod?.name!!
            if (msg.contains("Some resource load requests were throttled")) {
                /* For test, below msg are to be skipped so that what happens. (chrome says it's just msg)
                "Some resource load requests were throttled while the tab was in background, and no request was sent from the queue in the last 1 minute.
                This means previously requested in-flight requests haven't receiverd any response from servers."
                See https://www.chromestatus.com/feature/5527160148197376 for more details. */
            } else if (msg.contains("getFromWebViewSocket is not defined")) {
                //see procAfterOpenMain() in MainActivity.kt and PopupActivity.kt
            } else if (msg.contains("disconnected") || msg.contains("timeout")) { //ex) procSocketOn:qry_unread:HttpFuel:get: timeout
                //연결이 끊어질 때마다 웹뷰에서 토스트나 경고가 뜨면 사용자 불편 : 현재 상단 아이콘으로 표시되는 것으로 처리하고 있음
            } else {
                alert(context, msg, logTitle + ":" + title)
            }
        }

        fun getFiles(dir: File): ArrayList<String>? {
            var fileArray = dir.listFiles() // { pathname -> pathname.name.endsWith(".zip") }
            fileArray = fileArray?.let { sortFileList(it) }
            val fileArrayList: ArrayList<String> = ArrayList()
            if (fileArray != null) {
                for (file in fileArray) {
                    if (file.isFile) fileArrayList.add(file.name)
                }
            }
            fileArrayList.reverse()
            return fileArrayList
        }

        private fun sortFileList(files: Array<File>): Array<File> {
            Arrays.sort(files) { object1, object2 ->
                object1.lastModified().toString().compareTo(object2.lastModified().toString())
            }
            return files
        }

    }

}
