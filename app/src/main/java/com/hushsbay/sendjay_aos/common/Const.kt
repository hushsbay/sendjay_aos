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
        const val VERSIONCHK_WEB = "webview"
        const val VERSIONCHK_ETC = "etc"
        ////////////////////////////////////////////////for distinguishing dev and ops (through KeyChain when app starts)
//        const val SUFFIX_DEV = "devx" //for developer to check by userid if he want to connect to dev mode not ops(production) mode
        const val URL_HOST = "https://hushsbay.com"
        const val DIR_PUBLIC = "/app/msngr"
        //const val DIR_ROUTE = "/msngr"
        ////////////////////////////////////////////////
        const val URL_SERVER = "$URL_HOST:444" //444(https) -> 81(http) on AWS
        const val URL_SOCK = "$URL_HOST:3051/jay" //3051(https) -> 3051(http) on AWS
        //const val URL_SERVER = "$URL_HOST:443" //444(https) -> 81(http) on AWS
        //const val URL_SOCK = "$URL_HOST:3050/jay" //3051(https) -> 3051(http) on AWS
        const val URL_PUBLIC = URL_SERVER + DIR_PUBLIC
        //3050(web ops),3051(mobile ops) and 3060(web dev),3061(mobile dev)
        //서버 1대로 개발과 운영을 나누어 관리하기 위한 포트이며 정상적인 로드밸런싱이 필요한 실제 기업 환경에서는 사내 담당자와의 협의가 필요할 것임.
        ////////////////////////////////////////////////
        const val URL_JAY = "${DIR_PUBLIC}/" //means public page load
        //const val URL_ROUTE = "${DIR_ROUTE}/" //means restful(=ajax)
        ////////////////////////////////////////////////
        const val PAGE_MAIN = "/main.html"
        const val PAGE_ROOM = "/chat.html"
        const val PAGE_DUMMY = "/dummy.html"
        ////////////////////////////////////////////////
        const val PROVIDER_AUTHORITY = "com.hushsbay.sendjay_aos.common.KeyChainProvider" //same as ContentProvider class name and <provider ~ android:authorities in AndroidManifest.xml
        const val DELI = "##"
        const val SUBDELI = "$$"
        const val DELI_KEY = "__"
        const val W_KEY = "W$DELI_KEY"
        const val M_KEY = "M$DELI_KEY"
        const val NOTICHANID_FOREGROUND = "Uncheck this to hide icon."
        const val NOTICHANID_COMMON = "${Const.APP_NAME} notification"
        const val NOTI_ID_FOREGROUND_SERVICE = 1
        const val NOTI_CNT_START = 1001
        const val NOTI_CNT_END = 1010
        const val NOTI_ID_SUMMARY = NOTI_CNT_END + 1
        const val NOTI_ID_CHK_UNREAD = NOTI_ID_SUMMARY + 1
        ///////// 아래에서 대문자는 DB(mysql)에서 내려오는 경우에만 json.get(~)과 KeyChain에서 바로 편리하게 쓰려고 사용하는 것이며 그 외는 소문자임
        const val KC_USERID = "USER_ID" //used for KeyChain, UserInfo and login.js / 서버에서도 이름이 동일해야 함
        const val KC_PWD = "PWD" //used for KeyChain, UserInfo and login.js / 서버에서도 이름이 동일해야 함
        const val KC_USERNM = "USER_NM" //used for KeyChain, UserInfo and login.js / 서버에서도 이름이 동일해야 함
        const val KC_PASSKEY = "PASSKEY"
        const val KC_ORGCD = "ORG_CD" //used for KeyChain, UserInfo and login.js / 서버에서도 이름이 동일해야 함
        const val KC_ORGNM = "ORG_NM" // ""
        const val KC_TOPORGCD = "TOP_ORG_CD" // ""
        const val KC_TOPORGNM = "TOP_ORG_NM" // ""
        const val KC_NOTI_OFF = "NOTI_OFF"
        const val KC_SOUND_OFF = "SOUND_OFF"
        const val KC_TM_FR = "TM_FR"
        const val KC_TM_TO = "TM_TO"
        const val KC_BODY_OFF = "BODY_OFF"
        const val KC_SENDER_OFF = "SENDER_OFF"
        const val KC_TOKEN = "token" //소문자
        const val KC_USERKEY = "userkey"
        const val KC_AUTOLOGIN = "autologin"
        const val KC_SCREEN_STATE = "screen_state"
        const val KC_WINID = "winid"
        const val KC_USERIP = "userip"
        const val KC_ROOMID_FOR_CHATSERVICE = "roomid_for_chatservice"
        const val KC_WEBVIEW_MAIN_VERSION = "webview_main_version"
        const val KC_WEBVIEW_CHAT_VERSION = "webview_chat_version"
        const val KC_WEBVIEW_POPUP_VERSION = "webview_popup_version"
        /////////////////////////////////////////////////////////////////////////
        const val SOCK_EV_ALERT = "alert"
        const val SOCK_EV_TOAST = "toast"
        const val SOCK_EV_COMMON = "common"
        const val SOCK_EV_SET_ENV = "set_env"
        const val SOCK_EV_SEND_MSG = "send_msg"
        const val SOCK_EV_READ_MSG = "read_msg"
        const val SOCK_EV_RENAME_ROOM = "rename_room"
        const val SOCK_EV_CUT_MOBILE = "cut_mobile"
        const val SOCK_EV_MARK_AS_CONNECT = "mark_as_connect"
        /////////////////////////////////////////////////////////////////////////
        const val RESTFUL_TIMEOUT = 10000 //same as web client
        const val RESULT_OK = "0"
        const val RESULT_ERR = "-1"
        const val RESULT_ERR_HTTPFUEL = "-200"
        const val CELL_REVOKED = "message cancelled"
        const val CHECK_MOBILE_NETWORK = "$TITLE: No network available.\nCheck mobile network or wifi."
        const val WAIT_FOR_RECONNECT = "Please wait until network reconnected."
    }

}