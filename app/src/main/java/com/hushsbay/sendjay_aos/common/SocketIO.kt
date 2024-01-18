package com.hushsbay.sendjay_aos.common

class Const {

    enum class ServiceState {
        STOPPED, LOGOUTED, RUNNING
    }

    enum class SockState(num: Int) {
        BEFORE_CONNECT(0), FIRST_DISCONNECTED(2), RECONNECTED(4)
    }

}