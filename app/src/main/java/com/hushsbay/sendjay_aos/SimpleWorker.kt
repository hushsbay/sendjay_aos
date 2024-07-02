package com.hushsbay.sendjay_aos

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.hushsbay.sendjay_aos.common.Const
import com.hushsbay.sendjay_aos.common.KeyChain
import com.hushsbay.sendjay_aos.common.LogHelper
import com.hushsbay.sendjay_aos.common.Util
import org.apache.log4j.Logger

//현재 미사용중이나 버리기 아까운 내용이라 제거하지 않고 소스 참고용으로 보관하는 것임

class SimpleWorker(context: Context, workerParameters: WorkerParameters) : Worker(context, workerParameters) {

    private val logger: Logger = LogHelper.getLogger(context, this::class.simpleName)
    //private val connManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var context = context

    override fun doWork(): Result {
        val logTitle = object{}.javaClass.enclosingMethod?.name!!
        try {
            val autoLogin = KeyChain.get(applicationContext, Const.KC_AUTOLOGIN) ?: ""
            if (autoLogin == "Y") { //Do not use Util.connectSockWithCallback().
                if (ChatService.state == Const.ServiceState.STOPPED) { //Just restart ChatService.kt
                    val intentNew = Intent(context, ChatService::class.java)
                    context.startForegroundService(intentNew)
                }
            } else {
                val intent = Intent(context, ChatService::class.java)
                context.stopService(intent)
            }
        } catch (e: Exception) {
            logger.error("$logTitle: ${e.toString()}")
            Util.log(logTitle, "${e.toString()}")
        }
        return Result.success()
    }

}