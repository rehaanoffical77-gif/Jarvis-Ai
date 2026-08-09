package com.jarvis.assistant.ui.auth;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000d\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\n\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J \u0010\u0016\u001a\u00020\u00172\u0006\u0010\u0018\u001a\u00020\u00192\u0006\u0010\u001a\u001a\u00020\u00192\u0006\u0010\u001b\u001a\u00020\u0019H\u0002J\b\u0010\u001c\u001a\u00020\u001dH\u0002J\b\u0010\u001e\u001a\u00020\u0017H\u0002J*\u0010\u001f\u001a\u00020\u00172\u0006\u0010\u0018\u001a\u00020\u00192\u0006\u0010\u001a\u001a\u00020\u00192\u0006\u0010\u001b\u001a\u00020\u00192\b\u0010 \u001a\u0004\u0018\u00010\u0019H\u0002J\u0012\u0010!\u001a\u00020\u00172\b\u0010\"\u001a\u0004\u0018\u00010#H\u0014J\b\u0010$\u001a\u00020\u0017H\u0014J\b\u0010%\u001a\u00020\u0017H\u0014J(\u0010&\u001a\u00020\u00172\u0006\u0010\'\u001a\u00020\u00192\u0006\u0010\u0018\u001a\u00020\u00192\u0006\u0010\u001a\u001a\u00020\u00192\u0006\u0010\u001b\u001a\u00020\u0019H\u0002J\b\u0010(\u001a\u00020\u0017H\u0002J\b\u0010)\u001a\u00020\u0017H\u0002J\u0010\u0010*\u001a\u00020\u00172\u0006\u0010+\u001a\u00020\u0019H\u0002J\b\u0010,\u001a\u00020\u0017H\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\bX\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\nX\u0082.\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u000b\u001a\b\u0012\u0004\u0012\u00020\r0\fX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000e\u001a\u00020\u000fX\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0010\u001a\u00020\u0011X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0012\u001a\u00020\u0013X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0014\u001a\u00020\u0015X\u0082.\u00a2\u0006\u0002\n\u0000\u00a8\u0006-"}, d2 = {"Lcom/jarvis/assistant/ui/auth/LoginActivity;", "Landroidx/appcompat/app/AppCompatActivity;", "()V", "firebaseAuth", "Lcom/google/firebase/auth/FirebaseAuth;", "firestore", "Lcom/google/firebase/firestore/FirebaseFirestore;", "googleSignInBtn", "Landroid/widget/FrameLayout;", "googleSignInClient", "Lcom/google/android/gms/auth/api/signin/GoogleSignInClient;", "googleSignInLauncher", "Landroidx/activity/result/ActivityResultLauncher;", "Landroid/content/Intent;", "loginOrbView", "Lcom/jarvis/assistant/ui/main/OrbAnimationView;", "signInProgressBar", "Landroid/widget/ProgressBar;", "termsCheckBox", "Landroid/widget/CheckBox;", "termsText", "Landroid/widget/TextView;", "authenticateWithFirebaseUser", "", "name", "", "email", "photoUrl", "isLocallyAuthenticated", "", "launchNextScreen", "onAuthSuccess", "idToken", "onCreate", "savedInstanceState", "Landroid/os/Bundle;", "onPause", "onResume", "saveUserToFirebaseFirestore", "uid", "setupGoogleSignInClient", "setupTermsTextWithLinks", "showError", "msg", "startGoogleSignIn", "app_debug"})
public final class LoginActivity extends androidx.appcompat.app.AppCompatActivity {
    private com.google.android.gms.auth.api.signin.GoogleSignInClient googleSignInClient;
    private com.google.firebase.auth.FirebaseAuth firebaseAuth;
    private com.google.firebase.firestore.FirebaseFirestore firestore;
    private com.jarvis.assistant.ui.main.OrbAnimationView loginOrbView;
    private android.widget.CheckBox termsCheckBox;
    private android.widget.TextView termsText;
    private android.widget.FrameLayout googleSignInBtn;
    private android.widget.ProgressBar signInProgressBar;
    @org.jetbrains.annotations.NotNull()
    private final androidx.activity.result.ActivityResultLauncher<android.content.Intent> googleSignInLauncher = null;
    
    public LoginActivity() {
        super();
    }
    
    @java.lang.Override()
    protected void onCreate(@org.jetbrains.annotations.Nullable()
    android.os.Bundle savedInstanceState) {
    }
    
    private final void setupTermsTextWithLinks() {
    }
    
    private final void setupGoogleSignInClient() {
    }
    
    private final void startGoogleSignIn() {
    }
    
    private final void onAuthSuccess(java.lang.String name, java.lang.String email, java.lang.String photoUrl, java.lang.String idToken) {
    }
    
    private final void authenticateWithFirebaseUser(java.lang.String name, java.lang.String email, java.lang.String photoUrl) {
    }
    
    private final void saveUserToFirebaseFirestore(java.lang.String uid, java.lang.String name, java.lang.String email, java.lang.String photoUrl) {
    }
    
    private final boolean isLocallyAuthenticated() {
        return false;
    }
    
    private final void showError(java.lang.String msg) {
    }
    
    private final void launchNextScreen() {
    }
    
    @java.lang.Override()
    protected void onResume() {
    }
    
    @java.lang.Override()
    protected void onPause() {
    }
}