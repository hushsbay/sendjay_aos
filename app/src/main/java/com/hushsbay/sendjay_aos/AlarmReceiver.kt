package com.hushsbay.sendjay_aos

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.hushsbay.sendjay_aos.common.Const
import com.hushsbay.sendjay_aos.common.Util

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Util.log("AlarmReceiver", "ACTION_BOOT_COMPLETED")
            Toast.makeText(context, "ACTION_BOOT_COMPLETED", Toast.LENGTH_LONG).show()
        } else { //receives right after ChatService killed
            Util.log("AlarmReceiver", intent.action.toString()) //intent.action.toString() = null 현재로선 구분ㅁ해 가져올 것 없음
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
        } else {
            Util.log("AlarmReceiver", "ChatService.state is not stopped.")
        }
    }

}
