package com.jarvis.assistant.wake;

/**
 * Always-on, local "is someone talking to JARVIS?" gate.
 *
 * This does NOT talk to Gemini at all — it runs Android's own on-device
 * SpeechRecognizer in a tight restart loop, only ever checking short
 * utterances for the word "Jarvis" (optionally preceded by "hi/hello/hey").
 * Nothing leaves the phone and nothing is forwarded to the AI until that
 * word is heard, which is what stops JARVIS from responding to nearby
 * strangers' conversations in public.
 *
 * Lifecycle contract:
 * - Call [start] only while the conversational mic (AudioEngine's
 *   AudioRecord) is NOT recording — the two cannot hold the mic at once.
 * - Call [stop] before AudioEngine starts recording (JarvisVoiceService
 *   handles this handoff).
 * - All calls must happen on the main thread; SpeechRecognizer requires it.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000:\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0005\u0018\u0000 \u00192\u00020\u0001:\u0001\u0019B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\b\u0010\u0012\u001a\u00020\tH\u0002J\u0012\u0010\u0013\u001a\u00020\u00112\b\u0010\u0014\u001a\u0004\u0018\u00010\u0015H\u0002J\b\u0010\u0016\u001a\u00020\tH\u0002J\u0006\u0010\u0017\u001a\u00020\tJ\u0006\u0010\u0018\u001a\u00020\tR\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\"\u0010\u0007\u001a\n\u0012\u0004\u0012\u00020\t\u0018\u00010\bX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\n\u0010\u000b\"\u0004\b\f\u0010\rR\u0010\u0010\u000e\u001a\u0004\u0018\u00010\u000fX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0010\u001a\u00020\u0011X\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u001a"}, d2 = {"Lcom/jarvis/assistant/wake/WakeWordDetector;", "", "context", "Landroid/content/Context;", "(Landroid/content/Context;)V", "mainHandler", "Landroid/os/Handler;", "onWakeWordDetected", "Lkotlin/Function0;", "", "getOnWakeWordDetected", "()Lkotlin/jvm/functions/Function0;", "setOnWakeWordDetected", "(Lkotlin/jvm/functions/Function0;)V", "recognizer", "Landroid/speech/SpeechRecognizer;", "wantsToListen", "", "beginListening", "containsWakeWord", "bundle", "Landroid/os/Bundle;", "restartIfNeeded", "start", "stop", "Companion", "app_release"})
public final class WakeWordDetector {
    @org.jetbrains.annotations.NotNull()
    private final android.content.Context context = null;
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "WakeWordDetector";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String WAKE_LANGUAGE = "en-IN";
    @org.jetbrains.annotations.NotNull()
    private static final java.util.List<java.lang.String> WAKE_PHRASES = null;
    private static final long RESTART_DELAY_MS = 300L;
    
    /**
     * Fired on the main thread the moment a wake phrase is heard.
     */
    @org.jetbrains.annotations.Nullable()
    private kotlin.jvm.functions.Function0<kotlin.Unit> onWakeWordDetected;
    @org.jetbrains.annotations.NotNull()
    private final android.os.Handler mainHandler = null;
    @org.jetbrains.annotations.Nullable()
    private android.speech.SpeechRecognizer recognizer;
    
    /**
     * True while we *want* to be listening; false once stop() is called or a wake word fires.
     */
    private boolean wantsToListen = false;
    @org.jetbrains.annotations.NotNull()
    public static final com.jarvis.assistant.wake.WakeWordDetector.Companion Companion = null;
    
    public WakeWordDetector(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        super();
    }
    
    /**
     * Fired on the main thread the moment a wake phrase is heard.
     */
    @org.jetbrains.annotations.Nullable()
    public final kotlin.jvm.functions.Function0<kotlin.Unit> getOnWakeWordDetected() {
        return null;
    }
    
    /**
     * Fired on the main thread the moment a wake phrase is heard.
     */
    public final void setOnWakeWordDetected(@org.jetbrains.annotations.Nullable()
    kotlin.jvm.functions.Function0<kotlin.Unit> p0) {
    }
    
    public final void start() {
    }
    
    public final void stop() {
    }
    
    private final void beginListening() {
    }
    
    /**
     * Returns true (and fires the callback, stopping further restarts) if a wake phrase is present.
     */
    private final boolean containsWakeWord(android.os.Bundle bundle) {
        return false;
    }
    
    private final void restartIfNeeded() {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\t\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010 \n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0006X\u0082T\u00a2\u0006\u0002\n\u0000R\u0014\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00060\tX\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\n"}, d2 = {"Lcom/jarvis/assistant/wake/WakeWordDetector$Companion;", "", "()V", "RESTART_DELAY_MS", "", "TAG", "", "WAKE_LANGUAGE", "WAKE_PHRASES", "", "app_release"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}