package com.hushsbay.sendjay_aos

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.hushsbay.sendjay_aos.common.Const
import com.hushsbay.sendjay_aos.common.SocketIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChatService : Service() {

    //foregroundservice and notification icon => https://beehoneylife.tistory.com/5
    //https://www.spiria.com/en/blog/mobile-development/hiding-foreground-services-notifications-in-android/

    private var SEC_DURING_DAEMON: Long = 5000 //try connecting every 5 second only in case of disconnection
    private var SEC_DURING_RESTART = 5 //try restarting after 5 seconds (just once) when service killed (see another periodic trying with SimpleWorker.kt)

    companion object {
        var state = Const.ServiceState.STOPPED //See FcmReceiver.kt
        var serviceIntent: Intent? = null //See MainActivity.kt
        var status_sock = Const.SockState.BEFORE_CONNECT
        var curState_sock = false
        var isBeingSockChecked = false
        var gapScreenOffOnDualMode = "10000"
        var gapScreenOnOnDualMode = "3000"
    }

    private var pwrManager: PowerManager? = null
    private var connManager: ConnectivityManager? = null
    //private var disposable: Disposable? = null

    private var thread: Thread? = null
    private var shouldThreadStop = false

    override fun onCreate() {
        super.onCreate()
        try {
            status_sock = Const.SockState.BEFORE_CONNECT
            state = Const.ServiceState.RUNNING
            //logger = LogHelper.getLogger(applicationContext, this::class.simpleName)
            //NotiCenter(applicationContext, packageName) //kotlin invoke method : NotiCenter.invoke() //see MainActivity.kt also
            connManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            pwrManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            SocketIO(applicationContext) //kotlin invoke method : SocketIO.invoke()
            startForegroundWithNotification()
            initDeamon()
        } catch (e: Exception) {
            Log.i("onCreate", e.toString())
            e.printStackTrace()
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null; //Return the communication channel to the service ???!!!
    }

    override fun onTaskRemoved(rootIntent: Intent?) { //See android:stopWithTask="false" in AndroidManifest.xml
        super.onTaskRemoved(rootIntent)
        stopSelf() //and restartChatService()
    }

    override fun onDestroy() {
        super.onDestroy()
        state = Const.ServiceState.STOPPED
        Log.i("###############", "onDestroy")
        restartChatService()
        //unregisterReceiver(screenReceiver)
    }

    private fun restartChatService() {
        val logTitle = object{}.javaClass.enclosingMethod?.name!!
        try {
            Log.i("###############", "restartChatService0")
            if (!thread!!.isInterrupted || thread!!.isAlive) thread!!.interrupt()
            shouldThreadStop = true
            //disposable?.dispose()
            if (SocketIO.sock != null && SocketIO.sock!!.connected()) SocketIO.sock!!.disconnect()
            if (MainActivity.stopServiceByLogout) {
                serviceIntent = null
                state = Const.ServiceState.LOGOUTED
                Log.i("###############", "restartChatService2")
                return
            }
            curState_sock = false
            Log.i("###############", "restartChatService3")
        } catch (e: Exception) {
            Log.i("###############", e.toString())
            e.printStackTrace()
        }
    }

    private fun startForegroundWithNotification() {
        val logTitle = object{}.javaClass.enclosingMethod?.name!!
        try {
            shouldThreadStop = false
            startForeground(Const.NOTI_ID_FOREGROUND_SERVICE, null) //id should not be zero
            //Timer().schedule(mainTask(), 1000) //or postDelayed
        } catch (e: Exception) {
            Log.i("###############", e.toString())
            e.printStackTrace()
        }
    }

    private fun initDeamon() {
        val logTitle = object{}.javaClass.enclosingMethod?.name!!
        try {
            val r: Runnable = Daemon()
            thread = Thread(r)
            thread!!.setDaemon(true)
            thread!!.start()
        } catch (e: Exception) {
            Log.i("initDeamon", e.toString())
            e.printStackTrace()
        }
    }

    inner class Daemon : Runnable { //thread!!.state => https://developer.android.com/reference/kotlin/java/lang/Thread.State
        override fun run() { //thread.stop() deprecated. thread.interrupt() used instead.
            val logTitle = object{}.javaClass.enclosingMethod?.name!!
            try {
                while (!shouldThreadStop) {
                    synchronized(this) {
                        try { //Util.log(logTitle, "client_socket_connected_check : ${SocketIO.sock!!.connected()}")
                            if (isBeingSockChecked) return
                            //Util.connectSockWithCallback(applicationContext, connManager!!)
                            isBeingSockChecked = true
                            CoroutineScope(Dispatchers.Main).launch {
                                isBeingSockChecked = try {
                                    var json = SocketIO.connect(applicationContext, connManager!!).await()
                                    val code = json.get("code").asString
                                    if (code == Const.RESULT_OK) {
                                        Log.i("code", "ok")
                                    } else {
                                        Log.i("code", "not ok")
                                    }
                                    false
                                } catch (e: Exception) {
                                    false
                                }
                            }
                        } catch (e: InterruptedException) {
                            Log.i("e", e.toString())
                        } catch (e1: Exception) {
                            Log.i("e1", e1.toString())
                        } finally {
                            try {
                                Thread.sleep(SEC_DURING_DAEMON) //Thread.sleep(sleep_sec)
                            } catch (ex11: InterruptedException) {
                                Log.i("ex11", ex11.toString())
                            } catch (ex12: Exception) {
                                Log.i("ex12", ex12.toString())
                            }
                        }
                    }
                }
            } catch (ex: InterruptedException) {
                Log.i("(OK) thread interrupted", ex.toString())
            } catch (ex1: Exception) {
                Log.i("Daemon", ex1.toString())
            }
        }
    }

}