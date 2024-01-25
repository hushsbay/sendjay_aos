package com.hushsbay.sendjay_aos.common

import android.content.Context
import com.google.gson.JsonObject

class UserInfo { //See Util.getStrObjFromUserInfo() also.

    var token: String
    var userid: String
    var userkey: String
    var usernm: String
    var passkey: String
    var orgcd: String
    //var role: String

    var notioff: String
    var soundoff: String
    var fr: String
    var to: String
    var bodyoff: String
    var senderoff: String

    constructor(context: Context, json: JsonObject) { //gson (not org.json)
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
        if (json.get(Const.KC_USERNM) == null) {
            this.usernm = ""
        } else {
            this.usernm = json.get(Const.KC_USERNM).asString
        }
        if (json.get(Const.KC_PASSKEY) == null) {
            this.passkey = ""
        } else {
            this.passkey = json.get(Const.KC_PASSKEY).asString
        }
        if (json.get(Const.KC_ORGCD) == null) {
            this.orgcd = ""
        } else {
            this.orgcd = json.get(Const.KC_ORGCD).asString
        }
//        if (json.get(Const.KC_ROLE) == null) {
//            this.role = ""
//        } else {
//            this.role = json.get(Const.KC_ROLE).asString
//        }
        if (json.get(Const.KC_NOTI_OFF) == null) {
            this.notioff = ""
        } else {
            this.notioff = json.get(Const.KC_NOTI_OFF).asString
        }
        if (json.get(Const.KC_SOUND_OFF) == null) {
            this.soundoff = ""
        } else {
            this.soundoff = json.get(Const.KC_SOUND_OFF).asString
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
        KeyChain.set(context, Const.KC_TOKEN, this.token)
        KeyChain.set(context, Const.KC_USERID, this.userid)
        KeyChain.set(context, Const.KC_USERKEY, this.userkey)
        KeyChain.set(context, Const.KC_USERNM, this.usernm)
        KeyChain.set(context, Const.KC_PASSKEY, this.passkey)
        KeyChain.set(context, Const.KC_ORGCD, this.orgcd)
        //KeyChain.set(context, Const.KC_ROLE, this.role)
        KeyChain.set(context, Const.KC_NOTI_OFF, this.notioff)
        KeyChain.set(context, Const.KC_SOUND_OFF, this.soundoff)
        KeyChain.set(context, Const.KC_TM_FR, this.fr)
        KeyChain.set(context, Const.KC_TM_TO, this.to)
        KeyChain.set(context, Const.KC_BODY_OFF, this.bodyoff)
        KeyChain.set(context, Const.KC_SENDER_OFF, this.senderoff)
    }

    constructor(context: Context) {
        this.token = KeyChain.get(context, Const.KC_TOKEN) ?: ""
        this.userid = KeyChain.get(context, Const.KC_USERID) ?: ""
        this.userkey = KeyChain.get(context, Const.KC_USERKEY) ?: ""
        this.usernm = KeyChain.get(context, Const.KC_USERNM) ?: ""
        this.passkey = KeyChain.get(context, Const.KC_PASSKEY) ?: ""
        this.orgcd = KeyChain.get(context, Const.KC_ORGCD) ?: ""
        //this.role = KeyChain.get(context, Const.KC_ROLE) ?: ""
        this.notioff = KeyChain.get(context, Const.KC_NOTI_OFF) ?: ""
        this.soundoff = KeyChain.get(context, Const.KC_SOUND_OFF) ?: ""
        this.fr = KeyChain.get(context, Const.KC_TM_FR) ?: ""
        this.to = KeyChain.get(context, Const.KC_TM_TO) ?: ""
        this.bodyoff = KeyChain.get(context, Const.KC_BODY_OFF) ?: ""
        this.senderoff = KeyChain.get(context, Const.KC_SENDER_OFF) ?: ""
    }

}