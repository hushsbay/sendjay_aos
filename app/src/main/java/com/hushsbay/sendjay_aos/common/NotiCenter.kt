package com.hushsbay.sendjay_aos.common

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Vibrator
import androidx.core.app.NotificationCompat

object NotiCenter {

    var manager: NotificationManager? = null //See MainActivity.kt
    var channel: NotificationChannel? = null
    var audio: AudioManager? = null
    var vib: Vibrator? = null
    var mapRoomid: MutableMap<String, Int> = mutableMapOf<String, Int>() //See MainActivity.kt
    var mapRoomInfo: MutableMap<String, MutableMap<String, String>> = mutableMapOf<String, MutableMap<String, String>>() //See MainActivity.kt

    var gapScreenOffOnDualMode = "10000"
    var gapScreenOnOnDualMode = "5000"

    private var packageName: String? = null
    private var idNotiMax = Const.NOTI_CNT_START

    operator fun invoke(context: Context, packageName1: String) { //IMPORTANCE_MIN (no display on status bar) was not worked
        if (channel == null) channel = NotificationChannel(Const.NOTICHANID_COMMON, Const.NOTICHANID_COMMON, NotificationManager.IMPORTANCE_LOW) //IMPORTANCE_LOW : no sound and display on status bar)
        if (manager == null) {
            manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager!!.createNotificationChannel(NotiCenter.channel!!)
        }
        if (audio == null) audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (vib == null) vib = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        packageName = packageName1
    }

    fun notiFound(id: Int) : Boolean {
        val sbNoti = manager!!.activeNotifications
        var found = false
        for (item in sbNoti) { //val iteratorNoti = sbNoti.iterator(); while (iteratorNoti.hasNext()) { Util.log("####", iteratorNoti.next().toString()) }
            if (item.toString().contains("id=${id.toString()}")) {
                found = true
                break
            }
        }
        return found
    }

}