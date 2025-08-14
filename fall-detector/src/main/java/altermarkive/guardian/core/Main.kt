package altermarkive.guardian.core

import altermarkive.guardian.R
import altermarkive.guardian.databinding.MainBinding
import altermarkive.guardian.alerts.FallAlertActivity
import altermarkive.guardian.alerts.FallCountdownService
import android.app.Dialog
import android.content.Context
import android.content.Intent
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
        
        // *** VERIFICAR SI HAY COUNTDOWN EN PROGRESO ANTES DE MOSTRAR LA APP ***
        val (isCountdownActive, remainingSeconds) = FallCountdownService.getCountdownStatus()
        
        if (isCountdownActive) {
            // Hay un countdown en progreso, lanzar FallAlertActivity inmediatamente
            val intent = Intent(this, FallAlertActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("FROM_MAIN_ACTIVITY", true)
            }
            startActivity(intent)
            // No mostrar la interfaz principal, el usuario debe ver la alerta
            finish()
            return
        }
        
        val binding = MainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val navView: BottomNavigationView = binding.navigation
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.main) as NavHostFragment
        val navController = navHostFragment.navController
        val appBarConfiguration =
            AppBarConfiguration(setOf(R.id.about, R.id.signals, R.id.settings,R.id.safezone))
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        eula(this)
    }
    
    override fun onResume() {
        super.onResume()
        
        // Verificar de nuevo por si se inició un countdown mientras la app estaba en primer plano
        val (isCountdownActive, remainingSeconds) = FallCountdownService.getCountdownStatus()
        
        if (isCountdownActive) {
            // Hay un countdown en progreso, lanzar FallAlertActivity
            val intent = Intent(this, FallAlertActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("FROM_MAIN_ACTIVITY", true)
            }
            startActivity(intent)
        }
    }
}