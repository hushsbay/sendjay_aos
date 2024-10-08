package com.hushsbay.sendjay_aos.common

class Const {

    enum class ServiceState {
        STOPPED, LOGOUTED, RUNNING
    }

    enum class SockState(num: Int) {
        BEFORE_CONNECT(0), FIRST_DISCONNECTED(2), RECONNECTED(4)
    }

    companion object {
        const val APP_NAME = "sendjay" //Do not change. see KeyChainProvider also
        const val TITLE = "sendjay" //for AlertDialog Title and so on
        const val AOS = "aos"
        const val VERSIONCHK_APP = "${APP_NAME}_${AOS}"
        const val VERSIONCHK_ETC = "etc"
        ////////////////////////////////////////////////for distinguishing dev and ops (through KeyChain when app starts)
        const val URL_HOST = "https://hushsbay.com"
        const val DIR_PUBLIC = "/app/msngr"
        ////////////////////////////////////////////////
        const val URL_SERVER = "$URL_HOST:444" //444(https) -> 81(http) on AWS
        const val URL_SOCK = "$URL_HOST:3051/jay" //3051(https) -> 3051(http) on AWS
        const val URL_PUBLIC = URL_SERVER + DIR_PUBLIC
        const val URL_JAY = "${DIR_PUBLIC}/" //means public page load
        ////////////////////////////////////////////////
        const val PAGE_MAIN = "/main.html"
        const val PAGE_ROOM = "/chat.html"
        const val PAGE_DUMMY = "/dummy.html"
        ////////////////////////////////////////////////
        const val PROVIDER_AUTHORITY = "com.hushsbay.sendjay_aos.common.KeyChainProvider" //same as ContentProvider class name and <provider ~ android:authorities in AndroidManifest.xml
        const val DELI = "~~" //##은 쓰지 말기 (location param 가져올 때 # 포함시 북마크?로 인지되므로 오류 발생)
        const val SUBDELI = "$$"
        const val DELI_KEY = "__"
        const val W_KEY = "W$DELI_KEY"
        const val M_KEY = "M$DELI_KEY"
        const val NOTICHANID_FOREGROUND = "Uncheck this to hide icon."
        const val NOTICHANID_COMMON = "${Const.APP_NAME} notification"
        const val NOTI_ID_FOREGROUND_SERVICE = 1
        const val NOTI_CNT_START = 1001
        const val NOTI_CNT_END = 1100
        const val NOTI_ID_SUMMARY = NOTI_CNT_END + 1
        const val NOTI_ID_CHK_UNREAD = NOTI_ID_SUMMARY + 1
        //아래에서 대문자는 DB(mysql)에서 내려오는 경우에만 json.get(~)과 KeyChain에서 바로 편리하게 쓰려고 사용하는 것이며 그 외는 소문자임
        const val KC_USERID = "USER_ID"
        const val KC_PWD = "PWD"
        const val KC_USERNM = "USER_NM"
        const val KC_ORGCD = "ORG_CD"
        const val KC_ORGNM = "ORG_NM"
        const val KC_TOPORGCD = "TOP_ORG_CD"
        const val KC_TOPORGNM = "TOP_ORG_NM"
        const val KC_NOTI_OFF = "NOTI_OFF"
        const val KC_BODY_OFF = "BODY_OFF"
        const val KC_SENDER_OFF = "SENDER_OFF"
        const val KC_TM_FR = "TM_FR"
        const val KC_TM_TO = "TM_TO"
        const val KC_AUTOKEY_APP = "AUTOKEY_APP"
        //아래 소문자는 DB에서 내려오는 것이 아님
        const val KC_TOKEN = "token"
        const val KC_USERKEY = "userkey"
        const val KC_AUTOLOGIN = "autologin"
        const val KC_SCREEN_STATE = "screen_state"
        const val KC_WINID = "winid"
        const val KC_USERIP = "userip"
        const val KC_ROOMID_FOR_CHATSERVICE = "roomid_for_chatservice"
        const val KC_WEBVIEW_MAIN_VERSION = "webview_main_version"
        const val KC_WEBVIEW_CHAT_VERSION = "webview_chat_version"
        const val KC_WEBVIEW_POPUP_VERSION = "webview_popup_version"
        const val KC_DT_DISCONNECT = "dt_disconnect"
        /////////////////////////////////////////////////////////////////////////
        const val SOCK_EV_ALERT = "alert"
        const val SOCK_EV_TOAST = "toast"
        const val SOCK_EV_COMMON = "common"
        const val SOCK_EV_CHK_ALIVE = "chk_alive"
        const val SOCK_EV_REFRESH_TOKEN = "refresh_token"
        const val SOCK_EV_SET_ENV = "set_env"
        const val SOCK_EV_SEND_MSG = "send_msg"
        const val SOCK_EV_READ_MSG = "read_msg"
        const val SOCK_EV_RENAME_ROOM = "rename_room"
        const val SOCK_EV_MARK_AS_CONNECT = "mark_as_connect"
        const val SOCK_EV_CHK_ROOMFOCUS = "chk_roomfocus"
        const val SOCK_EV_CHK_TYPING = "chk_typing"
        const val SOCK_EV_STOP_MOBILE = "stop_mobile"
        const val SOCK_EV_GET_ROOMINFO = "get_roominfo"
        /////////////////////////////////////////////////////////////////////////
        const val RESTFUL_TIMEOUT = 5000 //SocketIO.kt(##777)와 HttpFuel.kt 설명 참조. same as web client. 5ms로 하면 HttpFuel~ 오류 발생
        const val RESULT_OK = "0"
        const val RESULT_ERR = "-1"
        const val RESULT_ERR_HTTPFUEL = "-200"
        const val RESULT_TOKEN_EXPIRED = "-84"
        const val RESULT_AUTH_ERR_PREFIX = "-8"
        const val CELL_REVOKED = "message cancelled"
        const val NETWORK_UNAVAILABLE = "네트워크가 연결되어 있지 않습니다."
        const val NETWORK_UNSTABLE = "$TITLE : 네트워크가 원할하지 않거나 서버 작업중입니다."
        /////////////////////////////////////////////////////////////////////////
    }

}