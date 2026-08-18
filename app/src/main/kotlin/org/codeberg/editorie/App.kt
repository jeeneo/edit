package org.codeberg.editorie

// SPDX-License-Identifier: MIT

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import org.codeberg.editorie.data.UserPreferences

class App : Application() {
    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var ctx: Context
            private set
        val prefs: UserPreferences by lazy { UserPreferences(ctx) }
    }

    override fun onCreate() {
        super.onCreate()
        ctx = applicationContext
    }
}
