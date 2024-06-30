package com.hushsbay.sendjay_aos.common

import android.app.Activity
import android.net.ConnectivityManager
import android.webkit.JavascriptInterface
import com.hushsbay.sendjay_aos.ChatService
import com.hushsbay.sendjay_aos.data.RxEvent
import org.json.JSONObject

//class WebInterface(private val curContext: Activity, private val connManager: ConnectivityManager) { //curContext, connManager가 필요한지 다시 체크
class WebInterface() { //When you call these functions from javascript, you should be careful with argument's match for calling function.

    //data: JSONObject, Gson not worked => Java exception was raised during method invocation
    @JavascriptInterface //RxEvent(val ev: String, val data: Any, val returnTo: String?=null, val returnToAnother: String?=null) {
    fun send(ev: String, data: String, returnTo: String?=null, returnToAnother: String?=null, procMsg: Boolean) {
        val json = JSONObject(data) //results in "data":{"userkeys":["W__1",~ in server
        //val json = Gson().fromJson(data, JsonObject::class.java) => results in "data":"{\"userkeys\":[\"W__1\",~ in server
        RxToUp.post(RxEvent(ev, json, returnTo, returnToAnother, procMsg)) //procMsg는 procSocetEmit() in ChatService.kt 참조
    } //RxToUp.post()도 안드로이드에서 독자적으로 송신하는 곳은 극소수임. 대부분 웹뷰에서 .send()로 호출

    @JavascriptInterface
    fun reconnectDone() {
        ChatService.status_sock = Const.SockState.FIRST_DISCONNECTED
    }

}