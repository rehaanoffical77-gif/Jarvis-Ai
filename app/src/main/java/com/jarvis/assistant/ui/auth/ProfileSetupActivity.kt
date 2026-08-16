package com.jarvis.assistant.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.jarvis.assistant.R
import com.jarvis.assistant.ui.main.MainActivity
import com.jarvis.assistant.ui.main.OrbAnimationView

class ProfileSetupActivity : AppCompatActivity() {

    private lateinit var firebaseAuth: FirebaseAuth

    private lateinit var profileOrbView: OrbAnimationView
    private lateinit var nameInput: EditText
    private lateinit var phoneInput: EditText
    private lateinit var saveProfileBtn: FrameLayout
    private lateinit var saveProgressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_setup)

        firebaseAuth = FirebaseAuth.getInstance()

        profileOrbView = findViewById(R.id.profileOrbView)
        nameInput = findViewById(R.id.nameInput)
        phoneInput = findViewById(R.id.phoneInput)
        saveProfileBtn = findViewById(R.id.saveProfileBtn)
        saveProgressBar = findViewById(R.id.saveProgressBar)

        profileOrbView.setState(OrbAnimationView.OrbState.LOGIN)
        profileOrbView.setAmplitude(0.35f)

        prepopulateData()

        saveProfileBtn.setOnClickListener {
            validateAndSaveProfile()
        }
    }

    private fun prepopulateData() {
        val prefs = getSharedPreferences("jarvis_prefs", MODE_PRIVATE)

        if (prefs.getBoolean("is_profile_complete", false)) {
            launchMainActivity()
            return
        }

        val savedName = prefs.getString("user_name", "") ?: ""
        val firebaseName = firebaseAuth.currentUser?.displayName ?: ""
        val nameToSet = if (savedName.isNotBlank()) savedName else firebaseName
        if (nameToSet.isNotBlank() && nameToSet != "Jarvis User") {
            nameInput.setText(nameToSet)
        }

        val savedPhone = prefs.getString("user_phone", "") ?: ""
        if (savedPhone.isNotBlank()) {
            phoneInput.setText(savedPhone)
        }
    }

    private fun launchMainActivity() {
        val intent = Intent(this@ProfileSetupActivity, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun validateAndSaveProfile() {
        val name = nameInput.text.toString().trim()
        val phone = phoneInput.text.toString().trim()

        if (name.isBlank()) {
            nameInput.error = "Please enter your name"
            nameInput.requestFocus()
            return
        }

        if (phone.isBlank()) {
            phoneInput.error = "Please enter your phone number"
            phoneInput.requestFocus()
            return
        }

        saveProgressBar.visibility = View.VISIBLE
        saveProfileBtn.isEnabled = false

        val currentUser = firebaseAuth.currentUser
        val prefs = getSharedPreferences("jarvis_prefs", MODE_PRIVATE)

        // 1. Immediately Save to Local SharedPreferences
        prefs.edit()
            .putString("user_name", name)
            .putString("user_phone", phone)
            .putBoolean("is_profile_complete", true)
            .apply()

        // 2. Update Firebase Auth Profile in Background
        if (currentUser != null) {
            try {
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(name)
                    .build()
                currentUser.updateProfile(profileUpdates)
            } catch (e: Exception) {
                Log.w("ProfileSetupActivity", "Failed to update profile", e)
            }
        }

        saveProgressBar.visibility = View.GONE
        Toast.makeText(this@ProfileSetupActivity, "Profile saved successfully!", Toast.LENGTH_SHORT).show()

        // 3. Instantly Launch MainActivity
        launchMainActivity()
    }

    override fun onResume() {
        super.onResume()
        profileOrbView.onResume()
    }

    override fun onPause() {
        super.onPause()
        profileOrbView.onPause()
    }
}
