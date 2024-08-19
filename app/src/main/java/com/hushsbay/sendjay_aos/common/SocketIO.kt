package com.hushsbay.sendjay_aos.common

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.hushsbay.sendjay_aos.ChatService
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.*

object SocketIO { //https://socketio.github.io/socket.io-client-java/initialization.html

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()) //Parent Job is cancelled 라는 오류 방지 위해 SupervisorJob으로 처리
    private const val DELAY = 10L //500L
    private const val SEC = 1000L //1500L

    var sock: Socket? = null
    lateinit var option: IO.Options

    operator fun invoke(uInfo: UserInfo, winid: String, userip: String) { //웹 설정과 동일 (common.js 참조)
        option = IO.Options().apply {
            forceNew = false //default=false //See 'disconnect_prev_sock' in pmessage.js (on server)
            reconnection = false
            //pwd, autokey_app 추가 => 예) ChatService.kt 리스타트시 SocketIO.invoke()되는데 Util.refreshTokenOrAutoLogin()처럼
            //토큰이 만기되면 아예 아이디/(암호와된)비번으로 로그인 처리하는 것으로 함 (모바일만 해당)
            //아래에서 token 다음에 userid가 와야 함 (순서 주의 => 그 아래 changeToken에서 처리 : 정규식 아닌 substring으로 구현)
            query = "token=${uInfo.token}&userid=${uInfo.userid}&pwd=${uInfo.pwd}&autokey_app=${uInfo.autokey_app}&userkey=${uInfo.userkey}&winid=${winid}&userip=${userip}"
        }
        sock = IO.socket(Const.URL_SOCK, option)
    }

    private fun changeToken(token: String) {
        if (sock!!.connected()) return //소켓이 연결된 상태에서 처리하면 안됨
        if (token == "") return
        var pos: Int? = option.query.indexOf("&userid=") //위 invoke() query 순서와 동일해야 함을 유의
        option.query = "token=${token}" + pos?.let { option.query.substring(it) }
        sock.apply { option = option } //sock = IO.socket(Const.URL_SOCK, option)으로 처리하면 안됨. sock은 살려놓고 option만 갱신
    }

    fun connect(context: Context, connManager: ConnectivityManager): Deferred<JsonObject> {
        return scope.async {
            try {
                var code = Const.RESULT_OK
                var msg = ""
                val nwCapa = connManager.getNetworkCapabilities(connManager.activeNetwork)
                if (nwCapa != null && nwCapa.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    if (ChatService.state == Const.ServiceState.LOGOUTED) {
                        code = Const.RESULT_ERR
                        msg = "로그아웃 상태입니다."
                    } else if (ChatService.state == Const.ServiceState.STOPPED) {
                        val intentNew = Intent(context, ChatService::class.java)
                        context.startForegroundService(intentNew)
                        val result = chkConnected().await()
                        if (result == null) {
                            code = Const.RESULT_ERR
                            msg = "Service started but socket connect timeout."
                        }
                    } else { //Const.ServiceState.RUNNING
                        if (sock == null) {
                            code = Const.RESULT_ERR
                            msg = "Socket not ready yet." //원래 사용자 입장에서는 이 msg가 표시되면 안됨
                        } else if (!sock!!.connected()) {
                            //##777 : 이 메소드는 Util.connectSockWithCallback()에서 실행 (ChatService.kt에서 데몬이 주기적으로 루핑중에 실행됨).
                            //예를 들어, 서버 다운시에는 네트워크가 살아 있어 바로 아래 Util.refreshTokenOrAutoLogin()가 호출되면서 HttpFuel의 타임아웃까지 기다렸다가 없어지므로
                            //타임아웃 기본이 약 15초정도라면 서버가 오랫동안 죽어 있으면 타임아웃된 호출말고 기다리고 있는 몇개의 호출이 서버 연결시 동시에 호출되는 현상이 발생함
                            //-> Util.refreshTokenOrAutoLogin()시 refreshToken()/login.js 호출과 연결후 connectSockWithCallback()에서 http로 로깅하는 작업이 해당됨
                            //만약 ChatService.kt에서 데몬이 3초 주기라면 HttpFuel의 타임아웃이 2초만 되어도 몇개씩 쌓이지는 않을 것임 (물론, 무한대로 쌓이지는 않지만 클라이언트가 많으면 부하가 큼)
                            //그런데, 이 refreshTokenOrAutoLogin()은 현재 서버 재시작시 아래 로깅횟수만큼 호출됨 (부하 이슈) : 사용하면 안됨
                            //-> 설명은 Util.refreshTokenOrAutoLogin() 참조하고 SocketIO.invoke()에 옵션 추가로 해결함
                            //Util.log("@@@@@@", "======="+ Util.getCurDateTimeStr(true))
                            //val json = Util.refreshTokenOrAutoLogin(context).await() //현재 서버죽고 위 로깅횟수만큼 서버 살고난 후 호출됨
                            val token = KeyChain.get(context, Const.KC_TOKEN) ?: ""
                            changeToken(token) //소켓이 연결된 상태에서 처리하면 안됨
                            sock!!.connect()
                            val result = chkConnected().await()
                            if (result == null) {
                                code = Const.RESULT_ERR
                                msg = "Unable to connect to socket server (socket)"
                            }
                        }
                    }
                } else {
                    code = Const.RESULT_ERR
                    msg = Const.NETWORK_UNAVAILABLE + "@@"
                } //if (msg != "") msg += "\n${Const.WAIT_FOR_RECONNECT}"; val jsonStr = """{ code : '$code', msg : '${Const.TITLE}: $msg' }"""
                val jsonStr = """{ code : '$code', msg : '$msg' }"""
                Gson().fromJson(jsonStr, JsonObject::class.java)
            } catch (e: Exception) {
                val jsonStr = """{ code : '${Const.RESULT_ERR}', msg : '${Const.TITLE}:SocketIO:connect: ${e.message}' }"""
                Gson().fromJson(jsonStr, JsonObject::class.java)
            }
        }
    }

    private fun chkConnected(): Deferred<String?> {
        return CoroutineScope(Dispatchers.Default).async {
            val result = withTimeoutOrNull(SEC) {// 말그대로 타임아웃까지 가면 null을 반환
                while (true) { //소켓 연결상태면 Done을 반환. 아니면 맨 아래 null 인 result를 반환
                    if (sock != null && sock!!.connected()) break
                    delay(DELAY)
                }
                "Done"
            }
            result
        }
    }

}