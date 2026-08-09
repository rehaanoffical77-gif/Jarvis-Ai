package com.jarvis.assistant.ui.settings

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.telecom.PhoneAccountHandle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.jarvis.assistant.JarvisApplication
import com.jarvis.assistant.R
import com.jarvis.assistant.util.AnimUtils
import com.jarvis.assistant.util.EnvLoader
import com.jarvis.assistant.util.SimManager
import com.jarvis.assistant.util.pressFeedback
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import android.util.Log

class SettingsActivity : AppCompatActivity() {

    private lateinit var geminiKeyContainer: View
    private lateinit var apiKeyLabel: TextView
    private lateinit var apiKeyInput: EditText
    private lateinit var apiKeyVisibilityToggle: ImageButton
    private lateinit var userNameInput: EditText
    private lateinit var activeEngineTitleText: TextView
    private lateinit var activeEngineSubtext: TextView
    private lateinit var activeEngineBadge: TextView
    private lateinit var geminiVoiceChipsRow: LinearLayout
    private lateinit var simSpinner: Spinner
    private lateinit var geminiVoiceLabel: View
    private lateinit var permissionsBar: View
    private lateinit var personalitySegmented: SegmentedControl
    private lateinit var personalityDescriptionText: TextView
    private lateinit var saveButton: TextView
    private lateinit var backBtn: View

    private var isApiKeyVisible = false
    private var selectedVoiceIndex = 0
    private var selectedModelIndex = 0
    private var selectedPersonalityIndex = 0
    private var selectedSimIndex = 0 // 0 = "Always ask"

    private val defaultGeminiModel = "models/gemini-3.1-flash-live-preview"

    private val geminiModelLabels = listOf(
        "Gemini 3.1 Flash Live (Native Audio)",
        "Gemini 2.5 Flash Live (Native Audio)"
    )
    private val geminiModelValues = listOf(
        "models/gemini-3.1-flash-live-preview",
        "models/gemini-2.5-flash-native-audio-preview-12-2025"
    )

    private val voiceLabels = listOf(
        "Kore", "Puck", "Charon", "Fenrir", "Zephyr", "Aoede", "Leda", "Orus"
    )
    private val voiceValues = voiceLabels

    private val personalityDescriptions = listOf(
        "Lumina AI — Warm best friend tone, natural human flow with conversational pauses. 💖",
        "Warm, caring Hinglish companion with expressive replies. 💖",
        "Formal, precise English only. No emojis, straight to the point. 💼",
        "Friendly Hinglish/English mix — balanced and helpful. 🤖"
    )

