package com.hushsbay.sendjay_aos.common

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.JsonObject
//import com.hushsbay.sendjay.common.*
import com.hushsbay.sendjay_aos.MainActivity
import com.hushsbay.sendjay_aos.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    operator fun invoke(context: Context, strPackageName: String) { //IMPORTANCE_MIN (no display on status bar) was not worked
        if (channel == null) { //IMPORTANCE_LOW(no sound), IMPORTANCE_DEFAULT(sound ok), IMPORTANCE_HIGH(sound + headup(popup))
            channel = NotificationChannel(Const.NOTICHANID_COMMON, Const.NOTICHANID_COMMON, NotificationManager.IMPORTANCE_HIGH)
        }
        if (manager == null) {
            manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager!!.createNotificationChannel(NotiCenter.channel!!)
        }
        if (audio == null) audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (vib == null) vib = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        packageName = strPackageName
    }

    fun chkNotiOn(context: Context, returnTo: String, roomidForService: String) : Boolean {
        //환경설정 데이터를 읽어서 알림을 표시할 지 말지를 결정
        //해당, 챗방이 열려 있다면 (이미 읽은 것으로 보므로) 알림이 필요없음
        var needNoti = true
        if (KeyChain.get(context, Const.KC_NOTI_OFF) == "Y") {
            needNoti = false
        } else {
            var dt = Util.getCurDateTimeStr() //20240512130549
            val tm = dt.substring(8, 12) //1305 //Util.log("@@@@", tm)
            val tm_fr = KeyChain.get(context, Const.KC_TM_FR) ?: "0000"
            val tm_to = KeyChain.get(context, Const.KC_TM_TO) ?: "2400"
            if (tm < tm_fr && tm > tm_to) {
                needNoti = false
            } else {
                if (returnTo != "" && returnTo == roomidForService && KeyChain.get(context, Const.KC_SCREEN_STATE) == "on" && MainActivity.isOnTop) {
                    needNoti = false
                }
            }
        }
        return needNoti
    }

    fun notiByRoom(context: Context, uInfo: UserInfo, roomid: String, body: String, webConnectedAlso: Boolean, msgid: String, cdt: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val intentNoti = Intent(context, MainActivity::class.java)
            intentNoti.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            intentNoti.putExtra("type", "open")
            intentNoti.putExtra("roomid", roomid)
            intentNoti.putExtra("origin", "noti")
            intentNoti.putExtra("objStr", "")
            if (mapRoomid[roomid] == null) mapRoomid[roomid] = idNotiMax++
            var noiseForRoom = true
            var title = if (mapRoomInfo[roomid] != null) {
                mapRoomInfo[roomid]?.get("roomtitle").toString()
            } else {
                //val json = HttpFuel.get(context, "${Const.DIR_ROUTE}/get_roominfo", listOf("roomid" to roomid)).await()
                val param = org.json.JSONObject()
                param.put("roomid", roomid)
                val json = HttpFuel.post(context, "/msngr/get_roominfo", param.toString()).await()
                if (json.get("code").asString == Const.RESULT_OK) {
                    getRoomInfo(json, roomid)["roomtitle"].toString()
                } else {
                    ""
                }
            }
            val m_cdt = mapRoomInfo[roomid]?.get("cdt") //Util.log("notiByRoom", "skip: already notified: $cdt === ${m_cdt.toString()}")
            if (m_cdt != null && cdt <= m_cdt.toString()) return@launch
            mapRoomInfo[roomid]?.put("cdt", cdt)
            if (uInfo.senderoff == "Y") title = "" //sender
            if (mapRoomInfo[roomid]?.get("noti").toString() == "X") noiseForRoom = false
            val realTitle = if (title == "") null else title
            val realBody = if (uInfo.bodyoff == "Y") null else body
            val noti = setupNoti(context, realTitle, realBody, intentNoti, mapRoomid[roomid]!!)
            val notiSummary = setupNotiSummmary(context)
            if (webConnectedAlso && msgid != "") { //["W__userid1","W__userid2"]
                val screenState = KeyChain.get(context, Const.KC_SCREEN_STATE) ?: ""
                val delaySec: Long = if (screenState != "on") gapScreenOffOnDualMode.toLong() else gapScreenOnOnDualMode.toLong()
                delay(delaySec) //Handler().postDelayed({ ... }, delaySec)
                //val param = listOf("msgid" to msgid, "roomid" to roomid)
                //val json = HttpFuel.get(context, "${Const.DIR_ROUTE}/qry_unread", param).await()
                val param = org.json.JSONObject()
                param.put("msgid", msgid)
                param.put("roomid", roomid)
                val json = HttpFuel.post(context, "/msngr/qry_unread", param.toString()).await()
                if (json.get("code").asString == Const.RESULT_OK) {
                    val list = json.getAsJsonArray("list")
                    if (list.size() > 0) {
                        val item = list[0].asJsonObject
                        if (item.get("UNREAD").asInt > 0) procNoti(context, uInfo, roomid, noti, notiSummary, noiseForRoom)
                    }
                }
            } else { //Noti can be called right away when only mobile is online or mobile just reconnected
                procNoti(context, uInfo, roomid, noti, notiSummary, noiseForRoom)
            }
        }
    }

    fun getRoomInfo(json: JsonObject, roomid: String) : MutableMap<String, String> {
        var ret = mutableMapOf("roomtitle" to "not found", "noti" to "X")
        if (json.get("code").asString == Const.RESULT_OK) {
            val list = json.getAsJsonArray("list") //if (list.size() == 0) return
            val item = list[0].asJsonObject
            val nicknm = item.get("NICKNM").asString
            val mainnm = item.get("MAINNM").asString
            val roomnm = item.get("ROOMNM").asString
            val noti = item.get("NOTI").asString
            var title = ""
            title = if (nicknm != null && nicknm != "") {
                nicknm
            } else if (mainnm != null && mainnm != "") {
                mainnm
            } else {
                val roomnmJson = Gson().fromJson(roomnm, JsonObject::class.java)
                roomnmJson.get("roomnm").asString
            }
            ret = mutableMapOf("roomtitle" to title, "noti" to noti)
            mapRoomInfo[roomid] = ret
        } else {
            //This should not be happened.
        }
        return ret
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

    private fun setupNoti(context: Context, title: String? = null, body: String? = null, intentNoti: Intent, requestCode: Int): Notification {
        //https://fimtrus.tistory.com/entry/Android-Notification-%EC%97%90%EC%84%9C-onNewIntent%EA%B0%80-%ED%83%80%EC%A7%80-%EC%95%8A%EB%8A%94-%EB%AC%B8%EC%A0%9C
        val builder = NotificationCompat.Builder(context, Const.NOTICHANID_COMMON)
        builder.setSmallIcon(R.mipmap.ic_launcher_round)
        val style = NotificationCompat.BigTextStyle()
        style.bigText(null)
        style.setBigContentTitle(null)
        style.setSummaryText(null)
        builder.setStyle(style)
        builder.setGroup(Const.APP_NAME)
        builder.setContentText(body ?: "New message arrived.")
        builder.setContentTitle(title)
        builder.setAutoCancel(true) //builder.setOngoing(true)
        val pendingIntent = PendingIntent.getActivity(context, requestCode, intentNoti, PendingIntent.FLAG_IMMUTABLE)
        builder.setContentIntent(pendingIntent)
        return builder.build()
    }

    private fun setupNotiSummmary(context: Context): Notification { //https://superwony.tistory.com/124
        val builder = NotificationCompat.Builder(context, Const.NOTICHANID_COMMON)
        builder.setSmallIcon(R.mipmap.ic_launcher_round)
        builder.setOnlyAlertOnce(true)
        builder.setGroup(Const.APP_NAME)
        builder.setGroupSummary(true) //single icon for multi room
        builder.setAutoCancel(true) //builder.setOngoing(true)
        return builder.build()
    }

    private fun procNoti(context: Context, uInfo: UserInfo, roomid: String, noti: Notification, notiSummary: Notification, noiseForRoom: Boolean) {
        manager!!.notify(mapRoomid[roomid]!!, noti)
        manager!!.notify(Const.NOTI_ID_SUMMARY, notiSummary)
        decideVerboseNoti(context, uInfo, noiseForRoom)
        if (idNotiMax > Const.NOTI_CNT_END) {
            idNotiMax = Const.NOTI_CNT_START
            mapRoomid.clear()
            mapRoomInfo.clear()
        }
        CoroutineScope(Dispatchers.IO).launch {
            //모바일에서만 호출. 웹과는 다르게 알림이 표시되고 나면 사용자는 언젠가는 알림을 보게 될 것이고
            //알림을 보고 난 후에는 다음에 재연결될 때는 기존 알림을 또 보는 것이 성가시게 될 것이므로 (아니면 사용자가 일일이 방마다 읽음 처리해야 하므로..)
            //LASTCHKDT 필드값을 업데이트해서 그 이후에 온 안읽은 톡만 알림을 표시하는 것이 합리적일 것으로 보임
            try {
                val param = org.json.JSONObject()
                param.put("type", "U")
                HttpFuel.post(context, "/msngr/qry_unread", param.toString()).await()
            } catch (e: Exception) {
                //do nothing
            }
        }
    }

    private fun decideVerboseNoti(context: Context, uInfo: UserInfo, noiseForRoom: Boolean) {
        var skipNoisy = false
        if (uInfo.fr != "" && uInfo.to != "") {
            val curTm = Util.getCurDateTimeStr().substring(8, 12) //Util.log("@@@@", curTm, uInfo.fr, uInfo.to)
            if (uInfo.fr <= uInfo.to) {
                if (curTm >= uInfo.fr && curTm <= uInfo.to) { //OK
                } else {
                    skipNoisy = true
                }
            } else {
                if ((curTm >= uInfo.fr && curTm <= "2400") || (curTm <= uInfo.to && curTm >= "0000")) { //OK
                } else {
                    skipNoisy = true
                }
            }
        }
        if (!skipNoisy && uInfo.notioff != "Y" && noiseForRoom) {
            if (audio!!.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                if (uInfo.soundoff != "Y") {
                    procSound(context)
                } else {
                    procVibrate()
                }
            } else if (audio!!.ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
                procVibrate()
            }
        }
    }

    private fun procSound(context: Context) {
        val ringtone = RingtoneManager.getRingtone(context, Uri.parse("android.resource://$packageName/${R.raw.sendjay}"))
        ringtone.play()
    }

    private fun procVibrate() {
        val timings = longArrayOf(0, 900, 0, 0) //val timings = longArrayOf(0, 300, 200, 300)
        val amp = intArrayOf(0, 60, 0, 0) //val amp = intArrayOf(0, 50, 0, 50)
        vib!!.vibrate(VibrationEffect.createWaveform(timings, amp, -1))
    }

}