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
            Util.log("AlarmReceiver", intent.action.toString())
        }
        if (ChatService.state == Const.ServiceState.STOPPED) {
            val intentNew = Intent(context, ChatService::class.java)
            context.startForegroundService(intentNew)
        }
    }

}
