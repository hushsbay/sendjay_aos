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

    val gapScreenOffOnDualMode = "1000"

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
        //if (vib == null) vib = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (vib == null) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vib = vibratorManager.defaultVibrator
        }
        packageName = strPackageName
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
            if (tm_to == "") tm_to = "2400"
            //Util.log("@@@@", tm+"==="+tm_fr+"==="+tm_to+"==="+returnTo+"==="+roomidForService)
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
                if (senderid == uInfo.userid) {
                    needNoti = false
//                } else { //
//                    val msgid = data.getString("msgid")
//                    val param = org.json.JSONObject()
//                    param.put("msgid", msgid)
//                    param.put("roomid", returnTo)
//                    val json = HttpFuel.post(context, "/msngr/qry_unread", param.toString()).await()
//                    if (json.get("code").asString == Const.RESULT_OK) {
//                        val list = json.getAsJsonArray("list")
//                        if (list.size() > 0) {
//                            val item = list[0].asJsonObject
//                            if (item.get("UNREAD").asInt == 0) needNoti = false //해당 메시지를 읽었으면 노티가 필요없음
//                        }
//                    }
                }
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
            if (callFromSendMsg) { //웹 연결 체크해 없으면 굳이 웹을 신경쓸 필요없음
                val userkeyArr = data.getJSONArray("userkeyArr")
                webConnectedAlso = userkeyArr.toString().contains(Const.W_KEY + uInfo.userid + "\"") //["W__userid1","W__userid2"]
            } else { //userkeyArr을 가져오기가 어려워 무조건 웹도 연결되어 있다고 보고 처리
                webConnectedAlso = true
            }
            body = Util.getTalkBodyCustom(type, body)
            val intentNoti = Intent(context, MainActivity::class.java)
            intentNoti.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            intentNoti.putExtra("type", "open")
            intentNoti.putExtra("roomid", roomid)
            intentNoti.putExtra("origin", "noti")
            intentNoti.putExtra("objStr", "")
            if (mapRoomid[roomid] == null) mapRoomid[roomid] = idNotiMax++
            //var noiseForRoom = true
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
                val screenState = KeyChain.get(context, Const.KC_SCREEN_STATE) ?: ""
                if (screenState == "off") { //PC 브라우저가 이 챗방을 열어 놓고 있으면 여기서 delay만큼 늦게 읽으므로 이미 읽음처리되어 노티가 필요없음
                    val delaySec: Long = gapScreenOffOnDualMode.toLong()
                    delay(delaySec) //Handler().postDelayed({ ... }, delaySec)
                }
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
            } else { //Noti can be called right away when only mobile is online or mobile just reconnected
                procNoti(context, uInfo, roomid, noti, notiSummary)
            }
        }
    }

