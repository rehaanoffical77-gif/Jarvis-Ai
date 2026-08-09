package com.jarvis.assistant.util;

/**
 * Lets JARVIS open any app installed on the device by spoken name,
 * e.g. "open YouTube", "open camera", "launch WhatsApp".
 *
 * Works by listing every launchable app on the device and fuzzy-matching
 * the spoken name against each app's visible label — no hardcoded
 * package list, so it works for whatever the user has installed.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u00c6\u0002\u0018\u00002\u00020\u0001:\u0001\rB\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0014\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u00042\u0006\u0010\u0006\u001a\u00020\u0007J\u0016\u0010\b\u001a\u00020\t2\u0006\u0010\u0006\u001a\u00020\u00072\u0006\u0010\n\u001a\u00020\u000bJ\u0018\u0010\f\u001a\u0004\u0018\u00010\u00052\u0006\u0010\u0006\u001a\u00020\u00072\u0006\u0010\n\u001a\u00020\u000b\u00a8\u0006\u000e"}, d2 = {"Lcom/jarvis/assistant/util/AppLauncher;", "", "()V", "getLaunchableApps", "", "Lcom/jarvis/assistant/util/AppLauncher$InstalledApp;", "context", "Landroid/content/Context;", "isAppInstalled", "", "spokenName", "", "openApp", "InstalledApp", "app_release"})
public final class AppLauncher {
    @org.jetbrains.annotations.NotNull()
    public static final com.jarvis.assistant.util.AppLauncher INSTANCE = null;
    
    private AppLauncher() {
        super();
    }
    
    /**
     * Returns every user-launchable app (has a launcher icon), sorted by label.
     * Only includes activities that are actually enabled and resolvable via
     * getLaunchIntentForPackage, so we never "match" something we can't open.
     */
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<com.jarvis.assistant.util.AppLauncher.InstalledApp> getLaunchableApps(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        return null;
    }
    
    /**
     * Attempts to open an app whose name best matches [spokenName].
     * Matching order: exact label match -> label starts-with -> label contains
     * -> package name contains. Returns the app that was launched, or null if
     * nothing matched or the launch failed for any reason (never throws).
     */
    @org.jetbrains.annotations.Nullable()
    public final com.jarvis.assistant.util.AppLauncher.InstalledApp openApp(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.NotNull()
    java.lang.String spokenName) {
        return null;
    }
    
    /**
     * True if at least one non-system app label/package matches [spokenName] closely.
     */
    public final boolean isAppInstalled(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.NotNull()
    java.lang.String spokenName) {
        return false;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0002\b\t\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0005J\t\u0010\t\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\n\u001a\u00020\u0003H\u00c6\u0003J\u001d\u0010\u000b\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u0003H\u00c6\u0001J\u0013\u0010\f\u001a\u00020\r2\b\u0010\u000e\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u000f\u001a\u00020\u0010H\u00d6\u0001J\t\u0010\u0011\u001a\u00020\u0003H\u00d6\u0001R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\u0007\u00a8\u0006\u0012"}, d2 = {"Lcom/jarvis/assistant/util/AppLauncher$InstalledApp;", "", "label", "", "packageName", "(Ljava/lang/String;Ljava/lang/String;)V", "getLabel", "()Ljava/lang/String;", "getPackageName", "component1", "component2", "copy", "equals", "", "other", "hashCode", "", "toString", "app_release"})
    public static final class InstalledApp {
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String label = null;
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String packageName = null;
        
        public InstalledApp(@org.jetbrains.annotations.NotNull()
        java.lang.String label, @org.jetbrains.annotations.NotNull()
        java.lang.String packageName) {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getLabel() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getPackageName() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component1() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component2() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.jarvis.assistant.util.AppLauncher.InstalledApp copy(@org.jetbrains.annotations.NotNull()
        java.lang.String label, @org.jetbrains.annotations.NotNull()
        java.lang.String packageName) {
            return null;
        }
        
        @java.lang.Override()
        public boolean equals(@org.jetbrains.annotations.Nullable()
        java.lang.Object other) {
            return false;
        }
        
        @java.lang.Override()
        public int hashCode() {
            return 0;
        }
        
        @java.lang.Override()
        @org.jetbrains.annotations.NotNull()
        public java.lang.String toString() {
            return null;
        }
    }
}