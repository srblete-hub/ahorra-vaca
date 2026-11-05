package com.caudal.finanzas

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.caudal.finanzas.ui.CaudalTheme
import com.caudal.finanzas.ui.MainScreen
import com.caudal.finanzas.ui.auth.LockScreen
import com.caudal.finanzas.ui.auth.SetupScreen

/** FragmentActivity porque BiometricPrompt lo necesita. */
class MainActivity : FragmentActivity() {
    private val appVm: AppViewModel by viewModels()
    private var backgroundedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideStatusBar()
        setContent {
            CaudalTheme {
                val hasAccount by appVm.hasAccount.collectAsStateWithLifecycle()
                val unlocked by appVm.unlocked.collectAsStateWithLifecycle()
                val settings by appVm.settings.collectAsStateWithLifecycle()
                // Bloquear capturas es opcional (Ajustes → Seguridad).
                androidx.compose.runtime.LaunchedEffect(settings.blockScreenshots) {
                    if (settings.blockScreenshots) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                // Surface da el fondo y el color de texto del tema a las pantallas sin Scaffold.
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when {
                        !hasAccount -> SetupScreen(appVm, this)
                        !unlocked -> LockScreen(appVm, this, settings.userName, settings.biometricEnabled)
                        else -> MainScreen(appVm, this)
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) backgroundedAt = SystemClock.elapsedRealtime()
    }

    /** Si la app ha estado en segundo plano más de 30 s, vuelve a pedir huella o PIN. */
    override fun onStart() {
        super.onStart()
        if (backgroundedAt != 0L && SystemClock.elapsedRealtime() - backgroundedAt > RELOCK_AFTER_MS) appVm.lock()
        backgroundedAt = 0L
    }

    private companion object {
        const val RELOCK_AFTER_MS = 30_000L
    }

    /** La app ocupa toda la pantalla: la barra de la hora se esconde (deslizando desde arriba se ve un momento). */
    private fun hideStatusBar() {
        // Sin esto, con la barra escondida Android deja una franja negra en la zona de la cámara.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R)
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                else android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideStatusBar()
    }
}
