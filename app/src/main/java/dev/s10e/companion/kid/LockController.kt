package dev.s10e.companion.kid

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.util.Log

class LockController(private val activity: Activity) {

    fun isDeviceOwner(): Boolean {
        val dpm = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isDeviceOwnerApp(activity.packageName)
    }

    fun enterLockMode() {
        val dpm = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminName = ComponentName(activity, CompanionAdminReceiver::class.java)

        try {
            if (dpm.isDeviceOwnerApp(activity.packageName)) {
                // Device Owner mode
                dpm.setLockTaskPackages(adminName, arrayOf(activity.packageName))
                activity.startLockTask()
            } else {
                // Screen pinning mode
                activity.startLockTask()
                Log.i("S10eCompanion", "Setup note: Zum Lösen Bildschirmsperre verlangen")
            }
        } catch (e: Exception) {
            Log.e("S10eCompanion", "Fehler beim Starten von LockTask", e)
        }
    }

    fun exitLockMode() {
        try {
            activity.stopLockTask()
        } catch (e: Exception) {
            Log.e("S10eCompanion", "Fehler beim Beenden von LockTask", e)
        }
    }

    fun clearDeviceOwnerApp() {
        val dpm = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (dpm.isDeviceOwnerApp(activity.packageName)) {
            dpm.clearDeviceOwnerApp(activity.packageName)
        }
    }
}
