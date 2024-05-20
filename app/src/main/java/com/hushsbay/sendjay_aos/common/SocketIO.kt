package com.hushsbay.sendjay_aos.common

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.hushsbay.sendjay_aos.ChatService
import com.hushsbay.sendjay_aos.common.Const
import com.hushsbay.sendjay_aos.common.UserInfo
import com.hushsbay.sendjay_aos.common.Util
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.*

object SocketIO { //https://socketio.github.io/socket.io-client-java/initialization.html

    var sock: Socket? = null

    //SupervisorJob is used to prevent Error (Parent Job is cancelled)
    //Parent Job is cancelled 라는 오류 방지를 위해 SupervisorJob으로 처리
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private const val DELAY = 500L
    private const val SEC = 1500L

    operator fun invoke(context: Context, uInfo: UserInfo, winid: String, userip: String) {
        val option: IO.Options = IO.Options().apply {
            forceNew = false //default=false //See 'disconnect_prev_sock' in pmessage.js (on server)
            reconnection = false
            query = "token=${uInfo.token}&userid=${uInfo.userid}&userkey=${uInfo.userkey}&winid=${winid}&userip=${userip}"
        }
        //sock = IO.socket(KeyChain.get(context, Const.KC_MODE_SOCK).toString(), option)
        sock = IO.socket(Const.URL_SOCK, option)
    }

    //예) procSocketEmit() in ChatService.kt : 서버로 전송시 소켓연결에 문제가 없으면 OK, 그게 아니면 문제해결하고 처리하는데 그래도 안되면 문제있다고 Return하는 것임
    fun connect(context: Context, connManager: ConnectivityManager): Deferred<JsonObject> {
        return scope.async {
            try {
                var code = Const.RESULT_OK
                var msg = ""
                val nwCapa = connManager.getNetworkCapabilities(connManager.activeNetwork)
                if (nwCapa != null && nwCapa.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    if (ChatService.state == Const.ServiceState.LOGOUTED) {
                        code = Const.RESULT_ERR
                        msg = "App logout."
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
                            SocketIO.sock!!.connect() //cannot connect on doze mode
                            val result = chkConnected().await()
                            if (result == null) {
                                code = Const.RESULT_ERR
                                msg = "Unable to connect to socket server."
                            } else {
                                msg = "connect" //접속 로그를 위한 단순 구분 코드
                            }
                        }
                    }
                } else {
                    code = Const.RESULT_ERR
                    msg = "Network not available."
                }
                //if (msg != "") msg += "\n${Const.WAIT_FOR_RECONNECT}"
                val jsonStr = """{ code : '$code', msg : '${Const.TITLE}: $msg' }"""
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
                    delay(DELAY) //사실, chkConnected는 데몬안에서 한번만 체크하고 넘어가도 상관없겠지만
                } //데몬 말고 루프돌면서 체크하고자 할 경우도 고려해 SEC동안 DELAY 사용해 체크해 봄
                "Done"
            }
            result
        }
    }

}