package org.codeberg.editorie.util

import android.content.Context
import android.widget.Toast
import org.codeberg.editorie.App

object AppToasts {
    private var toast: Toast? = null

    fun show(message: String, duration: Int = Toast.LENGTH_SHORT) {
        toast?.cancel()
        toast = Toast.makeText(App.ctx, message, duration).apply { show() }
    }

    fun show(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        toast?.cancel()
        toast = Toast.makeText(context, message, duration).apply { show() }
    }
}
