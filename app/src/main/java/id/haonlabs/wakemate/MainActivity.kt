package id.haonlabs.wakemate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import id.haonlabs.wakemate.presentation.WakeMateNavHost
import id.haonlabs.wakemate.ui.theme.WakeMateTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Tema mengikuti mode gelap/terang sistem, jadi warna ikon system bar dideteksi otomatis.
        enableEdgeToEdge()
        setContent {
            WakeMateTheme {
                WakeMateNavHost()
            }
        }
    }
}
