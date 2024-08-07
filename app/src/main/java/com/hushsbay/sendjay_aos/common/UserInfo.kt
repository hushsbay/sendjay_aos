package com.hushsbay.sendjay_aos.common

import android.content.Context
import com.google.gson.JsonObject

class UserInfo { //See Util.getStrObjFromUserInfo() also.

    //키체인 연동 (set/get)

    var token: String //Util.getStrObjFromUserInfo() 멤버
    var userid: String //Util.getStrObjFromUserInfo() 멤버
    var userkey: String //Util.getStrObjFromUserInfo() 멤버
    var usernm: String //Util.getStrObjFromUserInfo() 멤버
    var autokey_app: String //Util.getStrObjFromUserInfo() 멤버
    var orgcd: String //Util.getStrObjFromUserInfo() 멤버
    var orgnm: String //Util.getStrObjFromUserInfo() 멤버
    var toporgcd: String //Util.getStrObjFromUserInfo() 멤버
    var toporgnm: String //Util.getStrObjFromUserInfo() 멤버
    var pwd: String
    var notioff: String
    var fr: String
    var to: String
    var bodyoff: String
    var senderoff: String

    constructor(context: Context, json: JsonObject) { //gson (not org.json) => 키체인 설정(set) : 인증(login.js)시만 처리
        if (json.get(Const.KC_TOKEN) == null) {
            this.token = ""
        } else {
            this.token = json.get(Const.KC_TOKEN).asString
        }
        if (json.get(Const.KC_USERID) == null) {
            this.userid = ""
            this.userkey = ""
        } else {
            this.userid = json.get(Const.KC_USERID).asString
            this.userkey = Const.M_KEY + this.userid
        }
        if (json.get(Const.KC_PWD) == null) {
            this.pwd = ""
        } else {
            this.pwd = json.get(Const.KC_PWD).asString
        }
        if (json.get(Const.KC_USERNM) == null) {
            this.usernm = ""
        } else {
            this.usernm = json.get(Const.KC_USERNM).asString
        }
        if (json.get(Const.KC_ORGCD) == null) {
            this.orgcd = ""
        } else {
            this.orgcd = json.get(Const.KC_ORGCD).asString
        }
        if (json.get(Const.KC_ORGNM) == null) {
            this.orgnm = ""
        } else {
            this.orgnm = json.get(Const.KC_ORGNM).asString
        }
        if (json.get(Const.KC_TOPORGCD) == null) {
            this.toporgcd = ""
        } else {
            this.toporgcd = json.get(Const.KC_TOPORGCD).asString
        }
        if (json.get(Const.KC_TOPORGNM) == null) {
            this.toporgnm = ""
        } else {
            this.toporgnm = json.get(Const.KC_TOPORGNM).asString
        }
        if (json.get(Const.KC_NOTI_OFF) == null) {
            this.notioff = ""
        } else {
            this.notioff = json.get(Const.KC_NOTI_OFF).asString
        }
        if (json.get(Const.KC_BODY_OFF) == null) {
            this.bodyoff = ""
        } else {
            this.bodyoff = json.get(Const.KC_BODY_OFF).asString
        }
        if (json.get(Const.KC_SENDER_OFF) == null) {
            this.senderoff = ""
        } else {
            this.senderoff = json.get(Const.KC_SENDER_OFF).asString
        }
        if (json.get(Const.KC_TM_FR) == null) {
            this.fr = ""
        } else {
            this.fr = json.get(Const.KC_TM_FR).asString
        }
        if (json.get(Const.KC_TM_TO) == null) {
            this.to = ""
        } else {
            this.to = json.get(Const.KC_TM_TO).asString
        }
        if (json.get(Const.KC_AUTOKEY_APP) == null) {
            this.autokey_app = ""
        } else {
            this.autokey_app = json.get(Const.KC_AUTOKEY_APP).asString
        }
        if (this.token != "") KeyChain.set(context, Const.KC_TOKEN, this.token) //token이 빈값으로 내려오는 경우 KeyChain 건들지 말기
        KeyChain.set(context, Const.KC_USERID, this.userid)
        KeyChain.set(context, Const.KC_PWD, this.pwd)
        KeyChain.set(context, Const.KC_USERKEY, this.userkey)
        KeyChain.set(context, Const.KC_USERNM, this.usernm)
        KeyChain.set(context, Const.KC_ORGCD, this.orgcd)
        KeyChain.set(context, Const.KC_ORGNM, this.orgnm)
        KeyChain.set(context, Const.KC_TOPORGCD, this.toporgcd)
        KeyChain.set(context, Const.KC_TOPORGNM, this.toporgnm)
        KeyChain.set(context, Const.KC_NOTI_OFF, this.notioff)
        KeyChain.set(context, Const.KC_TM_FR, this.fr)
        KeyChain.set(context, Const.KC_TM_TO, this.to)
        KeyChain.set(context, Const.KC_BODY_OFF, this.bodyoff)
        KeyChain.set(context, Const.KC_SENDER_OFF, this.senderoff)
        KeyChain.set(context, Const.KC_AUTOKEY_APP, this.autokey_app)
    }

    constructor(context: Context) { //키체인 읽어오기(get)
        this.token = KeyChain.get(context, Const.KC_TOKEN) ?: ""
        this.userid = KeyChain.get(context, Const.KC_USERID) ?: ""
        this.pwd = KeyChain.get(context, Const.KC_PWD) ?: ""
        this.userkey = KeyChain.get(context, Const.KC_USERKEY) ?: ""
        this.usernm = KeyChain.get(context, Const.KC_USERNM) ?: ""
        this.orgcd = KeyChain.get(context, Const.KC_ORGCD) ?: ""
        this.orgnm = KeyChain.get(context, Const.KC_ORGNM) ?: ""
        this.toporgcd = KeyChain.get(context, Const.KC_TOPORGCD) ?: ""
        this.toporgnm = KeyChain.get(context, Const.KC_TOPORGNM) ?: ""
        this.notioff = KeyChain.get(context, Const.KC_NOTI_OFF) ?: ""
        this.fr = KeyChain.get(context, Const.KC_TM_FR) ?: ""
        this.to = KeyChain.get(context, Const.KC_TM_TO) ?: ""
        this.bodyoff = KeyChain.get(context, Const.KC_BODY_OFF) ?: ""
        this.senderoff = KeyChain.get(context, Const.KC_SENDER_OFF) ?: ""
        this.autokey_app = KeyChain.get(context, Const.KC_AUTOKEY_APP) ?: ""
    }

}