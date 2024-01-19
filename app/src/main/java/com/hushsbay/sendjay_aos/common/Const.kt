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
        const val RESULT_OK = "0"
        const val RESULT_ERR = "-1"
        const val WAIT_FOR_RECONNECT = "Please wait until network reconnected."
        ////////////////////////////////////////////////
        const val PROVIDER_AUTHORITY = "com.hushsbay.sendjay.common.KeyChainProvider" //same as ContentProvider class name and <provider ~ android:authorities in AndroidManifest.xml
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
        /////////////////////////////////////////////////////////////////////////
    }

}