//    fun notiByRoom(context: Context, uInfo: UserInfo, roomid: String, body: String, webConnectedAlso: Boolean, msgid: String, cdt: String) {
//        CoroutineScope(Dispatchers.IO).launch {
//            val intentNoti = Intent(context, MainActivity::class.java)
//            intentNoti.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
//            intentNoti.putExtra("type", "open")
//            intentNoti.putExtra("roomid", roomid)
//            intentNoti.putExtra("origin", "noti")
//            intentNoti.putExtra("objStr", "")
//            if (mapRoomid[roomid] == null) mapRoomid[roomid] = idNotiMax++
//            var noiseForRoom = true
//            var title = if (mapRoomInfo[roomid] != null) {
//                mapRoomInfo[roomid]?.get("roomtitle").toString()
//            } else {
//                //val json = HttpFuel.get(context, "${Const.DIR_ROUTE}/get_roominfo", listOf("roomid" to roomid)).await()
//                val param = org.json.JSONObject()
//                param.put("roomid", roomid)
//                val json = HttpFuel.post(context, "/msngr/get_roominfo", param.toString()).await()
//                if (json.get("code").asString == Const.RESULT_OK) {
//                    getRoomInfo(json, roomid)["roomtitle"].toString()
//                } else {
//                    ""
//                }
//            }
//            val m_cdt = mapRoomInfo[roomid]?.get("cdt") //Util.log("notiByRoom", "skip: already notified: $cdt === ${m_cdt.toString()}")
//            if (m_cdt != null && cdt <= m_cdt.toString()) return@launch
//            mapRoomInfo[roomid]?.put("cdt", cdt)
//            if (uInfo.senderoff == "Y") title = "" //sender
//            if (mapRoomInfo[roomid]?.get("noti").toString() == "X") noiseForRoom = false
//            val realTitle = if (title == "") null else title
//            val realBody = if (uInfo.bodyoff == "Y") null else body
//            val noti = setupNoti(context, realTitle, realBody, intentNoti, mapRoomid[roomid]!!)
//            val notiSummary = setupNotiSummmary(context)
//            if (webConnectedAlso && msgid != "") { //["W__userid1","W__userid2"]
//                val screenState = KeyChain.get(context, Const.KC_SCREEN_STATE) ?: ""
//                val delaySec: Long = if (screenState != "on") gapScreenOffOnDualMode.toLong() else gapScreenOnOnDualMode.toLong()
//                delay(delaySec) //Handler().postDelayed({ ... }, delaySec)
//                //val param = listOf("msgid" to msgid, "roomid" to roomid)
//                //val json = HttpFuel.get(context, "${Const.DIR_ROUTE}/qry_unread", param).await()
//                val param = org.json.JSONObject()
//                param.put("msgid", msgid)
//                param.put("roomid", roomid)
//                val json = HttpFuel.post(context, "/msngr/qry_unread", param.toString()).await()
//                if (json.get("code").asString == Const.RESULT_OK) {
//                    val list = json.getAsJsonArray("list")
//                    if (list.size() > 0) {
//                        val item = list[0].asJsonObject
//                        if (item.get("UNREAD").asInt > 0) procNoti(context, uInfo, roomid, noti, notiSummary, noiseForRoom)
//                    }
//                }
//            } else { //Noti can be called right away when only mobile is online or mobile just reconnected
//                procNoti(context, uInfo, roomid, noti, notiSummary, noiseForRoom)
//            }
//        }
//    }

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

    private fun procNoti(context: Context, uInfo: UserInfo, roomid: String, noti: Notification, notiSummary: Notification) {
        manager!!.notify(mapRoomid[roomid]!!, noti)
        manager!!.notify(Const.NOTI_ID_SUMMARY, notiSummary)
        if (audio!!.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            if (uInfo.soundoff == "Y" && uInfo.viboff == "Y") {
                //소리 Off, 진동 Off
            } else if (uInfo.soundoff == "Y") { //소리만 Off인 경우
                procVibrate() //진동 On
            } else { //진동만 Off인 경우
                procSound(context) //소리 On
            }
        } else if (audio!!.ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
            if (uInfo.soundoff == "Y" && uInfo.viboff == "Y") {
                //소리 Off, 진동 Off
            } else {
                procVibrate() //진동 On
            }
        }
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

//    private fun procNoti(context: Context, uInfo: UserInfo, roomid: String, noti: Notification, notiSummary: Notification, noiseForRoom: Boolean) {
//        manager!!.notify(mapRoomid[roomid]!!, noti)
//        manager!!.notify(Const.NOTI_ID_SUMMARY, notiSummary)
//        decideVerboseNoti(context, uInfo, noiseForRoom)
//        if (idNotiMax > Const.NOTI_CNT_END) {
//            idNotiMax = Const.NOTI_CNT_START
//            mapRoomid.clear()
//            mapRoomInfo.clear()
//        }
//        CoroutineScope(Dispatchers.IO).launch {
//            //모바일에서만 호출. 웹과는 다르게 알림이 표시되고 나면 사용자는 언젠가는 알림을 보게 될 것이고
//            //알림을 보고 난 후에는 다음에 재연결될 때는 기존 알림을 또 보는 것이 성가시게 될 것이므로 (아니면 사용자가 일일이 방마다 읽음 처리해야 하므로..)
//            //LASTCHKDT 필드값을 업데이트해서 그 이후에 온 안읽은 톡만 알림을 표시하는 것이 합리적일 것으로 보임
//            try {
//                val param = org.json.JSONObject()
//                param.put("type", "U")
//                HttpFuel.post(context, "/msngr/qry_unread", param.toString()).await()
//            } catch (e: Exception) {
//                //do nothing
//            }
//        }
//    }
//
//    private fun decideVerboseNoti(context: Context, uInfo: UserInfo, noiseForRoom: Boolean) {
//        var skipNoisy = false
//        if (uInfo.fr != "" && uInfo.to != "") {
//            val curTm = Util.getCurDateTimeStr().substring(8, 12) //Util.log("@@@@", curTm, uInfo.fr, uInfo.to)
//            if (uInfo.fr <= uInfo.to) {
//                if (curTm >= uInfo.fr && curTm <= uInfo.to) { //OK
//                } else {
//                    skipNoisy = true
//                }
//            } else {
//                if ((curTm >= uInfo.fr && curTm <= "2400") || (curTm <= uInfo.to && curTm >= "0000")) { //OK
//                } else {
//                    skipNoisy = true
//                }
//            }
//        }
//        if (!skipNoisy && uInfo.notioff != "Y" && noiseForRoom) {
//            if (audio!!.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
//                if (uInfo.soundoff != "Y") {
//                    procSound(context)
//                } else {
//                    procVibrate()
//                }
//            } else if (audio!!.ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
//                procVibrate()
//            }
//        }
//    }

    private fun procSound(context: Context) {
        val ringtone = RingtoneManager.getRingtone(context, Uri.parse("android.resource://$packageName/${R.raw.sendjay}"))
        ringtone.play()
    }

    private fun procVibrate() { //현재 디바이스 앱 알림 설정에서 무음으로 하지 않으면 기존 진동과 여기서 설정한 진동이 혼재되어 발생함
        //일단, 앱 알림 허용 코딩에서 무음으로 하는 방법을 찾아 보기로 함
        val timings = longArrayOf(0, 900, 0, 0) //val timings = longArrayOf(0, 300, 200, 300)
        val amp = intArrayOf(0, 60, 0, 0) //val amp = intArrayOf(0, 50, 0, 50)
        vib!!.vibrate(VibrationEffect.createWaveform(timings, amp, -1))
        //vib!!.vibrate(VibrationEffect.createOneShot(900, VibrationEffect.DEFAULT_AMPLITUDE))
    }

}