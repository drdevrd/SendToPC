package com.drdevrd.sendtopc

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Base64
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val prefs = Prefs(this)
        val pad = (16 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(pad, pad, pad, pad)

        val title = TextView(this)
        title.text = "Send to PC"
        title.textSize = 24f
        root.addView(title)

        val info = TextView(this)
        info.text = "In any app: Share > Send to PC. Files and text go straight to your PC file-share server."
        info.setPadding(0, pad / 2, 0, pad)
        root.addView(info)

        val urlField = EditText(this)
        urlField.hint = "Server URL"
        urlField.setText(prefs.url)
        urlField.inputType = InputType.TYPE_TEXT_VARIATION_URI
        root.addView(urlField, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val userField = EditText(this)
        userField.hint = "Username"
        userField.setText(prefs.user)
        root.addView(userField, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val passField = EditText(this)
        passField.hint = "Password"
        passField.setText(prefs.pass)
        passField.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        root.addView(passField, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val saveBtn = Button(this)
        saveBtn.text = "SAVE"
        saveBtn.setOnClickListener {
            prefs.url = urlField.text.toString().trim()
            prefs.user = userField.text.toString().trim()
            prefs.pass = passField.text.toString()
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        }
        root.addView(saveBtn, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val testBtn = Button(this)
        testBtn.text = "TEST CONNECTION"
        testBtn.setOnClickListener {
            val url = urlField.text.toString().trim().trimEnd('/')
            val user = userField.text.toString().trim()
            val pass = passField.text.toString()
            Thread {
                val msg = try {
                    val conn = URL(url + "/").openConnection() as HttpURLConnection
                    conn.connectTimeout = 15000
                    conn.readTimeout = 15000
                    val cred = Base64.encodeToString("$user:$pass".toByteArray(), Base64.NO_WRAP)
                    conn.setRequestProperty("Authorization", "Basic $cred")
                    val code = conn.responseCode
                    conn.disconnect()
                    when (code) {
                        200 -> "Connected OK"
                        401 -> "Wrong username or password"
                        else -> "Server replied $code"
                    }
                } catch (e: Exception) {
                    "Cannot reach server: " + (e.message ?: e.javaClass.simpleName)
                }
                runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
            }.start()
        }
        root.addView(testBtn, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
    }
}
