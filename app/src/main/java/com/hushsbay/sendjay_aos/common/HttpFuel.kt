package com.hushsbay.sendjay_aos.common

import android.content.Context
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.fuel.coroutines.awaitString
import com.github.kittinunf.fuel.coroutines.awaitStringResponseResult
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.*

object HttpFuel { //single instance, gson(com.google.gson.JsonObject)

    //timeout(Const.RESTFUL_TIMEOUT) 안 먹히고 timeoutRead(Const.RESTFUL_TIMEOUT)가 먹힘
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()) //Parent Job is cancelled 오류 방지 위해 SupervisorJob으로 처리

    fun get(context: Context, url: String, param: List<Pair<String, Any?>>?=null): Deferred<JsonObject> {
        return scope.async {
            try {
                var url = if (url.startsWith("http")) url else Const.URL_SERVER + url
                val token = KeyChain.get(context, Const.KC_TOKEN) ?: ""
                val uid = KeyChain.get(context, Const.KC_USERID) ?: ""
                val cookieStr = "userid=$uid; token=$token"
                val paramAuth = arrayOf(Headers.CONTENT_TYPE to "application/json", Headers.COOKIE to cookieStr) //application/x-www-form-urlencoded (when CORS needed)
                val noCache = listOf("noCache" to Util.getRnd().toString())
                val paramNoCache = param?.plus(noCache) ?: noCache //if (param == null) noCache else param.plus(noCache)
                val jsonStr = Fuel.get(url, paramNoCache).appendHeader(*paramAuth).timeoutRead(Const.RESTFUL_TIMEOUT).awaitString() //*(spread) for vararg
                val json = Gson().fromJson(jsonStr, JsonObject::class.java) //val jsonStr = Gson().toJson(jsonObject)
                json
            } catch (e: Exception) {
                val jsonStr = """{ code : '${Const.RESULT_ERR_HTTPFUEL}', msg : 'HttpFuel:get: ${e.message}' }"""
                Gson().fromJson(jsonStr, JsonObject::class.java)
            }
        }
    }

    //val param = """{ svc_id : 'chk_version', id : '${Proj.APP_CHKID}', ver : '${BuildConfig.VERSION_NAME}' }""" => not worked
    //val param = "{ \"type\" : \"M\" }" //val param = """{ type : 'M' }""" => worked but never ok with jsonBody(param)
    fun post(context: Context, url: String, param: String?=null): Deferred<JsonObject> {
        return scope.async {
            try {
                var url = if (url.startsWith("http")) url else Const.URL_SERVER + url
                val token = KeyChain.get(context, Const.KC_TOKEN) ?: ""
                val uid = KeyChain.get(context, Const.KC_USERID) ?: ""
                val cookieStr = "userid=$uid; token=$token"
                val paramAuth = arrayOf(Headers.CONTENT_TYPE to "application/json", Headers.COOKIE to cookieStr) //application/x-www-form-urlencoded (when CORS needed)
                var paramReal = if (param == null) {
                    val param1 = org.json.JSONObject()
                    param1.put("dummy", "")
                    param1.toString()
                } else {
                    param
                }
                val (request, response, result) = Fuel.post(url).body(paramReal).appendHeader(*paramAuth).timeoutRead(Const.RESTFUL_TIMEOUT).awaitStringResponseResult()
                val (retStr, error) = result //result.toString() => [Success: {"code":"0","msg":"","list":[]}] or [Failure: timeout ~으로 표시됨
                if (error != null) throw Exception(error.toString())
                /* 토큰이 서버에서 생성(갱신)되어 쿠키뿐만 아니라 응답본문에도 같이 내려오도록 했으므로 val json에 포함되니 이 부분은 막아도 됨 (참조용 - 지우지 말 것)
                val cookie = response.headers["Set-Cookie"] //ArrayList<>
                if (cookie.isNotEmpty()) { //unAuth일 경우 쿠키가 안내려올 경우도 고려해야 함. val cookieVal = cookie.first { it.startsWith("token=") }
                    cookie?.flatMap { HttpCookie.parse(it) }?.find { it.name == "token" }?.let {
                        KeyChain.set(context, Const.KC_TOKEN, it.value) //token만 keyChain에 값을 갱신해서 저장
                    }
                }*/
                val json = Gson().fromJson(retStr, JsonObject::class.java)
                json
            } catch (e: Exception) {
                val jsonStr = """{ code : '${Const.RESULT_ERR_HTTPFUEL}', msg : 'HttpFuel:post: ${e.message}' }"""
                Gson().fromJson(jsonStr, JsonObject::class.java)
            }
        }
    }

    fun isNetworkUnstableMsg(gson: JsonObject) : Boolean {
        if (gson.get("msg").asString.contains("timeout") || gson.get("msg").asString.contains("Unable to resolve host")) return true
        return false
    }

}