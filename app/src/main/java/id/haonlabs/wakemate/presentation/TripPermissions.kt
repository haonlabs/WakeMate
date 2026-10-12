package id.haonlabs.wakemate.presentation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Izin lokasi latar belakang sengaja tidak diminta: pemantauan saat layar mati berjalan lewat
 * foreground service bertipe location yang dimulai selagi aplikasi terbuka, dan service seperti
 * itu tetap boleh membaca lokasi tanpa ACCESS_BACKGROUND_LOCATION. Izin latar belakang membuat
 * versi 1.0 ditolak Play (Prominent Disclosure) padahal tidak dibutuhkan.
 */
object TripPermissions {

    val foreground: Array<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    fun hasForegroundLocation(context: Context) =
        context.isGranted(Manifest.permission.ACCESS_FINE_LOCATION) ||
            context.isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)

    fun hasNotifications(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.isGranted(Manifest.permission.POST_NOTIFICATIONS)

    /**
     * PRD §7.1: HP dengan battery saver agresif (Xiaomi/Oppo/Vivo) bisa membunuh service di tengah
     * jalan. Dibawa ke layar setelan daftar aplikasi, bukan dialog langsung, karena izin
     * REQUEST_IGNORE_BATTERY_OPTIMIZATIONS dibatasi kebijakan Play Store.
     */
    fun isBatteryOptimized(context: Context): Boolean {
        val power = context.getSystemService(PowerManager::class.java) ?: return false
        return !power.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun batteryOptimizationSettings() =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    private fun Context.isGranted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
