package com.drdevrd.sendtopc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Base64
import android.widget.Toast
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UploadService : Service() {

    private val channelId = "upload"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(channelId, "Uploads", NotificationManager.IMPORTANCE_LOW)
        )

        val first = buildNotification("Sending to PC...")
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, first)
        }

        val uris = ArrayList<Uri>()
        val clip = intent?.clipData
        if (clip != null) {
            for (i in 0 until clip.itemCount) {
                val u = clip.getItemAt(i).uri
                if (u != null) uris.add(u)
            }
        }
        val text = intent?.getStringExtra("text")

        Thread {
            runUploads(uris, text)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }.start()

        return START_NOT_STICKY
    }

    private fun buildNotification(msg: String): Notification {
        return Notification.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Send to PC")
            .setContentText(msg)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(msg: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(1, buildNotification(msg))
    }

    private fun toast(msg: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show()
        }
    }

    private fun runUploads(uris: List<Uri>, text: String?) {
        val prefs = Prefs(this)
        val total = uris.size + (if (text.isNullOrEmpty()) 0 else 1)
        var done = 0
        var lastError = ""

        for (uri in uris) {
            val name = displayName(uri)
            updateNotification("Sending " + (done + 1) + "/" + total + ": " + name)
            try {
                val stream = contentResolver.openInputStream(uri)
                if (stream == null) {
                    lastError = "Cannot open $name"
                } else {
                    stream.use { upload(prefs, name, it) }
                    done++
                }
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
            }
        }

        if (!text.isNullOrEmpty()) {
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            updateNotification("Sending text")
            try {
                val bytes = text.toByteArray(Charsets.UTF_8)
                bytes.inputStream().use { upload(prefs, "text-$stamp.txt", it) }
                done++
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
            }
        }

        if (done == total) {
            toast("Sent $done to PC")
        } else {
            toast("Sent $done of $total. Error: $lastError")
        }
    }

    private fun displayName(uri: Uri): String {
        var name: String? = null
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) name = c.getString(0)
            }
        } catch (e: Exception) {
            // ignore, fall back below
        }
        if (name.isNullOrEmpty()) {
            name = uri.lastPathSegment ?: ("file-" + System.currentTimeMillis())
        }
        return name!!.replace("\"", "_").replace("\r", "_").replace("\n", "_")
    }

    private fun upload(prefs: Prefs, name: String, input: InputStream) {
        val boundary = "----SendToPC" + System.currentTimeMillis()
        val conn = URL(prefs.url.trim().trimEnd('/') + "/upload").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 20000
        conn.readTimeout = 180000
        conn.setChunkedStreamingMode(65536)
        val cred = Base64.encodeToString((prefs.user + ":" + prefs.pass).toByteArray(), Base64.NO_WRAP)
        conn.setRequestProperty("Authorization", "Basic $cred")
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

        conn.outputStream.use { out ->
            val head = "--$boundary\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"$name\"\r\n" +
                "Content-Type: application/octet-stream\r\n\r\n"
            out.write(head.toByteArray(Charsets.UTF_8))
            input.copyTo(out, 65536)
            out.write(("\r\n--$boundary--\r\n").toByteArray(Charsets.UTF_8))
            out.flush()
        }

        val code = conn.responseCode
        conn.disconnect()
        if (code == 401) throw Exception("Wrong username or password")
        if (code !in 200..299) throw Exception("Server replied $code")
    }
}
