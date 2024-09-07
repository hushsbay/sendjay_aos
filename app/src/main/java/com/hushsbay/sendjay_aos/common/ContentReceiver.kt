package com.hushsbay.sendjay_aos.common

import android.content.ClipData
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.view.View
import androidx.core.view.ContentInfoCompat
import androidx.core.view.OnReceiveContentListener
import java.util.Base64

//https://talkjs.com/resources/support-for-rich-content-in-the-webview/

class ContentReceiver(private val contentReceived: (uri: Uri) -> Unit) : OnReceiveContentListener {

    override fun onReceiveContent(view: View, contentInfo: ContentInfoCompat): ContentInfoCompat? {
        val split = contentInfo.partition { item: ClipData.Item -> item.uri != null }
        split.first?.let { uriContent ->
            contentReceived(uriContent.clip.getItemAt(0).uri)
        }
        return split.second
    }

//    private fun getFileName(contentResolver: ContentResolver, uri: Uri): String? {
//        val cursor = contentResolver.query(uri, null, null, null, null, null)
//        return cursor?.use {
//            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
//            it.moveToFirst()
//            cursor.getString(nameIndex)
//        }
//    }

//    override fun onReceiveContent(view: View, payload: ContentInfoCompat): ContentInfoCompat? {
//        val split = payload.partition { item -> item.uri != null }
//        val uriContent = split.first
//        val remaining = split.second
//
//        val clip = uriContent.clip
//        if (clip.itemCount > 0) {
//            val contentResolver = view.context.contentResolver
//
//            val uri = clip.getItemAt(0).uri
//            val fileName = getFileName(contentResolver, uri)
//            val mimeType = contentResolver.getType(uri)
//
//            contentReceived(uri, fileName!!, mimeType!!)
//        }
//        return remaining
//    }

//
//    override fun onReceiveContent(view: View, payload: ContentInfoCompat): ContentInfoCompat? {
//        val split = payload.partition { item -> item.uri != null }
//        val uriContent = split.first
//        val remaining = split.second
//
//        val clip = uriContent.clip
//        if (clip.itemCount > 0) {
//            val contentResolver = view.context.contentResolver
//
//            val uri = clip.getItemAt(0).uri
//            val mimeType = contentResolver.getType(uri)
//            val fileName = getFileName(contentResolver, uri)
//
//            val bufferedInputStream = contentResolver.openInputStream(uri)?.buffered()
//            val javaScript = bufferedInputStream.use {
//                val encoder: Base64.Encoder = Base64.getEncoder()
//                val jsonByteArray = encoder.encodeToString(it?.readBytes())
//
//
//
//                """
//                var byteArray = new Int8Array($jsonByteArray);
//                var mediaFile = new File([byteArray], "$fileName", { type: "$mimeType" });
//                window.chatbox.sendFile(mediaFile);
//                """.trimIndent()
//
//            }
//
////            val myWebView = view as MyWebView
////            myWebView.post {
////                myWebView.evaluateJavascript(javaScript, null)
////            }
//        }
//        return remaining
//    }

}