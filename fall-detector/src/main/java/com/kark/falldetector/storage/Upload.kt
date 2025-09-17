package com.kark.falldetector.storage

import com.kark.falldetector.utils.Log
import android.content.Context

object Upload {
    private val TAG = Upload::class.java.name

    internal fun go(context: Context, root: String) {
        // No subimos archivos para cumplir con políticas de privacidad
        Log.i(TAG, "File upload disabled - no external data transmission")
    }
}