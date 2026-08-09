package com.jarvis.assistant.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jarvis.assistant.R
import com.jarvis.assistant.util.pressFeedback

class PermissionsActivity : AppCompatActivity() {

    private lateinit var backBtn: ImageView
    private lateinit var recheckPermissionsBtn: TextView

    private lateinit var micPermissionRow: View
    private lateinit var micPermissionBadge: TextView

    private lateinit var cameraPermissionRow: View
    private lateinit var cameraPermissionBadge: TextView

    private lateinit var contactsPermissionRow: View
    private lateinit var contactsPermissionBadge: TextView

    private lateinit var phonePermissionRow: View
    private lateinit var phonePermissionBadge: TextView

    private lateinit var notifPermissionRow: View
    private lateinit var notifPermissionBadge: TextView

    private lateinit var accessibilityRow: View
    private lateinit var accessibilityPermissionBadge: TextView

    private lateinit var overlayPermissionRow: View
    private lateinit var overlayPermissionBadge: TextView

    private lateinit var openAppDetailsBtn: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)

        initViews()
        wireInteractions()
    }

    override fun onResume() {
        super.onResume()
        checkAllPermissionsStatus(showToast = false)
    }

    private fun initViews() {
        backBtn = findViewById(R.id.backBtn)
        recheckPermissionsBtn = findViewById(R.id.recheckPermissionsBtn)

        micPermissionRow = findViewById(R.id.micPermissionRow)
        micPermissionBadge = findViewById(R.id.micPermissionBadge)

        cameraPermissionRow = findViewById(R.id.cameraPermissionRow)
        cameraPermissionBadge = findViewById(R.id.cameraPermissionBadge)

        contactsPermissionRow = findViewById(R.id.contactsPermissionRow)
        contactsPermissionBadge = findViewById(R.id.contactsPermissionBadge)

        phonePermissionRow = findViewById(R.id.phonePermissionRow)
        phonePermissionBadge = findViewById(R.id.phonePermissionBadge)

        notifPermissionRow = findViewById(R.id.notifPermissionRow)
        notifPermissionBadge = findViewById(R.id.notifPermissionBadge)

        accessibilityRow = findViewById(R.id.accessibilityRow)
        accessibilityPermissionBadge = findViewById(R.id.accessibilityPermissionBadge)

        overlayPermissionRow = findViewById(R.id.overlayPermissionRow)
        overlayPermissionBadge = findViewById(R.id.overlayPermissionBadge)

        openAppDetailsBtn = findViewById(R.id.openAppDetailsBtn)
    }

    private fun wireInteractions() {
        backBtn.pressFeedback()
        backBtn.setOnClickListener { finish() }

        recheckPermissionsBtn.pressFeedback(0.95f)
        recheckPermissionsBtn.setOnClickListener {
            checkAllPermissionsStatus(showToast = true)
        }

        micPermissionRow.pressFeedback(0.98f)
        micPermissionRow.setOnClickListener {
            if (!isPermissionGranted(Manifest.permission.RECORD_AUDIO)) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.RECORD_AUDIO), 201
                )
            } else {
                Toast.makeText(this, "Microphone permission is already granted", Toast.LENGTH_SHORT).show()
            }
        }

        cameraPermissionRow.pressFeedback(0.98f)
        cameraPermissionRow.setOnClickListener {
            if (!isPermissionGranted(Manifest.permission.CAMERA)) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.CAMERA), 202
                )
            } else {
                Toast.makeText(this, "Camera permission is already granted", Toast.LENGTH_SHORT).show()
            }
        }

        contactsPermissionRow.pressFeedback(0.98f)
        contactsPermissionRow.setOnClickListener {
            if (!isPermissionGranted(Manifest.permission.READ_CONTACTS)) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.READ_CONTACTS), 203
                )
            } else {
                Toast.makeText(this, "Contacts permission is already granted", Toast.LENGTH_SHORT).show()
            }
        }

        phonePermissionRow.pressFeedback(0.98f)
        phonePermissionRow.setOnClickListener {
            if (!isPermissionGranted(Manifest.permission.CALL_PHONE) ||
                !isPermissionGranted(Manifest.permission.READ_PHONE_STATE)) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(
                        Manifest.permission.CALL_PHONE,
                        Manifest.permission.READ_PHONE_STATE
                    ), 204
                )
            } else {
                Toast.makeText(this, "Phone & SIM permissions are already granted", Toast.LENGTH_SHORT).show()
            }
        }

        notifPermissionRow.pressFeedback(0.98f)
        notifPermissionRow.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (!isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS)) {
                    ActivityCompat.requestPermissions(
                        this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 205
                    )
                } else {
                    Toast.makeText(this, "Notifications permission is already granted", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Notifications permission is automatically granted on this Android version", Toast.LENGTH_SHORT).show()
            }
        }

        accessibilityRow.pressFeedback(0.97f)
        accessibilityRow.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, "Couldn't open Accessibility settings", Toast.LENGTH_SHORT).show()
            }
        }

        overlayPermissionRow.pressFeedback(0.98f)
        overlayPermissionRow.setOnClickListener {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "Display Over Other Apps is already enabled", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Couldn't open Overlay settings", Toast.LENGTH_SHORT).show()
            }
        }

        openAppDetailsBtn.pressFeedback(0.96f)
        openAppDetailsBtn.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Couldn't open App Settings", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkAllPermissionsStatus(showToast: Boolean = false) {
        var grantedCount = 0
        val totalCount = 7

        // 1. Mic
        val micGranted = isPermissionGranted(Manifest.permission.RECORD_AUDIO)
        updateBadge(micPermissionBadge, micGranted, "GRANTED", "NOT GRANTED")
        if (micGranted) grantedCount++

        // 2. Camera
        val cameraGranted = isPermissionGranted(Manifest.permission.CAMERA)
        updateBadge(cameraPermissionBadge, cameraGranted, "GRANTED", "NOT GRANTED")
        if (cameraGranted) grantedCount++

        // 3. Contacts
        val contactsGranted = isPermissionGranted(Manifest.permission.READ_CONTACTS)
        updateBadge(contactsPermissionBadge, contactsGranted, "GRANTED", "NOT GRANTED")
        if (contactsGranted) grantedCount++

        // 4. Phone
        val phoneGranted = isPermissionGranted(Manifest.permission.CALL_PHONE) &&
                isPermissionGranted(Manifest.permission.READ_PHONE_STATE)
        updateBadge(phonePermissionBadge, phoneGranted, "GRANTED", "NOT GRANTED")
        if (phoneGranted) grantedCount++

        // 5. Notifications
        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }
        updateBadge(notifPermissionBadge, notifGranted, "GRANTED", "NOT GRANTED")
        if (notifGranted) grantedCount++

        // 6. Accessibility
        val accessibilityEnabled = isAccessibilityServiceEnabled()
        updateBadge(accessibilityPermissionBadge, accessibilityEnabled, "ENABLED", "DISABLED")
        if (accessibilityEnabled) grantedCount++

        // 7. Overlay
        val overlayEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
        updateBadge(overlayPermissionBadge, overlayEnabled, "ENABLED", "DISABLED")
        if (overlayEnabled) grantedCount++

        if (showToast) {
            Toast.makeText(
                this,
                "Checked $totalCount permissions: $grantedCount Active / ${totalCount - grantedCount} Pending",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedService = "$packageName/${com.jarvis.assistant.service.JarvisAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val colonSplitter = android.text.TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(expectedService, ignoreCase = true) ||
                componentName.equals("$packageName/.service.JarvisAccessibilityService", ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun updateBadge(badge: TextView, isOk: Boolean, okText: String, failText: String) {
        if (isOk) {
            badge.text = "● $okText"
            badge.setTextColor(ContextCompat.getColor(this, R.color.success))
            badge.setBackgroundResource(R.drawable.bg_chip_pill_success)
        } else {
            badge.text = "● $failText"
            badge.setTextColor(ContextCompat.getColor(this, R.color.danger))
            badge.setBackgroundResource(R.drawable.bg_chip_pill)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        checkAllPermissionsStatus(showToast = false)
    }
}
