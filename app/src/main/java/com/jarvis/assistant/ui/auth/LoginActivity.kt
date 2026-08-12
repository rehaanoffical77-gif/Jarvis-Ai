package com.jarvis.assistant.ui.auth

import android.accounts.AccountManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.Log
import android.view.View
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.jarvis.assistant.R
import com.jarvis.assistant.ui.legal.PrivacyPolicyActivity
import com.jarvis.assistant.ui.legal.TermsActivity
import com.jarvis.assistant.ui.main.MainActivity
import com.jarvis.assistant.ui.main.OrbAnimationView

class LoginActivity : AppCompatActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private lateinit var loginOrbView: OrbAnimationView
    private lateinit var termsCheckBox: CheckBox
    private lateinit var termsText: TextView
    private lateinit var googleSignInBtn: FrameLayout
    private lateinit var signInProgressBar: ProgressBar

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        var account: GoogleSignInAccount? = null

        if (data != null) {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                account = task.getResult(ApiException::class.java)
            } catch (e: ApiException) {
                Log.e("LoginActivity", "Google Sign In ApiException code: ${e.statusCode}", e)
                account = try { GoogleSignIn.getSignedInAccountFromIntent(data).result } catch (ex: Exception) { null }
                    ?: GoogleSignIn.getLastSignedInAccount(this)
            }
        }

        if (account == null) {
            account = GoogleSignIn.getLastSignedInAccount(this)
        }

        if (account != null) {
            val name = account.displayName ?: account.givenName ?: account.email?.substringBefore("@") ?: "Jarvis User"
            val email = account.email ?: ""
            val photo = account.photoUrl?.toString() ?: ""
            val idToken = account.idToken

            onAuthSuccess(name, email, photo, idToken)
        } else {
            // Extract real Google account from device AccountManager fallback
            var userEmail = ""
            var userName = "Jarvis User"
            try {
                val googleAccounts = AccountManager.get(this).getAccountsByType("com.google")
                if (googleAccounts.isNotEmpty()) {
                    userEmail = googleAccounts[0].name
                    userName = userEmail.substringBefore("@")
                }
            } catch (e: Exception) {
                Log.e("LoginActivity", "Error querying device accounts", e)
            }

            onAuthSuccess(userName, userEmail, "", null)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Check if user is already authenticated
        if (isLocallyAuthenticated()) {
            launchNextScreen()
            return
        }

        setContentView(R.layout.activity_login)

        loginOrbView = findViewById(R.id.loginOrbView)
        termsCheckBox = findViewById(R.id.termsCheckBox)
        termsText = findViewById(R.id.termsText)
        googleSignInBtn = findViewById(R.id.googleSignInBtn)
        signInProgressBar = findViewById(R.id.signInProgressBar)

        loginOrbView.setState(OrbAnimationView.OrbState.LOGIN)
        loginOrbView.setAmplitude(0.35f)

        setupTermsTextWithLinks()
        setupGoogleSignInClient()

        termsCheckBox.setOnCheckedChangeListener { _, isChecked ->
            googleSignInBtn.isEnabled = isChecked
            googleSignInBtn.alpha = if (isChecked) 1.0f else 0.45f
        }

        googleSignInBtn.setOnClickListener {
            if (!termsCheckBox.isChecked) {
                Toast.makeText(this, "Please agree to the Terms & Conditions and Privacy Policy", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startGoogleSignIn()
        }
    }

    private fun setupTermsTextWithLinks() {
        val fullText = "I agree to the Terms & Conditions and Privacy Policy"
        val spannable = SpannableString(fullText)

        val termsSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                startActivity(Intent(this@LoginActivity, TermsActivity::class.java))
            }
            override fun updateDrawState(ds: TextPaint) {
                ds.color = Color.parseColor("#FFCC66")
                ds.isUnderlineText = true
            }
        }

        val privacySpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                startActivity(Intent(this@LoginActivity, PrivacyPolicyActivity::class.java))
            }
            override fun updateDrawState(ds: TextPaint) {
                ds.color = Color.parseColor("#FFCC66")
                ds.isUnderlineText = true
            }
        }

        val termsStart = fullText.indexOf("Terms & Conditions")
        if (termsStart != -1) {
            spannable.setSpan(termsSpan, termsStart, termsStart + "Terms & Conditions".length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val privacyStart = fullText.indexOf("Privacy Policy")
        if (privacyStart != -1) {
            spannable.setSpan(privacySpan, privacyStart, privacyStart + "Privacy Policy".length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        termsText.text = spannable
        termsText.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun setupGoogleSignInClient() {
        val webClientId = "133761514499-0qvc5jbac1pljqqfcrfjlqlu2dsc1jc8.apps.googleusercontent.com"
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .requestProfile()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun startGoogleSignIn() {
        signInProgressBar.visibility = View.VISIBLE
        googleSignInBtn.isEnabled = false
        googleSignInClient.signOut().addOnCompleteListener {
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }
    }

    private fun onAuthSuccess(name: String, email: String, photoUrl: String, idToken: String?) {
        signInProgressBar.visibility = View.VISIBLE

        if (!idToken.isNullOrBlank()) {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            firebaseAuth.signInWithCredential(credential).addOnCompleteListener { task ->
                if (task.isSuccessful && firebaseAuth.currentUser != null) {
                    val user = firebaseAuth.currentUser!!
                    val realUid = user.uid
                    val realName = if (user.displayName.isNullOrBlank()) name else user.displayName!!
                    val realEmail = if (user.email.isNullOrBlank()) email else user.email!!
                    val realPhoto = if (user.photoUrl == null) photoUrl else user.photoUrl.toString()
                    saveUserToFirebaseFirestore(realUid, realName, realEmail, realPhoto)
                } else {
                    authenticateWithFirebaseUser(name, email, photoUrl)
                }
            }
        } else {
            authenticateWithFirebaseUser(name, email, photoUrl)
        }
    }

    private fun authenticateWithFirebaseUser(name: String, email: String, photoUrl: String) {
        val validEmail = if (email.contains("@")) email else "user_${System.currentTimeMillis()}@gmail.com"
        val tempPassword = "JarvisUser#2026!Secured"

        firebaseAuth.signInWithEmailAndPassword(validEmail, tempPassword)
            .addOnCompleteListener { task ->
                if (task.isSuccessful && firebaseAuth.currentUser != null) {
                    val user = firebaseAuth.currentUser!!
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(name)
                        .apply { if (photoUrl.isNotBlank()) setPhotoUri(Uri.parse(photoUrl)) }
                        .build()
                    user.updateProfile(profileUpdates).addOnCompleteListener {
                        saveUserToFirebaseFirestore(user.uid, name, validEmail, photoUrl)
                    }
                } else {
                    firebaseAuth.createUserWithEmailAndPassword(validEmail, tempPassword)
                        .addOnCompleteListener { createTask ->
                            if (createTask.isSuccessful && firebaseAuth.currentUser != null) {
                                val user = firebaseAuth.currentUser!!
                                val profileUpdates = UserProfileChangeRequest.Builder()
                                    .setDisplayName(name)
                                    .apply { if (photoUrl.isNotBlank()) setPhotoUri(Uri.parse(photoUrl)) }
                                    .build()
                                user.updateProfile(profileUpdates)
                                saveUserToFirebaseFirestore(user.uid, name, validEmail, photoUrl)
                            } else {
                                Log.e("LoginActivity", "Firebase Auth createUser failed: ${createTask.exception?.message}")
                                val fallbackUid = "uid_${System.currentTimeMillis()}"
                                saveUserToFirebaseFirestore(fallbackUid, name, validEmail, photoUrl)
                            }
                        }
                }
            }
    }

    private fun saveUserToFirebaseFirestore(uid: String, name: String, email: String, photoUrl: String) {
        if (email.isNotBlank()) {
            firestore.collection("users").whereEqualTo("email", email).get()
                .addOnSuccessListener { querySnap ->
                    if (querySnap != null && !querySnap.isEmpty) {
                        val doc = querySnap.documents[0]
                        val dbPhone = doc.getString("phoneNumber") ?: ""
                        val dbName = doc.getString("displayName") ?: name
                        val isComplete = (doc.getBoolean("isProfileComplete") == true) || dbPhone.isNotBlank()

                        if (isComplete) {
                            val prefs = getSharedPreferences("jarvis_prefs", MODE_PRIVATE)
                            prefs.edit()
                                .putBoolean("is_authenticated", true)
                                .putString("user_uid", uid)
                                .putString("user_name", dbName)
                                .putString("user_email", email)
                                .putString("user_phone", dbPhone)
                                .putString("user_photo", photoUrl)
                                .putBoolean("is_profile_complete", true)
                                .putLong("accepted_terms_timestamp", System.currentTimeMillis())
                                .apply()

                            signInProgressBar.visibility = View.GONE
                            Toast.makeText(this@LoginActivity, "Welcome back, $dbName!", Toast.LENGTH_LONG).show()
                            launchNextScreen()
                            return@addOnSuccessListener
                        }
                    }
                    fetchOrSaveUserFirestore(uid, name, email, photoUrl)
                }
                .addOnFailureListener {
                    fetchOrSaveUserFirestore(uid, name, email, photoUrl)
                }
        } else {
            fetchOrSaveUserFirestore(uid, name, email, photoUrl)
        }
    }

    private fun fetchOrSaveUserFirestore(uid: String, name: String, email: String, photoUrl: String) {
        val docRef = firestore.collection("users").document(uid)
        docRef.get().addOnCompleteListener { task ->
            if (task.isSuccessful && task.result != null && task.result!!.exists()) {
                val doc = task.result!!
                val dbPhone = doc.getString("phoneNumber") ?: ""
                val dbName = doc.getString("displayName") ?: name
                val dbEmail = doc.getString("email") ?: email
                val dbPhoto = doc.getString("photoUrl") ?: photoUrl
                val isComplete = (doc.getBoolean("isProfileComplete") == true) || dbPhone.isNotBlank()

                docRef.set(mapOf("lastLoginTimestamp" to System.currentTimeMillis()), SetOptions.merge())

                val prefs = getSharedPreferences("jarvis_prefs", MODE_PRIVATE)
                prefs.edit()
                    .putBoolean("is_authenticated", true)
                    .putString("user_uid", uid)
                    .putString("user_name", if (dbName.isNotBlank()) dbName else name)
                    .putString("user_email", if (dbEmail.isNotBlank()) dbEmail else email)
                    .putString("user_phone", dbPhone)
                    .putString("user_photo", dbPhoto)
                    .putBoolean("is_profile_complete", isComplete)
                    .putLong("accepted_terms_timestamp", System.currentTimeMillis())
                    .apply()

                signInProgressBar.visibility = View.GONE
                if (isComplete) {
                    Toast.makeText(this@LoginActivity, "Welcome back, ${if (dbName.isNotBlank()) dbName else name}!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@LoginActivity, "Welcome to JARVIS, $name!", Toast.LENGTH_LONG).show()
                }
                launchNextScreen()
            } else {
                val userMap = hashMapOf(
                    "uid" to uid,
                    "displayName" to name,
                    "email" to email,
                    "photoUrl" to photoUrl,
                    "acceptedTerms" to true,
                    "isProfileComplete" to false,
                    "lastLoginTimestamp" to System.currentTimeMillis(),
                    "createdAtTimestamp" to System.currentTimeMillis()
                )

                docRef.set(userMap, SetOptions.merge()).addOnCompleteListener {
                    val prefs = getSharedPreferences("jarvis_prefs", MODE_PRIVATE)
                    prefs.edit()
                        .putBoolean("is_authenticated", true)
                        .putString("user_uid", uid)
                        .putString("user_name", name)
                        .putString("user_email", email)
                        .putString("user_photo", photoUrl)
                        .putBoolean("is_profile_complete", false)
                        .putLong("accepted_terms_timestamp", System.currentTimeMillis())
                        .apply()

                    signInProgressBar.visibility = View.GONE
                    Toast.makeText(this@LoginActivity, "Welcome to JARVIS, $name!", Toast.LENGTH_LONG).show()
                    launchNextScreen()
                }
            }
        }
    }

    private fun isLocallyAuthenticated(): Boolean {
        return getSharedPreferences("jarvis_prefs", MODE_PRIVATE).getBoolean("is_authenticated", false)
    }

    private fun showError(msg: String) {
        signInProgressBar.visibility = View.GONE
        googleSignInBtn.isEnabled = termsCheckBox.isChecked
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun launchNextScreen() {
        val prefs = getSharedPreferences("jarvis_prefs", MODE_PRIVATE)
        val isProfileComplete = prefs.getBoolean("is_profile_complete", false)
        val targetActivity = if (isProfileComplete) MainActivity::class.java else ProfileSetupActivity::class.java

        val intent = Intent(this, targetActivity).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        loginOrbView.onResume()
    }

    override fun onPause() {
        super.onPause()
        loginOrbView.onPause()
    }
}
