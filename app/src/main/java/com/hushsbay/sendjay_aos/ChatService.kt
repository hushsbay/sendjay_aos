package com.hushsbay.sendjay_aos

import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.os.IBinder
import android.os.PowerManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.hushsbay.sendjay_aos.common.Const
import com.hushsbay.sendjay_aos.common.HttpFuel
import com.hushsbay.sendjay_aos.common.KeyChain
import com.hushsbay.sendjay_aos.common.LogHelper
import com.hushsbay.sendjay_aos.common.NotiCenter
import com.hushsbay.sendjay_aos.common.RxToDown
import com.hushsbay.sendjay_aos.common.RxToRoom
import com.hushsbay.sendjay_aos.common.RxToUp
import com.hushsbay.sendjay_aos.common.SocketIO
import com.hushsbay.sendjay_aos.common.UserInfo
import com.hushsbay.sendjay_aos.common.Util
import com.hushsbay.sendjay_aos.data.RxEvent
import com.hushsbay.sendjay_aos.data.RxMsg
import io.reactivex.disposables.Disposable
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.log4j.Logger
import org.json.JSONObject
import java.util.*

class ChatService : Service() {

    //foregroundservice and notification icon => https://beehoneylife.tistory.com/5
    //https://www.spiria.com/en/blog/mobile-development/hiding-foreground-services-notifications-in-android/

    companion object {
        var state = Const.ServiceState.STOPPED
        var serviceIntent: Intent? = null //See MainActivity.kt
        var status_sock = Const.SockState.BEFORE_CONNECT
        var gapSecOnDualMode = "1000" //기본값이며 앱 구동시 서버에서의 설정값을 내려 받음
        //DualMode는 웹/모바일 모두 소켓연결일 때 이 갭(초)만큼 모바일에서 늦게 도착체크해서 한쪽에서 이미 읽었으면 다른 한쪽에서는 알림표시하지 않게 하기
    }

    private var SEC_DURING_DAEMON: Long = 3000 //try connecting every 3 second in case of disconnection
    //private var MAX_DURING_DAEMON: Long = 600000 //10분되면 주기적으로 실행 (토큰 갱신 주기 => 웹만 사용하고 여기서는 사용하지 않음) //지우지말고 참고로 두기
    //private var cnt_for_daemon: Long = 0 //지우지말고 참고로 두기
    private var SEC_DURING_RESTART = 3 //try restarting after 3 seconds (just once) when service killed (see another periodic trying with SimpleWorker.kt)

    private lateinit var logger: Logger
    private lateinit var screenReceiver: BroadcastReceiver
    private var pwrManager: PowerManager? = null
    private var connManager: ConnectivityManager? = null
    private var manager: NotificationManager?= null
    private var channel: NotificationChannel?= null
    private var disposable: Disposable? = null
    private var thread: Thread? = null
    private lateinit var uInfo: UserInfo

    private var shouldThreadStop = false
    private var stop_mobile = false

