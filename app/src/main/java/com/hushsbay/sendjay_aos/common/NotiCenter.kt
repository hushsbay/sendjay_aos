package com.hushsbay.sendjay_aos.common

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.net.Uri
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.hushsbay.sendjay_aos.ChatService
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

    private var packageName: String? = null
    private var idNotiMax = Const.NOTI_CNT_START

    operator fun invoke(context: Context, strPackageName: String) { //IMPORTANCE_MIN (no display on status bar) was not worked
        packageName = strPackageName
        if (channel == null) { //IMPORTANCE_LOW(no sound), IMPORTANCE_DEFAULT(sound ok), IMPORTANCE_HIGH(sound + headup(popup))
            channel = NotificationChannel(Const.NOTICHANID_COMMON, Const.NOTICHANID_COMMON, NotificationManager.IMPORTANCE_DEFAULT)
            val audioAttributes = AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
            val uri = Uri.parse("android.resource://$packageName/${R.raw.sendjay}")
            channel!!.setSound(uri, audioAttributes) //이 설정도 설치제거후 다시 실행한 경우에만 제대로 적용되었음
            channel!!.vibrationPattern = longArrayOf(0, 900, 0, 0) //이 설정도 설치제거후 다시 실행한 경우에만 제대로 적용되었음
            //1. 한번 설정된 Importance는 사용자에 의한 설정변경없이는 불가능하다고 안드로이드 개발자 사이트에 나옴
            //   https://stackoverflow.com/questions/60820163/android-notification-importance-cannot-be-changed
            //2. 아래(setupNoti)에서 1) 진동모드에서도 무진동처리 2) 소리모드에서 무음처리 하는 코딩 필요한데 코딩에서 방법 못찾음
            //##55 위 1.2.항목 때문에, 안드로이드 에니티브 설정을 인텐트로 불러 사용자로 하여금 옵션 설정하는 것으로 구현함 (여기에 팝업설정도 있음)
        }
        if (manager == null) {
            manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager!!.createNotificationChannel(channel!!)
        }
        if (audio == null) audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (vib == null) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vib = vibratorManager.defaultVibrator //vib = vibratorManager.getVibrator()
        }
    }

    suspend fun needNoti(context: Context, uInfo: UserInfo, returnTo: String?=null, roomidForService: String?=null, data: org.json.JSONObject?=null) : Boolean {
        var needNoti = true
        if (KeyChain.get(context, Const.KC_NOTI_OFF) == "Y") {
            needNoti = false //전체 알림 Off면 노티가 필요없음
        } else {
            var dt = Util.getCurDateTimeStr() //20240512130549
            val tm = dt.substring(8, 12) //1305
            var tm_fr = KeyChain.get(context, Const.KC_TM_FR) ?: ""
            var tm_to = KeyChain.get(context, Const.KC_TM_TO) ?: ""
            if (tm_fr == "") tm_fr = "0000"
            if (tm_to == "") tm_to = "2400" //Util.log("@@@@", tm+"==="+tm_fr+"==="+tm_to+"==="+returnTo+"==="+roomidForService)
            if (tm < tm_fr || tm > tm_to) {
                needNoti = false //지정된 알림시간내에 있지 않으면 노티가 필요없음
            } else {
                if (returnTo != null && returnTo == roomidForService && KeyChain.get(context, Const.KC_SCREEN_STATE) == "on" && MainActivity.isOnTop) {
                    needNoti = false //기기가 켜져 있고 해당 챗방이 열려 있을 때는 노티가 필요없음
                }
            }
        }
        if (needNoti) { //그럼에도 노티가 필요하면
            if (data != null && returnTo != null) {
                val senderid = data.getString("senderid")
                if (senderid == uInfo.userid) needNoti = false
            }
        }
        return needNoti
    }

    fun notiToRoom(context: Context, uInfo: UserInfo, roomid: String, data: org.json.JSONObject, callFromSendMsg: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            val msgid = data.getString("msgid")
            var body = data.getString("body")
            val type = data.getString("type")
            val cdt = data.getString("cdt") //서버의 send_msg.js에서 현재일시를 가져옴
            var webConnectedAlso: Boolean
            if (callFromSendMsg) { //웹 연결 체크해 없으면 굳이 웹을 신경쓸 필요없으므로 여기서 미리 파악하는 것임
                val userkeyArr = data.getJSONArray("userkeyArr")
                webConnectedAlso = userkeyArr.toString().contains(Const.W_KEY + uInfo.userid + "\"") //["W__userid1","W__userid2"]
            } else { //userkeyArr을 가져오기가 어려워 무조건 웹도 연결되어 있다고 보고 처리
                webConnectedAlso = true
            }
            body = Util.getTalkBodyCustom(type, body)
            val intentNoti = Intent(context, MainActivity::class.java) //val intentNoti = Intent(context, ChatService::class.java)
            intentNoti.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            intentNoti.putExtra("type", "open")
            intentNoti.putExtra("roomid", roomid)
            intentNoti.putExtra("origin", "noti")
            intentNoti.putExtra("objStr", "")
            if (mapRoomid[roomid] == null) mapRoomid[roomid] = idNotiMax++
            var title = if (mapRoomInfo[roomid] != null) {
                mapRoomInfo[roomid]?.get("roomtitle").toString()
            } else {
                val param = org.json.JSONObject()
                param.put("roomid", roomid)
                val json = HttpFuel.post(context, "/msngr/get_roominfo", param.toString()).await()
                if (json.get("code").asString == Const.RESULT_OK) {
                    getRoomInfo(json, roomid)["roomtitle"].toString()
                } else {
                    ""
                }
            }
            if (mapRoomInfo[roomid]?.get("noti").toString() == "X") return@launch
            val m_cdt = mapRoomInfo[roomid]?.get("cdt") //각 메세지의 현재 시각을 체크해서 그 방의 해당 메시지보다 이전의 메시지면 굳이 노티할 이유가 없음
            if (m_cdt != null && cdt <= m_cdt.toString()) return@launch
            mapRoomInfo[roomid]?.put("cdt", cdt)
            if (uInfo.senderoff == "Y") title = "" //sender
            val realTitle = if (title == "") null else title
            val realBody = if (uInfo.bodyoff == "Y") null else body
            val noti = setupNoti(context, realTitle, realBody, intentNoti, mapRoomid[roomid]!!)
            val notiSummary = setupNotiSummmary(context)
            if (webConnectedAlso && msgid != "") { //["W__userid1","W__userid2"]
                val delaySec: Long = ChatService.gapSecOnDualMode.toLong()
                delay(delaySec) //Handler().postDelayed({ ... }, delaySec)
                val param = org.json.JSONObject()
                param.put("msgid", msgid)
                param.put("roomid", roomid)
                val json = HttpFuel.post(context, "/msngr/qry_unread", param.toString()).await()
                if (json.get("code").asString == Const.RESULT_OK) {
                    val list = json.getAsJsonArray("list")
                    if (list.size() > 0) {
                        val item = list[0].asJsonObject
                        if (item.get("UNREAD").asInt > 0) procNoti(context, uInfo, roomid, noti, notiSummary)
                    }
                }
            } else {
                procNoti(context, uInfo, roomid, noti, notiSummary)
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
        /* 위 ##55 참조 (코딩 제거하지는 말고 참고로 둘 것)
        if (audio!!.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            if (KeyChain.get(context, Const.KC_SOUND_OFF) != "Y") {
            } else {
            }
        } else if (audio!!.ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
            if (KeyChain.get(context, Const.KC_VIB_OFF) != "Y") {
            } else {
            }
        }*/
        val pendingIntent = PendingIntent.getActivity(context, requestCode, intentNoti, PendingIntent.FLAG_IMMUTABLE) //FLAG_UPDATE_CURRENT시 오류 : 로그캣보면 사용불가라고 나옴
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

    private fun procNoti(context: Context, uInfo: UserInfo, roomid: String, noti: Notification, notiSummary: Notification) {
        manager!!.notify(mapRoomid[roomid]!!, noti)
        manager!!.notify(Const.NOTI_ID_SUMMARY, notiSummary)
        /* 여기서 알림 소리와 진동을 처리하지 않고 그 전에 처리 : 사실, procSound()와 procVibrate()는 알림 클래스에 통합되어 있는 형태가 아니므로 여기서는 사용치 말고 참고로 두기로 함
        if (audio!!.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            if (uInfo.soundoff != "Y") procSound(context) //소리 On
        } else if (audio!!.ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
            if (uInfo.viboff != "Y") procVibrate() //진동 On
        }*/
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

    /*private fun procSound(context: Context) { //Notification에 통합된 방법이 아님. 기존 사운드와 혼재되어 발생함
        val ringtone = RingtoneManager.getRingtone(context, Uri.parse("android.resource://$packageName/${R.raw.sendjay}"))
        ringtone.play()
    }
    private fun procVibrate() { //Notification에 통합된 방법이 아님. 기존 진동과 혼재되어 발생함
        val timings = longArrayOf(0, 900, 0, 0) //val timings = longArrayOf(0, 300, 200, 300)
        val amp = intArrayOf(0, 60, 0, 0) //val amp = intArrayOf(0, 50, 0, 50)
        vib!!.vibrate(VibrationEffect.createWaveform(timings, amp, -1))
        /*vib!!.vibrate(VibrationEffect.createOneShot(900, VibrationEffect.DEFAULT_AMPLITUDE))*/
    }*/

}