package com.buttonbox.ble

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/** One-purpose SAF host used only to re-authorize a recovery bundle after reinstall. */
class TunerRecoveryActivity : AppCompatActivity() {
    private val openRecoveryBundle = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) {
            Toast.makeText(this, "Tuner project restore cancelled", Toast.LENGTH_SHORT).show()
            finish()
            return@registerForActivityResult
        }

        val result = TunerRecoveryBundleStore.importBundle(this, uri)
        Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
        if (result.success) {
            startActivity(
                Intent(this, DashboardLabActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            openRecoveryBundle.launch(
                arrayOf("application/json", "text/json", "text/plain", "application/octet-stream")
            )
        }
    }
}
