package com.hushsbay.sendjay_aos

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.hushsbay.sendjay_aos.common.Const
import com.hushsbay.sendjay_aos.common.LogHelper
import com.hushsbay.sendjay_aos.common.Util
import org.apache.log4j.Logger
import java.util.Calendar

class AlarmReceiver : BroadcastReceiver() {

    @SuppressLint("ScheduleExactAlarm")
    override fun onReceive(context: Context, intent: Intent) {
        val act = intent.action.toString()
        if (act == Intent.ACTION_BOOT_COMPLETED) {
            Util.log("AlarmReceiver", act)
            Toast.makeText(context, act, Toast.LENGTH_SHORT).show()
        } else {
            Util.log("AlarmReceiver", act) //act = restart_service or one_minute_check
            Toast.makeText(context, "AlarmReceiver, $act", Toast.LENGTH_SHORT).show()
            if (act == "one_minute_check") {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val nextIntent = Intent(context, AlarmReceiver::class.java)
                nextIntent.action = "one_minute_check"
                val pendingIntent = PendingIntent.getBroadcast(context,1, nextIntent, PendingIntent.FLAG_IMMUTABLE) // or PendingIntent.FLAG_UPDATE_CURRENT)
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = System.currentTimeMillis()
                calendar.add(Calendar.SECOND, 60)
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent) //권한 설정 필요
            }
        }
        if (ChatService.state == Const.ServiceState.STOPPED) {
            val intentNew = Intent(context, ChatService::class.java)
            context.startForegroundService(intentNew)
            //android.app.ForegroundServiceStartNotAllowedException: startForegroundService() not allowed due to mAllowStartForeground false
            //위 오류 발생해 재가동 안되는데 일단, 배터리 최적화 대상에서 제외하고 돌려보아도 안됨 (원래 아래 링크 읽어 보면 해결된다고 나오는데 안됨)
            //https://developer.android.com/develop/background-work/services/foreground-services?hl=ko#background-start-restriction-exemptions
            //대신, 위 링크에서 아래와 같이 SYSTEM_ALERT_WINDOW 권한을 보유하면 된다고 해서 해보니 과연 해결됨
            //<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
            Util.log("AlarmReceiver", "startForegroundService")
            Toast.makeText(context, "AlarmReceiver, startForegroundService", Toast.LENGTH_SHORT).show()
        } else {
            Util.log("AlarmReceiver", "ChatService.state is not stopped.")
            Toast.makeText(context, "AlarmReceiver, ChatService.state is not stopped.", Toast.LENGTH_SHORT).show()
        }
    }

}
