package com.autopilot.ai.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

object AppLauncher {
    private const val TAG = "AppLauncher"

    fun launchAppByName(context: Context, appName: String): Boolean {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        for (app in apps) {
            val label = pm.getApplicationLabel(app).toString()
            if (label.contains(appName, ignoreCase = true)) {
                val intent = pm.getLaunchIntentForPackage(app.packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    Log.i(TAG, "Launched: $label (${app.packageName})")
                    return true
                }
            }
        }
        Log.w(TAG, "App not found: $appName")
        return false
    }
}