    private inner class mainTask : TimerTask() {
        override fun run() { //Timer().schedule(mainTask(), 1000)과 연계되는데 아래 오류로 막음
            manager!!.cancel(Const.NOTI_ID_FOREGROUND_SERVICE) //안먹힘
            manager!!.deleteNotificationChannel(Const.NOTICHANID_FOREGROUND) //앱도 같이 종료됨
            manager!!.shouldHideSilentStatusBarIcons()
            this.cancel()
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            status_sock = Const.SockState.BEFORE_CONNECT
            state = Const.ServiceState.RUNNING
            logger = LogHelper.getLogger(applicationContext, this::class.simpleName)
            connManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            pwrManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            screenReceiver = object : BroadcastReceiver() {
                override fun onReceive(contxt: Context?, intent: Intent?) { //when (intent?.action) {
                    intent?.let {
                        if (it.action == Intent.ACTION_SCREEN_ON) {
                            KeyChain.set(applicationContext, Const.KC_SCREEN_STATE, "on")
                            Util.connectSockWithCallback(applicationContext, connManager!!) //SocketIO.connect()
                        } else if (it.action == Intent.ACTION_SCREEN_OFF) {
                            KeyChain.set(applicationContext, Const.KC_SCREEN_STATE, "off")
                        }
                    }
                }
            }
            val filter1 = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            registerReceiver(screenReceiver, filter1)
            uInfo = UserInfo(applicationContext) //KeyChain Get
            NotiCenter(applicationContext, packageName) //NotiCenter.invoke() //see MainActivity.kt also
            val winid = KeyChain.get(applicationContext, Const.KC_WINID)
            val userip = KeyChain.get(applicationContext, Const.KC_USERIP)
            SocketIO(uInfo, winid!!, userip!!) //kotlin invoke method : SocketIO.invoke()
            startForegroundWithNotification()
            initDeamon()
        } catch (e: Exception) {
            logger.error("onCreate: ${e.toString()}")
            e.printStackTrace()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { //onCreate -> onStartCommand
        serviceIntent = intent
        return START_STICKY //https://stackoverflow.com/questions/25716864/why-is-my-service-started-twice
        //return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null;
    }

    override fun onTaskRemoved(rootIntent: Intent?) { //See android:stopWithTask="false" in AndroidManifest.xml
        super.onTaskRemoved(rootIntent)
        stopSelf() //and restartChatService()
    }

    //Service is destroyed when its explicitly stopped, or when the system needs resources.
    //To explicitly stop a service, call stopService from any Context, or stopSelf from the Service.
    override fun onDestroy() {
        super.onDestroy()
        state = Const.ServiceState.STOPPED
        restartChatService()
        unregisterReceiver(screenReceiver)
    }

    private fun startForegroundWithNotification() {
        val logTitle = object{}.javaClass.enclosingMethod?.name!!
        try {
            Util.log("startForegroundWithNotification")
            shouldThreadStop = false
            manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            channel = NotificationChannel(Const.NOTICHANID_FOREGROUND, Const.NOTICHANID_FOREGROUND, NotificationManager.IMPORTANCE_LOW)
            channel!!.setShowBadge(false) //앱을 완전히 제거후 새로 시작해야 적용됨
            manager!!.createNotificationChannel(channel!!) //여기 Noti는 진짜 알림을 위한 것이 아니고 foreground service 구동을 위한 것임
            val builder = NotificationCompat.Builder(this, Const.NOTICHANID_FOREGROUND)
            builder.setSmallIcon(R.drawable.notiforservice) //If not, 2 lines of verbose explanation shows.
            val style = NotificationCompat.BigTextStyle()
            style.bigText(null)
            style.setBigContentTitle(null)
            style.setSummaryText(null)
            builder.setStyle(style)
            builder.setContentTitle("Sendjay Messaging")
            builder.setContentText(null)
            builder.setOngoing(false)
            builder.setWhen(0)
            builder.setShowWhen(false)
            builder.setAutoCancel(true) //안먹힘
            val notificationIntent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
            builder.setContentIntent(pendingIntent)
            val notification = builder.build()
            //서비스 호출 이후 대략 5초안에 startForeground() 호출하지 않으면 오류 발생. id should not be zero
            //3번째 인자는 AndroidManifest.xml의 user-permission과 service태그내 type 설정이 없으면 오류 발생
            startForeground(Const.NOTI_ID_FOREGROUND_SERVICE, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            //Timer().schedule(mainTask(), 10000) //or postDelayed
        } catch (e: Exception) {
            logger.error("$logTitle: ${e.toString()}")
            Util.log(logTitle, e.toString())
            e.printStackTrace()
            Util.showRxMsgInApp(Const.SOCK_EV_ALERT, "$logTitle: ${e.toString()}")
        }
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun initDeamon() {
        val logTitle = object{}.javaClass.enclosingMethod?.name!!
        try { //SimpleWorker 작동 결과
            //1. 스크린오프 상태로 간 경우 서비스가 제한되는데 이 경우도 서비스를 재시작해야 함. SimpleWorker가 주기적으로 실행되어 서비스가 살아나 그동안 밀렸던 톡 도착 노티가 표시됨
            //2. ChatService를 강제로 죽이고 스크린오프 상태로 하고 배터리 설정도 기본으로 한 경우도 노티 문제없음
            //문제는 SimpleWorker가 최소 주기가 15분이라서 너무 길다는 것임. 그렇다고, FCM을 적용하는 것은 노력이 많이 드는 이슈임. SimpleWorker를 여러개 운영해 1분에 하나씩 실행시키는 것은 힘들어 보임
            /*따라서, SimpleWorker를 막고 아래 AlarmManager를 사용해 처리함
            val workManager = WorkManager.getInstance(applicationContext) //https://tristan91.tistory.com/480
            workManager.cancelAllWork()
            val periodicRequest = PeriodicWorkRequest.Builder(SimpleWorker::class.java, 15, TimeUnit.MINUTES).build() //minimum 15 minutes
            workManager.enqueue(periodicRequest)*/
            //아래와 같이 AlarmManager의 setExactAndAllowWhileIdle()를 사용함 (최소 1분 간격으로 Doze(Idle)모드에서도 정확도를 보임)
            //https://velog.io/@thevlakk/Android-AlarmManager-%ED%8C%8C%ED%97%A4%EC%B9%98%EA%B8%B0-1
            //권한 허용 필요 : https://diordna91.medium.com/android-12-%EC%A0%95%ED%99%95%ED%95%9C-%EC%95%8C%EB%9E%8C-%EA%B6%8C%ED%95%9C-d92f878de695
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val nextIntent = Intent(applicationContext, AlarmReceiver::class.java)
            nextIntent.action = "one_minute_check"
            val pendingIntent = PendingIntent.getBroadcast(applicationContext,1, nextIntent, PendingIntent.FLAG_IMMUTABLE) // or PendingIntent.FLAG_UPDATE_CURRENT)
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = System.currentTimeMillis()
            calendar.add(Calendar.SECOND, 60)
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent) //권한 설정 필요
            ///////////////////////////////////////////////////////////////////////////////////
            val r: Runnable = Daemon() //데몬만으로도 불안해서 위 AlarmReceiver를 추가로 사용하는 것임
            thread = Thread(r)
            thread!!.setDaemon(true)
            thread!!.start()
            procSocketOn()
            procSocketEmit()
        } catch (e: Exception) {
            logger.error("$logTitle: ${e.toString()}")
            Util.log(logTitle, e.toString())
            e.printStackTrace()
            Util.showRxMsgInApp(Const.SOCK_EV_TOAST, "$logTitle: ${e.toString()}")
        }
     }

    private fun restartChatService() {
        val logTitle = object{}.javaClass.enclosingMethod?.name!!
        try {
            if (!thread!!.isInterrupted || thread!!.isAlive) {
                thread!!.interrupt()
                thread = null
            }
            shouldThreadStop = true
            disposable?.dispose()
            if (SocketIO.sock != null && SocketIO.sock!!.connected()) {
                SocketIO.sock!!.disconnect()
                KeyChain.set(applicationContext, Const.KC_DT_DISCONNECT, Util.getCurDateTimeStr(true)) //Util.connectSockWithCallback() 참조
            }
            if (MainActivity.stopServiceByLogout || stop_mobile) {
                serviceIntent = null
                state = Const.ServiceState.LOGOUTED
                stop_mobile = false
                return
            }
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = System.currentTimeMillis()
            calendar.add(Calendar.SECOND, SEC_DURING_RESTART)
            val intent = Intent(this, AlarmReceiver::class.java)
            intent.action = "restart_service"
            val sender = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager[AlarmManager.RTC_WAKEUP, calendar.timeInMillis] = sender
        } catch (e: Exception) {
            logger.error("$logTitle: ${e.toString()}")
            Util.log(logTitle, e.toString())
            e.printStackTrace()
            Util.showRxMsgInApp(Const.SOCK_EV_TOAST, "$logTitle: ${e.toString()}")
        }
    }

    private fun setRoomInfo(json: JSONObject, logTitle:  String) { //rest->socket 방식으로 변경. socket/get_roominfo.js 설명 참조
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val data = json.getJSONObject("data")
                val roomid = data.getString("roomid")
                val param = org.json.JSONObject()
                param.put("roomid", roomid)
                RxToUp.post(RxEvent(Const.SOCK_EV_GET_ROOMINFO, param)) //, returnTo, returnToAnother))
                Util.log(logTitle + ": " + Const.SOCK_EV_GET_ROOMINFO, "old==" + roomid)
            } catch (e: Exception) {
                logger.error("$logTitle: ${e.toString()}")
                Util.log(logTitle, e.toString())
                e.printStackTrace()
                Util.showRxMsgInApp(Const.SOCK_EV_ALERT, "$logTitle: ${e.toString()}")
            }
        }
    }

    private fun procSocketEmit() {
        val logTitle = object{}.javaClass.enclosingMethod?.name!!
        disposable?.dispose()
        disposable = RxToUp.subscribe<RxEvent>().subscribe {
            try {
                val json = JSONObject()
                val evt = it.ev
                val dataStr = it.data.toString()
                json.put("ev", evt)
                json.putOpt("data", it.data)
                json.put("returnTo", it.returnTo ?: "parent")
                json.put("returnToAnother", it.returnToAnother)
                if (evt == "chk_alive" || evt == "chk_typing" || evt == "chk_roomfocus") {
                    //타이머로 돌아가는 이벤트들 로그 막음 (단순히 로그캣에서 보는데 복잡하기 때문임)
                } else {
                    Util.log("$logTitle", it.toString())
                }
                val procMsg = it.procMsg //모바일 전용 (웹에는 없음). 아래 buffering하고도 일부는 관련있는 파라미터임 (토스트 뿌리면 true 안뿌리게 하려면 false가 넘어와야 함)
                //아래는 소켓 끊어질 때 버퍼링 관련임. common.js의 hush.sock.sendVolatile() 설명 참조
                //socket.io의 기본 설정은 소켓이 끊어지더라도 버퍼에 두고 있다가 재연결시 내보내는 것인데 Android socket.io 라이브러리에서 volatile을 지원하는 것을 아직 찾지 못해, 사용하지 않으려 함
                //따라서, 앱이든 웹이든 버퍼에 저장하지 않게 하기 위해 아예 소켓이 연결되어 있지 않으면 전송을 멈추기로 함
                Util.connectSockWithCallback(applicationContext, connManager!!) { //SocketIO.connect()
                    if (it.get("code").asString != Const.RESULT_OK) {
                        if (evt == Const.SOCK_EV_SEND_MSG) {
                            val gson = Gson().fromJson(dataStr, JsonObject::class.java)
                            val type = gson.get("type").asString
                            if (type == "leave" || type == "invite" || type == "notice") {
                                //버퍼링. 소켓이 끊어지더라도 일단 소켓객체에 전달해 emit => chat.html의 procSendAndAppend() 설명 참조
                            } else { //talk, flink
                                if (procMsg == true) Toast.makeText(applicationContext, Const.TITLE + ": " + it.get("msg").asString, Toast.LENGTH_LONG).show()
                                return@connectSockWithCallback
                            }
                        } else {
                            if (procMsg == true) Toast.makeText(applicationContext, Const.TITLE + ": " + it.get("msg").asString, Toast.LENGTH_LONG).show()
                            return@connectSockWithCallback
                        }
                    } //서버 다운시 connectSockWithCallback()내 SocketIO.connect() 결과 Unable to connect~라는 msg 위에서 찍으면 잘 나옴. 그 때 SocketIO.sock은 null 아님
                    SocketIO.sock!!.emit(Const.SOCK_EV_COMMON, json)
                }
            } catch (e: Exception) {
                logger.error("$logTitle: ${e.toString()}")
                Util.log(logTitle, e.toString())
                e.printStackTrace()
                Util.showRxMsgInApp(Const.SOCK_EV_TOAST, "$logTitle: ${e.toString()}")
            }
        }
    }

    private fun procSocketOn() {
        val logTitle = object{}.javaClass.enclosingMethod?.name!!
        SocketIO.sock!!.off(Socket.EVENT_CONNECT).on(Socket.EVENT_CONNECT) {
            try {
                Util.log(logTitle, Socket.EVENT_CONNECT)
                if (status_sock == Const.SockState.FIRST_DISCONNECTED) { //html에서 [hush.cons.sock_ev_connect] 참조 요망
                    status_sock = Const.SockState.RECONNECTED //Socket.EVENT_CONNECT occurred many times at a moment. (socket.io 초기버전 이야기?!)
                    Util.sendToDownWhenConnDisconn(applicationContext, Socket.EVENT_CONNECT)
                }
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val roomidForService = KeyChain.get(applicationContext, Const.KC_ROOMID_FOR_CHATSERVICE)!!
                        val param = org.json.JSONObject()
                        param.put("type", "R") //모바일에서만 호출
                        val json = HttpFuel.post(applicationContext, "/msngr/qry_unread", param.toString()).await()
                        if (json.get("code").asString == Const.RESULT_OK) {
                            val list = json.getAsJsonArray("list")
                            if (list.size() == 0) return@launch
                            for (i in 0 until list.size()) {
                                val item = list[i].asJsonObject
                                val roomid = item.get("ROOMID").asString
                                val addinfo = item.get("ADDINFO").asString //for mobile only
                                val arr = addinfo.split(Const.DELI)
                                val msgid = arr[0]
                                val cdt = arr[1]
                                val type = arr[2]
                                val body = arr[3]
                                val body1 = "안읽은톡) " + Util.getTalkBodyCustom(type, body)
                                val param = org.json.JSONObject()
                                param.put("msgid", msgid)
                                param.put("body", body1)
                                param.put("type", type)
                                param.put("cdt", cdt)
                                param.put("senderid","dummy") //서버의 qry_unread.js where 조건 보면 어차피 내가 보낸 건 빼고 가져옴 (=내가 보낸 건 없음)
                                var needNoti = NotiCenter.needNoti(applicationContext, uInfo, roomid, roomidForService, param)
                                if (!needNoti) return@launch
                                NotiCenter.notiToRoom(applicationContext, uInfo, roomid, param,false)
                            }
                        } else if (HttpFuel.isNetworkUnstableMsg(json)) {
                            Util.showRxMsgInApp(Const.SOCK_EV_TOAST, Const.NETWORK_UNSTABLE+"^^")
                        } else {
                            Util.showRxMsgInApp(Const.SOCK_EV_ALERT, "$logTitle: ${json.get("msg").asString}") //크리티컬하지 않으므로 막아도 무방
                        }
                    } catch (e: Exception) {
                        logger.error("$logTitle: EVENT_CONNECT ${e.toString()}")
                        e.printStackTrace()
                        Util.showRxMsgInApp(Const.SOCK_EV_ALERT, "$logTitle:qry_unread: ${e.toString()}") //크리티컬하지 않으므로 막아도 무방
                    }
                }
            } catch (e1: Exception) {
                logger.error("$logTitle: EVENT_CONNECT1 ${e1.toString()}")
                Util.log(logTitle, e1.toString())
                e1.printStackTrace()
                Util.showRxMsgInApp(Const.SOCK_EV_TOAST, "$logTitle: ${e1.toString()}")
            }
        }.off(Socket.EVENT_DISCONNECT).on(Socket.EVENT_DISCONNECT) {
            try { //설명은 class Daemon의 ###10 참조
                Util.log(logTitle, Socket.EVENT_DISCONNECT)
                if (status_sock == Const.SockState.BEFORE_CONNECT) status_sock = Const.SockState.FIRST_DISCONNECTED
                Util.sendToDownWhenConnDisconn(applicationContext, Socket.EVENT_DISCONNECT)
                KeyChain.set(applicationContext, Const.KC_DT_DISCONNECT, Util.getCurDateTimeStr(true)) //Util.connectSockWithCallback() 참조
            } catch (e: Exception) {
                logger.error("$logTitle: EVENT_DISCONNECT ${e.toString()}")
                Util.log(logTitle, e.toString())
                e.printStackTrace()
                Util.showRxMsgInApp(Const.SOCK_EV_TOAST, "$logTitle: ${e.toString()}")
            }
        }.off(Const.SOCK_EV_ALERT).on(Const.SOCK_EV_ALERT) {
            try {
                if (it[0] is String) { //check type (java.lang.String cannot be cast to org.json.JSONObject)
                    Util.log(logTitle + ": " + Const.SOCK_EV_ALERT, it[0].toString() + ": " + it[1].toString())
                } else {
                    val jsonObj = it[0] as JSONObject
                    Util.log(logTitle, jsonObj.toString())
                    RxToDown.post(RxMsg(Const.SOCK_EV_ALERT, jsonObj))
                }
            } catch (e: Exception) {
                logger.error("$logTitle: SOCK_EV_ALERT ${e.toString()}")
                Util.log(logTitle + ": " + Const.SOCK_EV_ALERT, e.toString())
                e.printStackTrace()
            }
        }.off(Const.SOCK_EV_TOAST).on(Const.SOCK_EV_TOAST) {
            try {
                if (it[0] is String) { //check type (java.lang.String cannot be cast to org.json.JSONObject)
                    Util.log(logTitle + ": " + Const.SOCK_EV_TOAST, it[0].toString() + ": " + it[1].toString())
                } else {
                    val jsonObj = it[0] as JSONObject
                    RxToDown.post(RxMsg(Const.SOCK_EV_TOAST, jsonObj))
                }
            } catch (e: Exception) {
                logger.error("$logTitle: SOCK_EV_TOAST ${e.toString()}")
                Util.log(logTitle + ": " + Const.SOCK_EV_TOAST, e.toString())
                e.printStackTrace()
            }
        }.off(Const.SOCK_EV_COMMON).on(Const.SOCK_EV_COMMON) { it ->
            try { //대부분의 소켓 이벤트는 여기로 옴
                val json = it[0] as JSONObject
                val jsonStr = it[0].toString() //it.get(0).toString()
                val gson = Gson().fromJson(jsonStr, JsonObject::class.java)
                val ev = gson.get("ev").asString
                val returnTo = gson.get("returnTo").asString
                val returnToAnother = gson.get("returnToAnother")?.asString
                if (ev != "chk_alive" && ev != "chk_typing") Util.log("$logTitle", jsonStr, it[0].javaClass.kotlin.qualifiedName!!)
                //아래 returnToAnother는 org.json.JSONObject로 가져오면 try catch 필요하게 되어 번거로움 (gson으로 가져옴)
                //json.get("data")가 아닌 넘어온 객체 전체인 json임을 유의. json으로 넘기지 않고 gson.toString()에서 변환한 json시 처리가 더 불편함
                RxToDown.post(RxEvent(ev, json, returnTo, returnToAnother))
                val roomidForService = KeyChain.get(applicationContext, Const.KC_ROOMID_FOR_CHATSERVICE)!!
                if (returnTo != "" && returnTo == roomidForService) {
                    RxToRoom.post(RxEvent(ev, json, returnTo, returnToAnother))
                } else if (returnTo == "all") {
                    RxToRoom.post(RxEvent(ev, json, returnTo, returnToAnother))
                } //아래 몇가지는 모바일에서 필요한 처리이므로 구현해야 함
                if (ev == Const.SOCK_EV_SEND_MSG) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val data = json.getJSONObject("data")
                            var needNoti = NotiCenter.needNoti(applicationContext, uInfo, returnTo, roomidForService, data)
                            if (!needNoti) return@launch
                            NotiCenter.notiToRoom(applicationContext, uInfo, returnTo, data, true)
                        } catch (e: Exception) {
                            //do nothing
                        }
                    }
                } else if (ev == Const.SOCK_EV_READ_MSG) {
                    val data = json.getJSONObject("data")
                    val type = data.getString("type")
                    if (type == "updateall" || type == "update") {
                        val userid = data.getString("userid")
                        if (userid == uInfo.userid) {
                            NotiCenter.mapRoomid[returnTo]?.let {
                                NotiCenter.manager!!.cancel(it)
                                NotiCenter.mapRoomid.remove(returnTo)
                            }
                            if (NotiCenter.mapRoomid.isEmpty()) NotiCenter.manager!!.cancel(Const.NOTI_ID_SUMMARY)
                        }
                    }
                } else if (ev == Const.SOCK_EV_SET_ENV) {
                    val data = json.getJSONObject("data")
                    val kind = data.getString("kind")
                    if (kind == "userinfo") {
                        val userid = data.getString("userid")
                        if (userid == uInfo.userid) {
                            KeyChain.set(applicationContext, Const.KC_NOTI_OFF, data.getString("notioff"))
                            KeyChain.set(applicationContext, Const.KC_BODY_OFF, data.getString("bodyoff"))
                            KeyChain.set(applicationContext, Const.KC_SENDER_OFF, data.getString("senderoff"))
                            KeyChain.set(applicationContext, Const.KC_TM_FR, data.getString("fr"))
                            KeyChain.set(applicationContext, Const.KC_TM_TO, data.getString("to"))
                            uInfo = UserInfo(applicationContext) //org.json not gson //KeyChain Get
                        }
                    } else if (kind == "noti") {
                        setRoomInfo(json, ev)
                    }
                } else if (ev == Const.SOCK_EV_RENAME_ROOM) {
                    setRoomInfo(json, ev)
                } else if (ev == Const.SOCK_EV_CHK_ROOMFOCUS) {
                    val screenState = KeyChain.get(applicationContext, Const.KC_SCREEN_STATE) ?: ""
                    val focusedRoomid = if (screenState == "on" && MainActivity.isOnTop && roomidForService != "") {
                        roomidForService
                    } else {
                        ""
                    }
                    RxToUp.post(RxEvent(Const.SOCK_EV_CHK_ROOMFOCUS, JSONObject().put("focusedRoomid", focusedRoomid), "parent"))
                } else if (ev == Const.SOCK_EV_STOP_MOBILE) {
                    val data = json.getJSONObject("data")
                    val userid = data.getString("userid")
                    if (userid == uInfo.userid) { //Util.log(userid, uInfo.userid)
                        Util.clearKeyChainForLogout(applicationContext)
                        stop_mobile = true
                        stopSelf()
                    }
                } else if (ev == Const.SOCK_EV_GET_ROOMINFO) {
                    val jsonRI = json.getJSONObject("data")
                    val gsonRI = Gson().fromJson(jsonRI.toString(), JsonObject::class.java)
                    NotiCenter.getRoomInfo(gsonRI, jsonRI.getString("roomid"))
                    Util.log(logTitle + ": " + Const.SOCK_EV_GET_ROOMINFO, jsonRI.toString())
                }
            } catch (e: Exception) {
                logger.error("$logTitle: SOCK_EV_COMMON ${e.toString()}")
                Util.log(logTitle + ": " + Const.SOCK_EV_COMMON, e.toString())
                e.printStackTrace()
                Util.showRxMsgInApp(Const.SOCK_EV_ALERT, "$logTitle: ${e.toString()}")
            }
        }
    }

    inner class Daemon : Runnable { //thread!!.state => https://developer.android.com/reference/kotlin/java/lang/Thread.State
        override fun run() { //thread.stop() deprecated. thread.interrupt() used instead.
            val logTitle = object{}.javaClass.enclosingMethod?.name!!
            try {
                while (!shouldThreadStop) {
                    synchronized(this) {
                        try {
                            //###10 소켓연결이 끊어지면 여기가 아닌 procSocketOn()의 DISCONNECT 이벤트에서 제일 먼저 인지되는데
                            //거기서는 단지, 연결이 끊어진 걸 앱과 웹뷰에 전달하는 정도가 전부임. 다시 재연결하는 노력은 여기서 진행됨
                            //소켓연결이 끊어지는 건 1) ChatService가 살아 있으면서 단순히 통신 이상이거나 2) ChatService가 살아 있으면서 서버가 다운되거나
                            //3) ChatService가 (사용자에 의해) 강제 종료되면서 끊어지는 경우가 있는데 이 모든 경우에 대해 다시 원상복구해야 함
                            /*val screenState = KeyChain.get(applicationContext, Const.KC_SCREEN_STATE) ?: ""
                            Util.log(logTitle, "socket_connected : ${SocketIO.sock!!.connected()} / screen : ${screenState}" )*/
                            val autoLogin = KeyChain.get(applicationContext, Const.KC_AUTOLOGIN) ?: ""
                            if (autoLogin == "Y") Util.connectSockWithCallback(applicationContext,connManager!!)
                            /* 지우지 말고 참조로 두기
                            if (cnt_for_daemon >= MAX_DURING_DAEMON) {
                                cnt_for_daemon = 0
                                CoroutineScope(Dispatchers.IO).launch {
                                    try {
                                        //val json = HttpFuel.post(applicationContext,"/auth/refresh_token", param.toString()).await()
                                        //여기서 rest 호출시 대기모드나 수면모드에서 호출이 block되므로 의미없게 됨. 나중에 cnt_for_daemon값으로 쓸 일이 있을지 몰라 그냥 두기로 함
                                    } catch (e: Exception) {
                                        //Util.log("refresh_token", e.toString())
                                    }
                                }
                            } else {
                                cnt_for_daemon += SEC_DURING_DAEMON
                            }*/
                        } catch (e: InterruptedException) {
                            logger.error("$logTitle: e ${e.toString()}")
                            Util.log(logTitle, "thread interrupted")
                        } catch (e1: Exception) {
                            logger.error("$logTitle: e1 ${e1.toString()}")
                            Util.log(logTitle, "e1 ${e1.toString()}")
                        } finally {
                            try {
                                Thread.sleep(SEC_DURING_DAEMON)
                            } catch (ex11: InterruptedException) {
                                logger.error("$logTitle: ex11 ${ex11.toString()}")
                                Util.log(logTitle, "${ex11.toString()} thread interrupted finally")
                            } catch (ex12: Exception) {
                                logger.error("$logTitle: ex12 ${ex12.toString()}")
                                Util.log(logTitle, "${ex12.toString()} finally")
                            }
                        }
                    }
                }
            } catch (ex: InterruptedException) {
                logger.error("$logTitle: ex ${ex.toString()}")
                Util.log(logTitle, "${ex.toString()} thread interrupted last finally")
            } catch (ex1: Exception) {
                logger.error("$logTitle: ex1 ${ex1.toString()}")
                Util.log(logTitle, "${ex1.toString()} last finally")
            }
        }
    }

}