package com.hushsbay.sendjay_aos

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.hushsbay.sendjay_aos.common.Const
import com.hushsbay.sendjay_aos.ui.theme.Sendjay_aosTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            //Util.log("AlarmReceiver", "ACTION_BOOT_COMPLETED")
            Toast.makeText(context, "ACTION_BOOT_COMPLETED", Toast.LENGTH_LONG).show()
        } else { //receives right after ChatService killed
            //Util.log("AlarmReceiver", intent.action.toString())
        }
        if (ChatService.state == Const.ServiceState.STOPPED) {
            val intentNew = Intent(context, ChatService::class.java)
            context.startForegroundService(intentNew)
        }
    }

}
