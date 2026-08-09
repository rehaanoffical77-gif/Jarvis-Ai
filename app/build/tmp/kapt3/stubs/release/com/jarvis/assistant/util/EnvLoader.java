package com.jarvis.assistant.util;

/**
 * Utility to load variables from SharedPreferences and assets/env.properties file.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0006\u001a\u00020\u00042\u0006\u0010\u0007\u001a\u00020\bJ\u000e\u0010\t\u001a\u00020\u00042\u0006\u0010\u0007\u001a\u00020\bJ\u0006\u0010\n\u001a\u00020\u000bR\u0010\u0010\u0003\u001a\u0004\u0018\u00010\u0004X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0005\u001a\u0004\u0018\u00010\u0004X\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006\f"}, d2 = {"Lcom/jarvis/assistant/util/EnvLoader;", "", "()V", "cachedApiKey", "", "cachedYoutubeKey", "getApiKey", "context", "Landroid/content/Context;", "getYoutubeApiKey", "resetCache", "", "app_release"})
public final class EnvLoader {
    @org.jetbrains.annotations.Nullable()
    private static java.lang.String cachedApiKey;
    @org.jetbrains.annotations.Nullable()
    private static java.lang.String cachedYoutubeKey;
    @org.jetbrains.annotations.NotNull()
    public static final com.jarvis.assistant.util.EnvLoader INSTANCE = null;
    
    private EnvLoader() {
        super();
    }
    
    public final void resetCache() {
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getApiKey(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getYoutubeApiKey(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        return null;
    }
}