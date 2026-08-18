package org.codeberg.editorie

// SPDX-License-Identifier: MIT

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import org.codeberg.editorie.data.AppTheme
import org.codeberg.editorie.data.EditorTheme
import org.codeberg.editorie.data.LocalAppTheme
import org.codeberg.editorie.data.LocalOnThemeChange
import org.codeberg.editorie.ui.editorscreen.EditorScreen

class MainActivity : ComponentActivity() {
    var pendingIntent: Intent by mutableStateOf(intent)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        pendingIntent = intent
        setContent {
            val systemDark = isSystemInDarkTheme()
            val theme = remember { mutableStateOf(App.prefs.loadAppTheme()) }
            CompositionLocalProvider(
                LocalAppTheme provides theme.value,
                LocalOnThemeChange provides { newTheme ->
                    theme.value = newTheme; App.prefs.saveAppTheme(newTheme)
                },
            ) {
                EditorTheme(
                    darkTheme = when (theme.value) {
                        AppTheme.Dynamic -> systemDark
                        AppTheme.OLED, AppTheme.Dark -> true
                        AppTheme.Light -> false
                    },
                    dynamicColor = theme.value == AppTheme.Dynamic,
                    oledTheme = theme.value == AppTheme.OLED,
                ) { EditorScreen(incomingIntent = pendingIntent) }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = "package:org.codeberg.editorie".toUri()
                }
                startActivity(intent)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingIntent = intent
    }
}
