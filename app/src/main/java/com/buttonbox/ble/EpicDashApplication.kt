package com.buttonbox.ble

import android.app.Application

/** Starts the permanent native Tuner project and its single reinstall-recovery mirror. */
class EpicDashApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        TunerPermanentProjectStore.initialize(this)
        TunerRecoveryBundleStore.startMirroring(this)
    }
}
