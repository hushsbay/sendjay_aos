package com.hushsbay.sendjay_aos.common

import android.net.Uri
import android.view.View
import androidx.core.view.ContentInfoCompat
import androidx.core.view.OnReceiveContentListener

//https://talkjs.com/resources/support-for-rich-content-in-the-webview/

class ContentReceiver(private val contentReceived: (view: View, uri: Uri) -> Unit) : OnReceiveContentListener {

//    override fun onReceiveContent(view: View, contentInfo: ContentInfoCompat): ContentInfoCompat? {
//        val split = contentInfo.partition { item: ClipData.Item -> item.uri != null }
//        split.first?.let { uriContent ->
//            contentReceived(uriContent.clip.getItemAt(0).uri)
//        }
//        return split.second
//    }

    override fun onReceiveContent(view: View, payload: ContentInfoCompat): ContentInfoCompat? {
        val split = payload.partition { item -> item.uri != null }
        val uriContent = split.first
        val remaining = split.second
        val clip = uriContent.clip
        if (clip.itemCount > 0) {
            val uri = clip.getItemAt(0).uri
            contentReceived(view, uri) //view 추가 $$88
        }
        return remaining
    }

}