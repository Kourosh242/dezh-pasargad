package com.pasargad.dezh

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.pasargad.dezh.di.AppContainer
import kotlinx.coroutines.launch

/**
 * Application entry: builds the DI container, resolves the initial lock state
 * and wires app-level foreground/background transitions to the auto-lock policy.
 * No secrets are ever created or held here.
 */
class DezhApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        installCrashBreadcrumb()
        container = AppContainer(this)

        container.applicationScope.launch {
            container.securityRepository.initialize()
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    container.autoLockController.onAppForegrounded()
                }

                override fun onStop(owner: LifecycleOwner) {
                    container.autoLockController.onAppBackgrounded()
                }
            },
        )

        // Screen off applies the same auto-lock policy as backgrounding.
        val screenOffReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                    container.autoLockController.onScreenOff()
                }
            }
        }
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    /**
     * Diagnostics: on any uncaught crash the stack trace is appended to a
     * user-accessible file (Android/data/com.pasargad.dezh/files/) so a failure
     * can be reported without adb. Best-effort only; never replaces the
     * system handler.
     */
    private fun installCrashBreadcrumb() {
        val systemHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val dir = getExternalFilesDir(null) ?: return@runCatching
                java.io.File(dir, "dezh-crash-log.txt").appendText(
                    buildString {
                        append("\n==== ")
                        append(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date()))
                        append(" ====")
                        append("\nthread: ").append(thread.name)
                        append("\n").append(android.util.Log.getStackTraceString(throwable))
                    },
                )
            }
            systemHandler?.uncaughtException(thread, throwable)
        }
    }
}
