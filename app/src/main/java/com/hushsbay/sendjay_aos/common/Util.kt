package com.hushsbay.sendjay_aos.common

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
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
import com.hushsbay.sendjay_aos.data.RxEvent
import com.hushsbay.sendjay_aos.data.RxMsg
import io.reactivex.disposables.Disposable
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

        fun alert(context: Activity, msg: String, title: String?=null, onYes: () -> Unit = {}, onNo: (() -> Unit)? = null, onCancel: (() -> Unit)? = null) {
            val builder = AlertDialog.Builder(context)
            val titleDisp = title ?: Const.TITLE
            val body = "[$titleDisp]\n" //if (titleDisp.startsWith(Const.TITLE)) "" else "[$title]\n"
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
            var titleDisp = title ?: Const.TITLE //if (!titleDisp.startsWith(Const.TITLE)) titleDisp = Const.TITLE + "|" + titleDisp
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

        fun setParamForAutoLogin(context: Context): org.json.JSONObject {
            val param = org.json.JSONObject()
            param.put("uid", KeyChain.get(context, Const.KC_USERID))
            param.put("pwd", KeyChain.get(context, Const.KC_PWD))
            param.put("autokey_app", KeyChain.get(context, Const.KC_AUTOKEY_APP))
            param.put("autologin", "Y")
            param.put("kind", "app") //login.js호출시만 구분이 필요함
            return param
        }

        fun chkIfNetworkAvailable(context: Activity, connManager: ConnectivityManager, type: String): Boolean {
            val nwCapa = connManager.getNetworkCapabilities(connManager.activeNetwork)
            return if (nwCapa != null && nwCapa.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                //hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR, TRANSPORT_WIFI, TRANSPORT_ETHERNET..)
                true //return true even if wifi is on and has errors.
            } else {
                if (type == "toast") {
                    Util.toast(context, Const.NETWORK_UNAVAILABLE)
                } else if (type == "alert") {
                    Util.alert(context, Const.NETWORK_UNAVAILABLE, Const.TITLE)
                }
                false
            }
        }

        fun connectSockWithCallback(context: Context, connManager: ConnectivityManager, callback: (json: JsonObject) -> Unit = {}) {
            val logTitle = object{}.javaClass.enclosingMethod?.name!!
            //if (ChatService.isBeingSockChecked) return; ChatService.isBeingSockChecked = true; //다른 소켓통신이 여기서 막힐 수도 있으므로 사용하면 안됨
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val token = KeyChain.get(context, Const.KC_TOKEN) ?: ""
                    var json = SocketIO.connect(context, connManager, token).await()
                    val code = json.get("code").asString
                    if (code == Const.RESULT_OK) { //1) 미접속->접속일 경우 2) 접속된 상태가 계속되는 경우 2가지 모두 해당함
                        //1) 경우는 ChatService.kt에서 socket.io 고유 이벤트 잡아서 처리하고
                        //여기서는 2) 경우에 대해서만 처리. 2)는 계속 체크하는 의미로 1)과는 달리 SOCK_EV_MARK_AS_CONNECT로 처리함
                        sendToDownWhenConnDisconn(context, Const.SOCK_EV_MARK_AS_CONNECT)
                    } else {
                        sendToDownWhenConnDisconn(context, Socket.EVENT_DISCONNECT)
                        KeyChain.set(context, Const.KC_DT_DISCONNECT, getCurDateTimeStr(true))
                    }
                    if (json.get("msg").asString == "connect") { //접속 로그를 위한 단순 구분 코드
                        val param = org.json.JSONObject()
                        param.put("device", Const.AOS)
                        param.put("work", "conn")
                        val screen = KeyChain.get(context, Const.KC_SCREEN_STATE) ?: ""
                        param.put("state", screen)
                        val strDtNow = getCurDateTimeStr(true)
                        val strDtDisconnect = KeyChain.get(context, Const.KC_DT_DISCONNECT) ?: ""
                        param.put("cdt", strDtNow)
                        param.put("udt", strDtDisconnect)
                        if (strDtDisconnect == "") {
                            param.put("dur", -1) //접속이 끊어진 적이 없음 (예: 최초 연결시)
                        } else { //duration(seconds) = 현재시각 - dt
                            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                            val date1 = dateFormat.parse(strDtDisconnect)
                            val date2 = dateFormat.parse(strDtNow)
                            var diff: Long = abs(date2.time - date1.time) / 1000 //초(seconds)
                            if (diff > 31536000) diff = 31536000 //1년 넘으면 1년으로 최대치 설정
                            param.put("dur", diff)
                        }
                        KeyChain.set(context, Const.KC_DT_DISCONNECT, "") //reset해야 로깅에 의미가 있음
                        HttpFuel.post(context, "/msngr/append_log", param.toString()).await() //로깅이므로 오류가 나도 넘어가도록 함
                    }
                    callback(json)
                } catch (e: Exception) {
                    sendToDownWhenConnDisconn(context, Socket.EVENT_DISCONNECT)
                    val jsonStr = """{ code : '${Const.RESULT_ERR}', msg : 'connectSockWithCallback\n${e.toString()}' }"""
                    callback(Gson().fromJson(jsonStr, JsonObject::class.java))
                }
            }
        }

        fun procRxMsg(context: Activity, callback: ((type: String, msg: String) -> Unit)? = null): Disposable {
            return RxToDown.subscribe<RxMsg>().subscribe {
                CoroutineScope(Dispatchers.Main).launch { //Do not remove this line since Dispatchers.Main covers Dispatchers.IO which already called.
                    try {
                        val param = it.data as org.json.JSONObject
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
                json1.put("dummy", "reserved") //just for reserving
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

        fun procConsoleMsg(context: Activity, msg: String, title: String) { //for webview
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
