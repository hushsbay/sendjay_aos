package com.hushsbay.sendjay_aos

import android.app.Activity
import android.content.Intent
import android.os.Bundle
class SplashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        var intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

}