package de.meinhelfer.tag

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.core.content.ContextCompat
import de.meinhelfer.tag.ui.HelferApp
import de.meinhelfer.tag.update.AppPrefs
import de.meinhelfer.tag.update.UpdateWorker

class MainActivity : ComponentActivity() {
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Die App ist hell gestaltet: Statusleisten-Symbole dunkel, auch wenn das Handy im Dunkelmodus ist.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Hintergrund-Pruefung auf neue Versionen (kann in den Einstellungen abgeschaltet werden)
        if (AppPrefs.autoUpdateCheck(this)) UpdateWorker.schedule(this) else UpdateWorker.cancel(this)

        setContent {
            MaterialTheme {
                HelferApp()
            }
        }
    }
}
