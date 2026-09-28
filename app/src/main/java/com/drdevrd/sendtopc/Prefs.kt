package com.drdevrd.sendtopc

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    var url: String
        get() = sp.getString("url", "https://files.hshospitalcbe.in") ?: ""
        set(value) {
            sp.edit().putString("url", value).apply()
        }

    var user: String
        get() = sp.getString("user", "asd") ?: ""
        set(value) {
            sp.edit().putString("user", value).apply()
        }

    var pass: String
        get() = sp.getString("pass", "asd") ?: ""
        set(value) {
            sp.edit().putString("pass", value).apply()
        }
}
