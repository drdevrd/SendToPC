package com.drdevrd.sendtopc

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast

class ShareActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uris = ArrayList<Uri>()
        var text: String? = null

        if (intent.action == Intent.ACTION_SEND) {
            val single: Uri? = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            if (single != null) {
                uris.add(single)
            } else {
                text = intent.getStringExtra(Intent.EXTRA_TEXT)
            }
        } else if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            val list: ArrayList<Uri>? = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            }
            if (list != null) uris.addAll(list)
        }

        if (uris.isEmpty() && text.isNullOrEmpty()) {
            Toast.makeText(this, "Nothing to send", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val svc = Intent(this, UploadService::class.java)
        if (uris.isNotEmpty()) {
            val clip = ClipData.newRawUri("files", uris[0])
            for (i in 1 until uris.size) {
                clip.addItem(ClipData.Item(uris[i]))
            }
            svc.clipData = clip
            svc.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (!text.isNullOrEmpty()) {
            svc.putExtra("text", text)
        }
        startForegroundService(svc)
        finish()
    }
}
