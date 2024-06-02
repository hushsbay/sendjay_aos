package com.hushsbay.sendjay_aos

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.hushsbay.sendjay_aos.common.Const
import com.hushsbay.sendjay_aos.common.UserInfo
import com.hushsbay.sendjay_aos.common.Util
import io.reactivex.disposables.Disposable
import org.apache.log4j.Logger
import java.util.*

class DummyService : Service() {

    //foregroundservice and notification icon => https://beehoneylife.tistory.com/5
    //https://www.spiria.com/en/blog/mobile-development/hiding-foreground-services-notifications-in-android/

//    companion object {
//        var state = Const.ServiceState.STOPPED //See FcmReceiver.kt
        var serviceIntent: Intent? = null //See MainActivity.kt
//        var status_sock = Const.SockState.BEFORE_CONNECT
//        var curState_sock = false
//        //var isBeingSockChecked = false
//        var gapScreenOffOnDualMode = "10000"
//        var gapScreenOnOnDualMode = "3000"
//    }

    private var SEC_DURING_DAEMON: Long = 3000 //try connecting every 3 second in case of disconnection
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
    private var cut_mobile = false

    private inner class mainTask : TimerTask() { //Timer().schedule(mainTask(), 1000)과 연계되는데 아래 오류로 막음
        override fun run() {
            Util.log("stopForeground", "stopForeground")
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
            stopSelf()
            this.cancel()
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            connManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            pwrManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            startForegroundWithNotification()
            val intentNew = Intent(applicationContext, ChatService_Bg::class.java)
            startService(intentNew)
        } catch (e: Exception) {
            logger.error("onCreate: ${e.toString()}")
            e.printStackTrace()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { //onCreate -> onStartCommand
        serviceIntent = intent
        return START_NOT_STICKY //https://stackoverflow.com/questions/25716864/why-is-my-service-started-twice
        //return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null;
    }

    override fun onTaskRemoved(rootIntent: Intent?) { //See android:stopWithTask="false" in AndroidManifest.xml
        super.onTaskRemoved(rootIntent)
        Util.log("@@@@@", "restartChatServiceAAA")
        //stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf() //and restartChatService()
    }

    //Service is destroyed when its explicitly stopped, or when the system needs resources.
    //To explicitly stop a service, call stopService from any Context, or stopSelf from the Service.
    override fun onDestroy() {
        super.onDestroy()
    }

    private fun startForegroundWithNotification() {
        val logTitle = object{}.javaClass.enclosingMethod?.name!!
        try {
            Util.log("startForegroundWithNotification")
            shouldThreadStop = false
            //var manager: NotificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            //var channel: NotificationChannel = NotificationChannel(Const.NOTICHANID_FOREGROUND, Const.NOTICHANID_FOREGROUND, NotificationManager.IMPORTANCE_LOW)
            channel = NotificationChannel(Const.NOTICHANID_FOREGROUND, Const.NOTICHANID_FOREGROUND, NotificationManager.IMPORTANCE_LOW)
            manager!!.createNotificationChannel(channel!!) //여기 Noti는 진짜 알림을 위한 것이 아니고 foreground service 구동을 위한 것임
            val builder = NotificationCompat.Builder(this, Const.NOTICHANID_FOREGROUND)
            builder.setSmallIcon(R.mipmap.ic_launcher) //If not, 2 lines of verbose explanation shows.
            val style = NotificationCompat.BigTextStyle()
            style.bigText(null)
            style.setBigContentTitle(null)
            style.setSummaryText(null)
            builder.setStyle(style)
            builder.setContentTitle("좌우로 드래그하면 이 알림을 제거합니다.")
            builder.setContentText(null)
            builder.setOngoing(true)
            builder.setWhen(0)
            builder.setShowWhen(false)
            val notificationIntent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
            builder.setContentIntent(pendingIntent)
            val notification = builder.build()
            //startForegroundService() 호출 이후 대략 5초안에 startForeground() 호출하지 않으면 오류 발생. id should not be zero
            //3번째 인자는 AndroidManifest.xml의 user-permission과 service태그내 type 설정이 없으면 오류 발생
            startForeground(Const.NOTI_ID_FOREGROUND_SERVICE, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            Timer().schedule(mainTask(), 1000) //or postDelayed
        } catch (e: Exception) {
            logger.error("$logTitle: ${e.toString()}")
            Util.log(logTitle, e.toString())
            e.printStackTrace()
            //RxToDown.post(RxMsg(Const.SOCK_EV_ALERT, JSONObject().put("msg", "$logTitle: ${e.toString()}")))
            Util.showRxMsgInApp(Const.SOCK_EV_ALERT, "$logTitle: ${e.toString()}")
        }
    }






}