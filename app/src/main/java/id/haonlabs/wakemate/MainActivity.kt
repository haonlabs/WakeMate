package id.haonlabs.wakemate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import id.haonlabs.wakemate.presentation.WakeMateNavHost
import id.haonlabs.wakemate.ui.theme.WakeMateTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Temanya gelap tetap, jadi ikon system bar harus terang — deteksi otomatis akan
        // menyimpulkan sebaliknya dan membuatnya nyaris tak terlihat di atas latar navy.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            WakeMateTheme {
                WakeMateNavHost()
            }
        }
    }
}
