package altermarkive.guardian.core

import altermarkive.guardian.storage.Upload
import altermarkive.guardian.utils.Log
import android.content.BroadcastReceiver
import android.content.Intent
import android.net.ConnectivityManager
import android.content.Context
//Sube los datos a internet
//detecta si hay internet y si es asi comienza la subida de datos
class Connectivity : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        @Suppress("DEPRECATION")
        if (ConnectivityManager.CONNECTIVITY_ACTION == intent.action) {
            val manager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val info = manager.activeNetworkInfo
            if (info != null && info.isConnected) {
                Log.i(TAG, "Detected internet connectivity")
                Upload.go(context.applicationContext, context.applicationContext.filesDir.path)
            }
        }
    }

    companion object {
        private val TAG = Connectivity::class.java.name
    }
}