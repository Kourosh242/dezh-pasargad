package com.pasargad.dezh.data.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.pasargad.dezh.domain.update.AppVersionProvider

/** Reads the versionName of the installed package — the single source of truth. */
class PackageAppVersionProvider(context: Context) : AppVersionProvider {

    private val appContext = context.applicationContext

    override val currentVersion: String by lazy {
        val packageName = appContext.packageName
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            appContext.packageManager.getPackageInfo(packageName, 0)
        }
        info.versionName ?: "0.0.0"
    }
}
