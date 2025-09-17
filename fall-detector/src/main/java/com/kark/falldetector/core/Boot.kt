package com.kark.falldetector.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent


//Esta funcion hace que apenas se inicie el dispoisitvo, se inicie la app
class Boot : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            Guardian.initiate(context)
        }
    }
}