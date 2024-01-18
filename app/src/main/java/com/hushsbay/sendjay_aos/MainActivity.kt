package com.hushsbay.sendjay_aos

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.hushsbay.sendjay_aos.ui.theme.Sendjay_aosTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

//socket.io는 json(org.json.JSONObect) 사용. Fuel은 gson(com.google.gson.JsonObject) 사용
//onCreate -> onStart -> onResume -> onPause -> onStop -> onDestroy
class MainActivity : ComponentActivity() {

    companion object { //See ChatService.kt
        var isOnTop = true
        var stopServiceByLogout = false
    }

    private lateinit var curContext: Activity
    private lateinit var pm: PowerManager
    private lateinit var connManager: ConnectivityManager

    private var isOnCreate = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Sendjay_aosTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Greeting("Android")
                }
            }
        }
        curContext = this@MainActivity
        isOnCreate = true
        stopServiceByLogout = false
        pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        connManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        start()
    }

    private fun start() {
        val logTitle = object{}.javaClass.enclosingMethod?.name!!
        if (!packageManager.canRequestPackageInstalls()) {
            //Util.alert(curContext, "이 앱은 플레이스토어에서 다운로드받지 않는 인하우스앱입니다. 따라서, 출처를 알 수 없는 앱(${Const.TITLE}) 사용을 허용해 주시기 바랍니다.", Const.TITLE, {
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            //})
        } else {
            CoroutineScope(Dispatchers.Main).launch {
                //if (!chkUpdate(true)) return@launch
                //procLogin(false) {
                    //CoroutineScope(Dispatchers.Main).launch {
                        try {
                            //val winid1 = Util.getCurDateTimeStr() //Mobile
                            //val param = listOf("type" to "set_new", "userkey" to uInfo.userkey, "winid" to winid1)
                            //val json = HttpFuel.get(curContext, "${Const.DIR_ROUTE}/chk_redis", param).await()
                            //if (json.get("code").asString != Const.RESULT_OK) {
                            //    Util.alert(curContext, json.get("msg").asString, logTitle)
                            //} else {
                            //    KeyChain.set(curContext, Const.KC_WINID, winid1)
                            //    KeyChain.set(curContext, Const.KC_USERIP, json.get("userip").asString)
                                if (ChatService.serviceIntent == null) {
                                    val intentNew = Intent(curContext, ChatService::class.java)
                                    startForegroundService(intentNew)
                                }
                            //    setupWebViewMain()
                                //setupWebViewLocal()
                            //}
                        } catch (e: Exception) {
                            //logger.error("$logTitle: ${e.toString()}")
                            //Util.procException(curContext, e, logTitle)
                            Log.i("###############", e.toString())
                        }
                   //}
                //}
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    Sendjay_aosTheme {
        Greeting("Android")
    }
}