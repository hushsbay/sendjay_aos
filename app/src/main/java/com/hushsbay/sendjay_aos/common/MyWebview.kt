package com.hushsbay.sendjay_aos.common

import android.content.Context
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.webkit.WebView
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat

class MyWebView : WebView { //https://talkjs.com/resources/support-for-rich-content-in-the-webview/

    //원래는 웹뷰에서 버튼을 누르면 안드로이드 EditText에라도 포커싱을 시켜서 animated gif를 전송할 수 있도록 해주자는 목표였는데
    //아래 onCreateInputConnection()가 여기 웹뷰 클래스에서도 override되므로 ContentReceiver와도 연계해서
    //사용자 불편없이 웹뷰의 input에서도 바로 animated gif를 선택할 수 있도록 함 (이렇게 구현하지 않으면 animated gif를 누르면 연결할 수 없다는 식으로 나옴
    //activity_main.xml에서 MyWebView로 사용 $$88

    constructor(context: Context) : super(context)
    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet)

    private val MIME_TYPES = arrayOf("image/*") //arrayOf("image/*", "video/*") //MainActivity.kt에서도 동일하게 설정하기로 함

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val inputConnection = super.onCreateInputConnection(outAttrs)
        if (inputConnection == null) return inputConnection
        EditorInfoCompat.setContentMimeTypes(outAttrs, MIME_TYPES)
        return InputConnectionCompat.createWrapper(this, inputConnection, outAttrs)
    }

}