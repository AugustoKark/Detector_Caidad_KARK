package com.kark.falldetector.storage

import com.kark.falldetector.utils.Log
import com.kark.falldetector.alerts.Contact
import com.kark.falldetector.alerts.Messenger
import android.content.Context
import io.ipfs.api.IPFS
import io.ipfs.api.NamedStreamable
import io.ipfs.multiaddr.MultiAddress
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.Executors


//Este archivo es el encargado de comprimir, y subir los datos
class Upload internal constructor() {
    companion object {
        private val TAG = Upload::class.java.name
        private var ipfs: IPFS? = null

        internal fun go(context: Context, root: String) {
            val ipfs = ipfs
            ipfs ?: return
            val zipped: Array<String>? = Storage.list(root, Storage.FILTER_ZIP)
            if (zipped != null && zipped.isNotEmpty()) {
                Arrays.sort(zipped)
                for (file in zipped) {
                    try {
                        val filePath = File(root, file).absolutePath
                        ServerAdapter.uploadFile(context, filePath)
                    } catch (exception: IOException) {
                        val failure = android.util.Log.getStackTraceString(exception)
                        Log.e(TAG, "Failed to upload the file $file:\n $failure")
                    }
                }
            }
        }
    }

    init {
        if (ipfs == null) {
            Executors.newSingleThreadExecutor().execute {
            }
        }
    }
}