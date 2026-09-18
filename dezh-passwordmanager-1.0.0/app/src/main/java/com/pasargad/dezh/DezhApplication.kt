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
}