    private var callCapableSims: List<SimManager.SimOption> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        initViews()
        loadPrefs()
        wireInteractions()
    }



    private fun initViews() {
        geminiKeyContainer = findViewById(R.id.geminiKeyContainer)
        apiKeyLabel = findViewById(R.id.apiKeyLabel)
        apiKeyInput = findViewById(R.id.apiKeyInput)
        apiKeyVisibilityToggle = findViewById(R.id.apiKeyVisibilityToggle)
        userNameInput = findViewById(R.id.userNameInput)
        activeEngineTitleText = findViewById(R.id.activeEngineTitleText)
        activeEngineSubtext = findViewById(R.id.activeEngineSubtext)
        activeEngineBadge = findViewById(R.id.activeEngineBadge)
        geminiVoiceChipsRow = findViewById(R.id.geminiVoiceChipsRow)
        simSpinner = findViewById(R.id.simSpinner)
        geminiVoiceLabel = findViewById(R.id.geminiVoiceLabel)
        permissionsBar = findViewById(R.id.permissionsBar)
        permissionsBar.pressFeedback(0.96f)
        permissionsBar.setOnClickListener {
            startActivity(Intent(this, PermissionsActivity::class.java))
        }

        personalitySegmented = findViewById(R.id.personalitySegmented)
        personalityDescriptionText = findViewById(R.id.personalityDescriptionText)
        saveButton = findViewById(R.id.saveButton)
        backBtn = findViewById(R.id.backBtn)
    }

    private fun wireInteractions() {
        backBtn.pressFeedback()
        backBtn.setOnClickListener { finish() }

        apiKeyVisibilityToggle.setOnClickListener {
            isApiKeyVisible = !isApiKeyVisible
            if (isApiKeyVisible) {
                apiKeyInput.transformationMethod = HideReturnsTransformationMethod.getInstance()
                apiKeyVisibilityToggle.setImageResource(R.drawable.ic_visibility_off)
            } else {
                apiKeyInput.transformationMethod = PasswordTransformationMethod.getInstance()
                apiKeyVisibilityToggle.setImageResource(R.drawable.ic_visibility)
            }
            apiKeyInput.setSelection(apiKeyInput.text?.length ?: 0)
        }

        saveButton.pressFeedback(0.95f)
        saveButton.setOnClickListener { saveAndClose() }

        personalitySegmented.onSelectionChange { index ->
            selectedPersonalityIndex = index
            AnimUtils.crossFadeText(personalityDescriptionText, personalityDescriptions[index])
        }
    }

    private fun prefs() = getSharedPreferences(JarvisApplication.PREFS_NAME, Context.MODE_PRIVATE)

    private fun loadPrefs() {
        val p = prefs()
        userNameInput.setText(p.getString("user_name", ""))

        val savedVoice = p.getString("gemini_voice", "Kore")
        selectedVoiceIndex = voiceValues.indexOf(savedVoice).coerceAtLeast(0)

        val savedModel = p.getString("gemini_model", geminiModelValues[0]) ?: geminiModelValues[0]
        selectedModelIndex = geminiModelValues.indexOf(savedModel).coerceAtLeast(0)

        val personalityIndex = when (p.getString("personality_mode", "lumina")) {
            "gf" -> 1
            "professional" -> 2
            "assistant" -> 3
            else -> 0
        }
        selectedPersonalityIndex = personalityIndex
        personalitySegmented.setOptions(listOf("Lumina 💖", "GF 💖", "Pro 💼", "Assist 🤖"), personalityIndex)
        personalityDescriptionText.text = personalityDescriptions[personalityIndex]

        buildSimToggle()
        updateVoiceAccessibility()
        updateEngineBannerUI()
        updateApiKeyFieldUI()
    }

    private fun buildVoiceChips(
        container: LinearLayout,
        labels: List<String>,
        selectedIndex: Int,
        selectedBg: Int,
        selectedTextColor: Int,
        badgeBg: Int,
        badgeTextColor: Int,
        isEnabled: Boolean = true,
        onSelect: (Int) -> Unit
    ) {
        container.removeAllViews()
        labels.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex

            val chip = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(9), dp(16), dp(9))
                setBackgroundResource(if (isSelected) selectedBg else R.drawable.bg_chip_pill)
                alpha = if (isEnabled) 1.0f else 0.5f
                this.isEnabled = isEnabled
                pressFeedback(0.94f)
            }

            val label1 = TextView(this).apply {
                text = label
                textSize = 12f
                typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(
                    ContextCompat.getColor(
                        this@SettingsActivity,
                        if (isSelected) selectedTextColor else R.color.text_secondary
                    )
                )
            }
            chip.addView(label1)

            if (isSelected) {
                val badge = TextView(this).apply {
                    text = "ACTIVE"
                    textSize = 8f
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(dp(4), dp(1), dp(4), dp(1))
                    setTextColor(ContextCompat.getColor(this@SettingsActivity, badgeTextColor))
                    setBackgroundResource(badgeBg)
                }
                val badgeLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                badgeLp.marginStart = dp(6)
                chip.addView(badge, badgeLp)
            }

            chip.setOnClickListener { onSelect(index) }

            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.marginEnd = dp(8)
            container.addView(chip, lp)
        }
    }

    private fun updateVoiceAccessibility() {
        geminiVoiceLabel.alpha = 1.0f
        buildVoiceChips(
            container = geminiVoiceChipsRow,
            labels = voiceLabels,
            selectedIndex = selectedVoiceIndex,
            selectedBg = R.drawable.bg_chip_pill_primary,
            selectedTextColor = R.color.accent_primary,
            badgeBg = R.drawable.bg_chip_badge_primary,
            badgeTextColor = R.color.text_on_accent,
            isEnabled = true
        ) { index -> onGeminiVoiceSelected(index) }
    }

    private fun onGeminiVoiceSelected(index: Int) {
        selectedVoiceIndex = index
        updateVoiceAccessibility()
        updateEngineBannerUI()
    }

    private fun buildSimToggle() {
        callCapableSims = SimManager.getCallCapableSims(this)
        val savedSlotIndex = SimManager.getPreferredSimIndex(this)

        val simOptions = if (callCapableSims.isNotEmpty()) {
            callCapableSims
        } else {
            listOf(
                SimManager.SimOption(PhoneAccountHandle(android.content.ComponentName(packageName, "Sim1"), "0"), "SIM 1", 0, 1),
                SimManager.SimOption(PhoneAccountHandle(android.content.ComponentName(packageName, "Sim2"), "1"), "SIM 2", 1, 2)
            )
        }

        selectedSimIndex = when {
            savedSlotIndex < 0 -> 0
            savedSlotIndex + 1 < (simOptions.size + 1) -> savedSlotIndex + 1
            else -> 0
        }

        val simLabels = listOf("Ask") + simOptions.map { it.label }

        val adapter = object : ArrayAdapter<String>(this, R.layout.sim_spinner_item_selected, simLabels) {
            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = convertView ?: layoutInflater.inflate(R.layout.spinner_item_dropdown, parent, false)
                (view as TextView).text = simLabels[position]
                view.setTextColor(
                    ContextCompat.getColor(
                        this@SettingsActivity,
                        if (position == selectedSimIndex) R.color.success else R.color.text_primary
                    )
                )
                return view
            }
        }

        simSpinner.adapter = adapter
        simSpinner.setSelection(selectedSimIndex, false)
        simSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (selectedSimIndex != position) {
                    selectedSimIndex = position
                    if (position == 0) {
                        SimManager.savePreferredSim(this@SettingsActivity, null, -1, -1)
                    } else {
                        val option = simOptions[position - 1]
                        SimManager.savePreferredSim(this@SettingsActivity, option.handle, option.slotIndex, option.subId)
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun saveCurrentApiKeys() {
        prefs().edit().apply {
            putString("api_key", apiKeyInput.text.toString().trim())
            apply()
        }
    }



    private fun updateEngineBannerUI() {
        val modelLabel = geminiModelLabels.getOrNull(selectedModelIndex) ?: "Gemini 3.1 Live"
        activeEngineTitleText.text = "Gemini Live ($modelLabel)"
        activeEngineSubtext.text = "Voice: ${voiceValues.getOrNull(selectedVoiceIndex) ?: "Kore"} | Real-time Bidirectional Audio"
        activeEngineBadge.text = "● ACTIVE"
    }

    private fun updateApiKeyFieldUI() {
        geminiKeyContainer.visibility = View.VISIBLE
        val p = prefs()
        val savedGemini = p.getString("api_key", "") ?: ""
        val defaultGemini = EnvLoader.getApiKey(this)
        apiKeyInput.setText(if (savedGemini.isNotBlank()) savedGemini else defaultGemini)
    }

    private fun saveAndClose() {
        val selectedPersonality = when (selectedPersonalityIndex) {
            1 -> "gf"
            2 -> "professional"
            3 -> "assistant"
            else -> "lumina"
        }

        val newUserName = userNameInput.text.toString().trim()

        prefs().edit().apply {
            putString("api_key", apiKeyInput.text.toString().trim())
            putString("user_name", newUserName)
            putString("tts_engine", "gemini")
            putString("gemini_model", geminiModelValues.getOrNull(selectedModelIndex) ?: defaultGeminiModel)
            putString("gemini_voice", voiceValues.getOrNull(selectedVoiceIndex) ?: "Kore")
            putString("personality_mode", selectedPersonality)
            apply()
        }

        syncUserDataToFirebase(newUserName)

        EnvLoader.resetCache()
        Toast.makeText(this, "Configuration saved successfully", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun syncUserDataToFirebase(newUserName: String) {
        try {
            val auth = FirebaseAuth.getInstance()
            val currentUser = auth.currentUser
            val p = prefs()

            val uid = currentUser?.uid ?: p.getString("user_uid", null)
            val email = currentUser?.email ?: p.getString("user_email", "") ?: ""

            // 1. Sync Display Name to Firebase Authentication Console
            if (currentUser != null) {
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(newUserName)
                    .build()
                currentUser.updateProfile(profileUpdates).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d("SettingsActivity", "Firebase Auth displayName synced: $newUserName")
                    }
                }
            }

            // 2. Sync Display Name and updated timestamp to Firebase Firestore Database
            if (!uid.isNullOrBlank()) {
                val updateMap = hashMapOf<String, Any>(
                    "displayName" to newUserName,
                    "email" to email,
                    "updatedAtTimestamp" to System.currentTimeMillis()
                )
                FirebaseFirestore.getInstance().collection("users").document(uid)
                    .set(updateMap, SetOptions.merge())
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.d("SettingsActivity", "Firebase Firestore document synced for UID: $uid")
                        } else {
                            Log.e("SettingsActivity", "Failed to sync Firestore: ${task.exception?.message}")
                        }
                    }
            }
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Error syncing user data to Firebase", e)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
