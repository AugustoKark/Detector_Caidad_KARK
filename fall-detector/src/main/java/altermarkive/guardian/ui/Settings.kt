package altermarkive.guardian.ui

import altermarkive.guardian.R
import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat

class Settings : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }
}