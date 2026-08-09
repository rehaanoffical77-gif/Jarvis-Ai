package com.jarvis.assistant;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0003\u0018\u0000 \u00062\u00020\u0001:\u0001\u0006B\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u0003\u001a\u00020\u0004H\u0002J\b\u0010\u0005\u001a\u00020\u0004H\u0016\u00a8\u0006\u0007"}, d2 = {"Lcom/jarvis/assistant/JarvisApplication;", "Landroid/app/Application;", "()V", "installCrashHandler", "", "onCreate", "Companion", "app_release"})
public final class JarvisApplication extends android.app.Application {
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String PREFS_NAME = "jarvis_prefs";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String CRASH_PREFS = "jarvis_crash_log";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "JarvisApplication";
    @org.jetbrains.annotations.NotNull()
    public static final com.jarvis.assistant.JarvisApplication.Companion Companion = null;
    
    public JarvisApplication() {
        super();
    }
    
    @java.lang.Override()
    public void onCreate() {
    }
    
    /**
     * Installs a global uncaught exception handler that saves the full
     * stack trace to SharedPreferences so the error overlay in MainActivity
     * can display it on next launch. After saving, the default handler
     * runs (to let the OS show the crash dialog / kill the process).
     */
    private final void installCrashHandler() {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0007"}, d2 = {"Lcom/jarvis/assistant/JarvisApplication$Companion;", "", "()V", "CRASH_PREFS", "", "PREFS_NAME", "TAG", "app_release"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}