package altermarkive.guardian.core

import altermarkive.guardian.R
import altermarkive.guardian.databinding.MainBinding
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView

class Main : AppCompatActivity() {
    private fun eula(context: Context) {
        // Run the guardian
        Guardian.initiate(this)
        // Load the EULA
        val dialog = Dialog(context)
        dialog.setContentView(R.layout.eula)
        dialog.setTitle("EULA")

        // Buscar el botón con el ID correcto según tu layout
        val acceptButton = dialog.findViewById<View>(R.id.acceptButton) as com.google.android.material.button.MaterialButton
        acceptButton.setOnClickListener {
            // Cerrar el diálogo
            dialog.dismiss()

            // Navegar a la pestaña de Settings
            val navView = findViewById<BottomNavigationView>(R.id.navigation)
            navView.selectedItemId = R.id.settings
        }

        val layout = WindowManager.LayoutParams()
        val window = dialog.window
        window ?: return
        layout.copyFrom(window.attributes)
        layout.width = WindowManager.LayoutParams.MATCH_PARENT
        layout.height = WindowManager.LayoutParams.MATCH_PARENT
        window.attributes = layout
        dialog.show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = MainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val navView: BottomNavigationView = binding.navigation
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.main) as NavHostFragment
        val navController = navHostFragment.navController
        val appBarConfiguration =
            AppBarConfiguration(setOf(R.id.about, R.id.signals, R.id.settings))
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        eula(this)
    }
}