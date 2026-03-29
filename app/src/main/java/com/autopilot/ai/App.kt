package com.autopilot.ai

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.provider.Settings
import com.autopilot.ai.data.db.AppDatabase
import com.autopilot.ai.data.repository.ApiKeyRepository
import com.autopilot.ai.service.AgentOrchestrator
import com.autopilot.ai.service.FloatingOverlayService

class App : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { ApiKeyRepository(database.apiKeyDao()) }
    val orchestrator by lazy { AgentOrchestrator(this, repository) }

    /** True when any AutoPilot activity is in the foreground. */
    var isAppInForeground = false
        private set

    private var activeActivities = 0
    private var overlayStarted = false

    override fun onCreate() {
        super.onCreate()

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                activeActivities++
                isAppInForeground = true
                // App is in foreground → hide bubble
                FloatingOverlayService.hide()
            }

            override fun onActivityPaused(activity: Activity) {
                activeActivities--
                if (activeActivities <= 0) {
                    activeActivities = 0
                    isAppInForeground = false
                    // App went to background → show bubble if overlay is enabled
                    if (overlayStarted && Settings.canDrawOverlays(this@App)) {
                        FloatingOverlayService.show(this@App)
                    }
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    /** Call this when user sends their first message — starts the overlay service. */
    fun enableOverlay() {
        if (!overlayStarted && Settings.canDrawOverlays(this)) {
            overlayStarted = true
            // Don't show immediately — will show when app goes to background
        }
    }
}
