package com.buttonbox.ble

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.buttonbox.ble.databinding.ActivitySplashBinding
import java.io.BufferedReader
import java.io.InputStreamReader

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    
    companion object {
        private const val SPLASH_DELAY = 2000L
        private const val HOME_LAUNCH_DELAY = 15000L
        private const val PREFS_NAME = "epic_dash_prefs"
        private const val KEY_DISCLAIMER_ACCEPTED = "disclaimer_accepted"
        private const val KEY_LAST_VERSION_CODE = "last_version_code"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Animate logo fade in
        binding.ivLogo.alpha = 0f
        binding.tvAppName.alpha = 0f
        binding.tvTagline.alpha = 0f
        
        binding.ivLogo.animate().alpha(1f).setDuration(800).start()
        binding.tvAppName.animate().alpha(1f).setDuration(800).setStartDelay(400).start()
        binding.tvTagline.animate().alpha(1f).setDuration(800).setStartDelay(600).start()
        
        val launchDelay = if (intent?.hasCategory(Intent.CATEGORY_HOME) == true) {
            HOME_LAUNCH_DELAY
        } else {
            SPLASH_DELAY
        }

        Handler(Looper.getMainLooper()).postDelayed({
            checkDisclaimerAndProceed()
        }, launchDelay)
    }

    private fun checkDisclaimerAndProceed() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val disclaimerAccepted = prefs.getBoolean(KEY_DISCLAIMER_ACCEPTED, false)
        
        if (disclaimerAccepted) {
            checkForUpdateAndProceed()
        } else {
            showDisclaimer()
        }
    }
    
    private fun checkForUpdateAndProceed() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val lastVersionCode = prefs.getLong(KEY_LAST_VERSION_CODE, 0)
        val currentVersionCode = getCurrentVersionCode()
        
        if (lastVersionCode > 0 && currentVersionCode > lastVersionCode) {
            // App was updated - show what's new
            showWhatsNew {
                prefs.edit().putLong(KEY_LAST_VERSION_CODE, currentVersionCode).apply()
                proceedToMain()
            }
        } else {
            // First install or same version
            prefs.edit().putLong(KEY_LAST_VERSION_CODE, currentVersionCode).apply()
            proceedToMain()
        }
    }
    
    private fun getCurrentVersionCode(): Long {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        } catch (e: Exception) {
            0L
        }
    }
    
    private fun showWhatsNew(onDismiss: () -> Unit) {
        val whatsNewText = loadWhatsNewText()
        val versionName = try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            packageInfo.versionName ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
        
        AlertDialog.Builder(this, R.style.DarkAlertDialog)
            .setTitle("🆕 What's New in v$versionName")
            .setMessage(whatsNewText)
            .setPositiveButton("Got it!") { _, _ ->
                onDismiss()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun loadWhatsNewText(): String {
        return try {
            assets.open("whatsnew.txt").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            }
        } catch (e: Exception) {
            "• Bug fixes and performance improvements"
        }
    }

    private fun showDisclaimer() {
        AlertDialog.Builder(this, R.style.DarkAlertDialog)
            .setTitle("⚠️ Off-Road Use Only")
            .setMessage(
                "IMPORTANT DISCLAIMER\n\n" +
                "EpicDash is designed for OFF-ROAD and COMPETITION USE ONLY.\n\n" +
                "• This application is NOT intended for use on public roads\n" +
                "• Do not operate this device while driving\n" +
                "• The driver should never interact with this application while the vehicle is in motion\n" +
                "• Always follow local laws and regulations\n" +
                "• Use at your own risk\n\n" +
                "By continuing, you acknowledge that you understand and accept these terms."
            )
            .setPositiveButton("I Understand & Accept") { _, _ ->
                val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                prefs.edit().putBoolean(KEY_DISCLAIMER_ACCEPTED, true).apply()
                proceedToMain()
            }
            .setNegativeButton("Decline") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun proceedToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}
