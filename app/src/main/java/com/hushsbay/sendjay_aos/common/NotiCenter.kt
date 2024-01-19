package com.hushsbay.sendjay_aos.common

class Const {

    enum class ServiceState {
        STOPPED, LOGOUTED, RUNNING
    }

    enum class SockState(num: Int) {
        BEFORE_CONNECT(0), FIRST_DISCONNECTED(2), RECONNECTED(4)
    }

    companion object {
        const val TITLE = "sendjay" //for AlertDialog Title and so on
        const val RESULT_OK = "0"
        const val RESULT_ERR = "-1"
        const val WAIT_FOR_RECONNECT = "Please wait until network reconnected."
        const val NOTI_ID_FOREGROUND_SERVICE = 1
    }

}