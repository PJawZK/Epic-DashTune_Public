package com.buttonbox.ble

import android.content.Context
import android.content.Intent
import android.webkit.JavascriptInterface

/** Opens the user-authorized recovery picker; owns no tuning or ECU authority. */
class TunerRecoveryBridge(context: Context) {
    private val appContext = context.applicationContext

    @JavascriptInterface
    fun openRecoveryBundle(): Boolean = runCatching {
        appContext.startActivity(
            Intent(appContext, TunerRecoveryActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.getOrDefault(false)

    @JavascriptInterface
    fun diagnostics(): String = TunerRecoveryBundleStore.diagnostics(appContext)
}
