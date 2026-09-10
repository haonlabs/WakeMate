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
 * Izin dipecah bertahap sesuai Android 10+ (Technical Design §8): foreground dulu supaya user
 * paham konteksnya, baru background — meminta keduanya sekaligus justru ditolak sistem.
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

    fun hasBackgroundLocation(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            context.isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

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
