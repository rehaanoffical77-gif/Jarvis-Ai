package com.jarvis.assistant.service;

/**
 * Owns the mic capture + Gemini Live WebSocket for the whole app lifetime,
 * independent of whatever Activity is currently on screen.
 *
 * Why this exists: Android blocks microphone access for apps that are not
 * in the foreground and have no active foreground service. Previously
 * AudioEngine/GeminiLiveClient lived inside MainActivity, so the moment
 * another app (e.g. YouTube, opened via open_app) came to the front, the
 * OS cut mic access and JARVIS effectively went silent/crashed. Running this
 * as a foreground service with type "microphone" keeps the conversation
 * alive in the background.
 *
 * MainActivity binds to this service for UI updates (transcripts,
 * amplitude, speaking state) via [JarvisVoiceListener], but the service
 * itself does not depend on the Activity being bound or visible.
 *
 * --- Wake word ("Jarvis") gating ---
 * The mic is ALWAYS on and ALWAYS streaming to Gemini (no separate local
 * speech recognizer, so no extra mic-open/close cycles and no earcon
 * beeping). Gemini transcribes speech in real time via input transcription;
 * this class buffers each spoken turn's transcript and only lets JARVIS
 * actually speak a reply or run a tool (open an app, call a contact, etc.)
 * if that turn's transcript contains "Jarvis". If it doesn't, the reply is
 * silently dropped and no tool is executed — so "open YouTube" alone does
 * nothing, but "Jarvis, open YouTube" works.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0086\u0001\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\n\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\b\n\u0002\b\u0011\n\u0002\u0018\u0002\n\u0002\b\u000e\u0018\u0000 U2\u00020\u0001:\u0003UVWB\u0005\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u001d\u001a\u00020\n2\u0006\u0010\u001e\u001a\u00020\u001fH\u0002J\u0010\u0010 \u001a\u00020\n2\u0006\u0010\u001e\u001a\u00020\u001fH\u0002J\b\u0010!\u001a\u00020\"H\u0002J\b\u0010#\u001a\u00020$H\u0002J&\u0010%\u001a\u00020$2\u0006\u0010&\u001a\u00020\'2\u0006\u0010\u001e\u001a\u00020\u001f2\u0006\u0010(\u001a\u00020\'H\u0082@\u00a2\u0006\u0002\u0010)J\u0006\u0010*\u001a\u00020$J\u0006\u0010+\u001a\u00020\nJ\u0006\u0010,\u001a\u00020\nJ\u0006\u0010-\u001a\u00020\nJ\u0006\u0010.\u001a\u00020\nJ\u0006\u0010/\u001a\u00020\nJ\u0006\u00100\u001a\u00020\nJ\u0012\u00101\u001a\u0002022\b\u00103\u001a\u0004\u0018\u000104H\u0016J\b\u00105\u001a\u00020$H\u0016J\b\u00106\u001a\u00020$H\u0016J\"\u00107\u001a\u0002082\b\u00103\u001a\u0004\u0018\u0001042\u0006\u00109\u001a\u0002082\u0006\u0010:\u001a\u000208H\u0016J\b\u0010;\u001a\u00020$H\u0002J&\u0010<\u001a\u00020$2\u0006\u0010=\u001a\u00020\'2\u0006\u0010>\u001a\u00020\'2\u0006\u0010?\u001a\u00020\'2\u0006\u0010@\u001a\u00020\'J\u000e\u0010A\u001a\u00020$2\u0006\u0010B\u001a\u00020\'J\u001e\u0010C\u001a\u00020$2\u0006\u0010(\u001a\u00020\'2\u0006\u0010&\u001a\u00020\'2\u0006\u0010D\u001a\u00020\u001fJ\u000e\u0010E\u001a\u00020$2\u0006\u0010F\u001a\u00020\nJ\u001c\u0010G\u001a\u00020$2\b\b\u0002\u0010H\u001a\u00020\n2\n\b\u0002\u0010I\u001a\u0004\u0018\u00010JJ\u0016\u0010K\u001a\u00020$2\u0006\u0010L\u001a\u0002082\u0006\u0010M\u001a\u000204J&\u0010N\u001a\u00020$2\u0006\u0010=\u001a\u00020\'2\u0006\u0010>\u001a\u00020\'2\u0006\u0010?\u001a\u00020\'2\u0006\u0010@\u001a\u00020\'J\u0006\u0010O\u001a\u00020$J\u0006\u0010P\u001a\u00020$J\u0006\u0010Q\u001a\u00020$J\u0006\u0010R\u001a\u00020$J\u0010\u0010S\u001a\u00020\n2\u0006\u0010B\u001a\u00020\'H\u0002J\u0010\u0010T\u001a\u00020$2\b\u0010I\u001a\u0004\u0018\u00010JR\u0010\u0010\u0003\u001a\u0004\u0018\u00010\u0004X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0012\u0010\u0005\u001a\u00060\u0006R\u00020\u0000X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0007\u001a\u0004\u0018\u00010\bX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\nX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0012\u0010\u000b\u001a\u00060\fj\u0002`\rX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u000e\u001a\u0004\u0018\u00010\u000fX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0010\u001a\u00020\nX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0011\u001a\u00020\nX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0012\u001a\u00020\nX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0013\u001a\u0004\u0018\u00010\u0014X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0015\u001a\u00020\u0016X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001c\u0010\u0017\u001a\u0004\u0018\u00010\u0018X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0019\u0010\u001a\"\u0004\b\u001b\u0010\u001c\u00a8\u0006X"}, d2 = {"Lcom/jarvis/assistant/service/JarvisVoiceService;", "Landroid/app/Service;", "()V", "audioEngine", "Lcom/jarvis/assistant/ai/AudioEngine;", "binder", "Lcom/jarvis/assistant/service/JarvisVoiceService$LocalBinder;", "cameraVisionEngine", "Lcom/jarvis/assistant/vision/CameraVisionEngine;", "currentTurnHasWakeWord", "", "currentTurnInputText", "Ljava/lang/StringBuilder;", "Lkotlin/text/StringBuilder;", "geminiLive", "Lcom/jarvis/assistant/ai/GeminiLiveClient;", "interruptSentThisTurn", "isSessionStarted", "isUserMuted", "screenCaptureEngine", "Lcom/jarvis/assistant/vision/ScreenCaptureEngine;", "toolScope", "Lkotlinx/coroutines/CoroutineScope;", "uiListener", "Lcom/jarvis/assistant/service/JarvisVoiceService$JarvisVoiceListener;", "getUiListener", "()Lcom/jarvis/assistant/service/JarvisVoiceService$JarvisVoiceListener;", "setUiListener", "(Lcom/jarvis/assistant/service/JarvisVoiceService$JarvisVoiceListener;)V", "adjustBrightness", "args", "Lorg/json/JSONObject;", "adjustVolume", "buildNotification", "Landroid/app/Notification;", "createNotificationChannel", "", "handleToolCall", "name", "", "callId", "(Ljava/lang/String;Lorg/json/JSONObject;Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "interrupt", "isCameraFrontLens", "isCameraVisionActive", "isCurrentlySpeaking", "isMicMuted", "isScreenSharing", "isSessionRunning", "onBind", "Landroid/os/IBinder;", "intent", "Landroid/content/Intent;", "onCreate", "onDestroy", "onStartCommand", "", "flags", "startId", "resetTurnState", "restartSession", "apiKey", "modelString", "systemPrompt", "voiceName", "sendTextToGemini", "text", "sendToolResponse", "result", "setMicMuted", "muted", "startCameraVision", "useFront", "previewTextureView", "Landroid/view/TextureView;", "startScreenShare", "resultCode", "data", "startSession", "stopCameraVision", "stopScreenShare", "stopSession", "switchCameraLens", "textHasWakeWord", "updateCameraPreviewTarget", "Companion", "JarvisVoiceListener", "LocalBinder", "app_release"})
public final class JarvisVoiceService extends android.app.Service {
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String CHANNEL_ID = "jarvis_voice_channel";
    private static final int NOTIFICATION_ID = 101;
    @org.jetbrains.annotations.NotNull()
    private static final java.util.List<java.lang.String> WAKE_PHRASES = null;
    @org.jetbrains.annotations.NotNull()
    private final com.jarvis.assistant.service.JarvisVoiceService.LocalBinder binder = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.CoroutineScope toolScope = null;
    
    /**
     * UI layer (MainActivity) sets this while bound; service works fine without it too.
     */
    @org.jetbrains.annotations.Nullable()
    private com.jarvis.assistant.service.JarvisVoiceService.JarvisVoiceListener uiListener;
    @org.jetbrains.annotations.Nullable()
    private com.jarvis.assistant.ai.GeminiLiveClient geminiLive;
    @org.jetbrains.annotations.Nullable()
    private com.jarvis.assistant.ai.AudioEngine audioEngine;
    @org.jetbrains.annotations.Nullable()
    private com.jarvis.assistant.vision.ScreenCaptureEngine screenCaptureEngine;
    @org.jetbrains.annotations.Nullable()
    private com.jarvis.assistant.vision.CameraVisionEngine cameraVisionEngine;
    private boolean isSessionStarted = false;
    private boolean isUserMuted = false;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.StringBuilder currentTurnInputText = null;
    private boolean currentTurnHasWakeWord = true;
    private boolean interruptSentThisTurn = false;
    @org.jetbrains.annotations.NotNull()
    public static final com.jarvis.assistant.service.JarvisVoiceService.Companion Companion = null;
    
    public JarvisVoiceService() {
        super();
    }
    
    /**
     * UI layer (MainActivity) sets this while bound; service works fine without it too.
     */
    @org.jetbrains.annotations.Nullable()
    public final com.jarvis.assistant.service.JarvisVoiceService.JarvisVoiceListener getUiListener() {
        return null;
    }
    
    /**
     * UI layer (MainActivity) sets this while bound; service works fine without it too.
     */
    public final void setUiListener(@org.jetbrains.annotations.Nullable()
    com.jarvis.assistant.service.JarvisVoiceService.JarvisVoiceListener p0) {
    }
    
    private final boolean textHasWakeWord(java.lang.String text) {
        return false;
    }
    
    /**
     * Resets per-turn bookkeeping, ready for the next utterance.
     */
    private final void resetTurnState() {
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public android.os.IBinder onBind(@org.jetbrains.annotations.Nullable()
    android.content.Intent intent) {
        return null;
    }
    
    @java.lang.Override()
    public void onCreate() {
    }
    
    @java.lang.Override()
    public int onStartCommand(@org.jetbrains.annotations.Nullable()
    android.content.Intent intent, int flags, int startId) {
        return 0;
    }
    
    /**
     * Restarts the session with updated settings (personality, voice, API key, etc.).
     */
    public final void restartSession(@org.jetbrains.annotations.NotNull()
    java.lang.String apiKey, @org.jetbrains.annotations.NotNull()
    java.lang.String modelString, @org.jetbrains.annotations.NotNull()
    java.lang.String systemPrompt, @org.jetbrains.annotations.NotNull()
    java.lang.String voiceName) {
    }
    
    /**
     * Starts the mic/WebSocket session. Safe to call repeatedly; a no-op once already running.
     */
    public final void startSession(@org.jetbrains.annotations.NotNull()
    java.lang.String apiKey, @org.jetbrains.annotations.NotNull()
    java.lang.String modelString, @org.jetbrains.annotations.NotNull()
    java.lang.String systemPrompt, @org.jetbrains.annotations.NotNull()
    java.lang.String voiceName) {
    }
    
    private final java.lang.Object handleToolCall(java.lang.String name, org.json.JSONObject args, java.lang.String callId, kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    private final boolean adjustVolume(org.json.JSONObject args) {
        return false;
    }
    
    private final boolean adjustBrightness(org.json.JSONObject args) {
        return false;
    }
    
    /**
     * Send a text message to Gemini Live (used for text chat input).
     */
    public final void sendTextToGemini(@org.jetbrains.annotations.NotNull()
    java.lang.String text) {
    }
    
    public final void setMicMuted(boolean muted) {
    }
    
    public final boolean isMicMuted() {
        return false;
    }
    
    public final boolean isCurrentlySpeaking() {
        return false;
    }
    
    public final void interrupt() {
    }
    
    public final void sendToolResponse(@org.jetbrains.annotations.NotNull()
    java.lang.String callId, @org.jetbrains.annotations.NotNull()
    java.lang.String name, @org.jetbrains.annotations.NotNull()
    org.json.JSONObject result) {
    }
    
    public final boolean isSessionRunning() {
        return false;
    }
    
    public final void startScreenShare(int resultCode, @org.jetbrains.annotations.NotNull()
    android.content.Intent data) {
    }
    
    public final void stopScreenShare() {
    }
    
    public final boolean isScreenSharing() {
        return false;
    }
    
    public final void startCameraVision(boolean useFront, @org.jetbrains.annotations.Nullable()
    android.view.TextureView previewTextureView) {
    }
    
    public final void updateCameraPreviewTarget(@org.jetbrains.annotations.Nullable()
    android.view.TextureView previewTextureView) {
    }
    
    public final void switchCameraLens() {
    }
    
    public final void stopCameraVision() {
    }
    
    public final boolean isCameraVisionActive() {
        return false;
    }
    
    public final boolean isCameraFrontLens() {
        return false;
    }
    
    /**
     * Fully tears down the voice session and stops the service (e.g. user quit JARVIS entirely).
     */
    public final void stopSession() {
    }
    
    @java.lang.Override()
    public void onDestroy() {
    }
    
    private final void createNotificationChannel() {
    }
    
    private final android.app.Notification buildNotification() {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u001e\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010 \n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082T\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\u00040\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\t"}, d2 = {"Lcom/jarvis/assistant/service/JarvisVoiceService$Companion;", "", "()V", "CHANNEL_ID", "", "NOTIFICATION_ID", "", "WAKE_PHRASES", "", "app_release"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00000\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\u0007\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0006\n\u0002\u0010\u000e\n\u0002\b\u000f\n\u0002\u0018\u0002\n\u0002\b\u0003\bf\u0018\u00002\u00020\u0001J\u0010\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u0005H\u0016J\u0018\u0010\u0006\u001a\u00020\u00032\u0006\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\bH\u0016J\b\u0010\n\u001a\u00020\u0003H\u0016J\b\u0010\u000b\u001a\u00020\u0003H\u0016J\b\u0010\f\u001a\u00020\u0003H\u0016J\u0010\u0010\r\u001a\u00020\u00032\u0006\u0010\u000e\u001a\u00020\u000fH\u0016J\u0010\u0010\u0010\u001a\u00020\u00032\u0006\u0010\u0011\u001a\u00020\u000fH\u0016J\u0010\u0010\u0012\u001a\u00020\u00032\u0006\u0010\u0011\u001a\u00020\u000fH\u0016J\u0018\u0010\u0013\u001a\u00020\u00032\u0006\u0010\u0014\u001a\u00020\b2\u0006\u0010\u0015\u001a\u00020\u000fH\u0016J\u0010\u0010\u0016\u001a\u00020\u00032\u0006\u0010\u0017\u001a\u00020\bH\u0016J\b\u0010\u0018\u001a\u00020\u0003H\u0016J\b\u0010\u0019\u001a\u00020\u0003H\u0016J\b\u0010\u001a\u001a\u00020\u0003H\u0016J\b\u0010\u001b\u001a\u00020\u0003H\u0016J \u0010\u001c\u001a\u00020\u00032\u0006\u0010\u001d\u001a\u00020\u000f2\u0006\u0010\u001e\u001a\u00020\u001f2\u0006\u0010 \u001a\u00020\u000fH\u0016J\b\u0010!\u001a\u00020\u0003H\u0016\u00a8\u0006\""}, d2 = {"Lcom/jarvis/assistant/service/JarvisVoiceService$JarvisVoiceListener;", "", "onAmplitudeChanged", "", "rms", "", "onCameraVisionStateChanged", "isActive", "", "isFront", "onCommandIgnored", "onConnected", "onDisconnected", "onError", "msg", "", "onInputTranscript", "text", "onOutputTranscript", "onResearchStateChanged", "isSearching", "query", "onScreenShareStateChanged", "isSharing", "onSetupComplete", "onShutdownRequested", "onSpeakingStarted", "onSpeakingStopped", "onToolCall", "name", "args", "Lorg/json/JSONObject;", "callId", "onTurnComplete", "app_release"})
    public static abstract interface JarvisVoiceListener {
        
        public abstract void onConnected();
        
        public abstract void onSetupComplete();
        
        public abstract void onDisconnected();
        
        public abstract void onError(@org.jetbrains.annotations.NotNull()
        java.lang.String msg);
        
        public abstract void onInputTranscript(@org.jetbrains.annotations.NotNull()
        java.lang.String text);
        
        public abstract void onOutputTranscript(@org.jetbrains.annotations.NotNull()
        java.lang.String text);
        
        public abstract void onTurnComplete();
        
        public abstract void onAmplitudeChanged(float rms);
        
        public abstract void onSpeakingStarted();
        
        public abstract void onSpeakingStopped();
        
        public abstract void onToolCall(@org.jetbrains.annotations.NotNull()
        java.lang.String name, @org.jetbrains.annotations.NotNull()
        org.json.JSONObject args, @org.jetbrains.annotations.NotNull()
        java.lang.String callId);
        
        /**
         * A turn was heard but ignored because it didn't start with "Jarvis".
         */
        public abstract void onCommandIgnored();
        
        public abstract void onScreenShareStateChanged(boolean isSharing);
        
        public abstract void onCameraVisionStateChanged(boolean isActive, boolean isFront);
        
        public abstract void onResearchStateChanged(boolean isSearching, @org.jetbrains.annotations.NotNull()
        java.lang.String query);
        
        public abstract void onShutdownRequested();
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 3, xi = 48)
        public static final class DefaultImpls {
            
            public static void onConnected(@org.jetbrains.annotations.NotNull()
            com.jarvis.assistant.service.JarvisVoiceService.JarvisVoiceListener $this) {
            }
            
            public static void onSetupComplete(@org.jetbrains.annotations.NotNull()
            com.jarvis.assistant.service.JarvisVoiceService.JarvisVoiceListener $this) {
            }
            
            public static void onDisconnected(@org.jetbrains.annotations.NotNull()
            com.jarvis.assistant.service.JarvisVoiceService.JarvisVoiceListener $this) {
            }
            
            public static void onError(@org.jetbrains.annotations.NotNull()
            com.jarvis.assistant.service.JarvisVoiceService.JarvisVoiceListener $this, @org.jetbrains.annotations.NotNull()
            java.lang.String msg) {
            }
            
            public static void onInputTranscript(@org.jetbrains.annotations.NotNull()
            com.jarvis.assistant.service.JarvisVoiceService.JarvisVoiceListener $this, @org.jetbrains.annotations.NotNull()
            java.lang.String text) {
            }
            
            public static void onOutputTranscript(@org.jetbrains.annotations.NotNull()
            com.jarvis.assistant.service.JarvisVoiceService.JarvisVoiceListener $this, @org.jetbrains.annotations.NotNull()
            java.lang.String text) {
            }
            
            public static void onTurnComplete(@org.jetbrains.annotations.NotNull()
            com.jarvis.assistant.service.JarvisVoiceService.JarvisVoiceListener $this) {
            }
            
            public static void onAmplitudeChanged(@org.jetbrains.annotations.NotNull()
            com.jarvis.assistant.service.JarvisVoiceService.JarvisVoiceListener $this, float rms) {
            }
            
            public static void onSpeakingStarted(@org.jetbrains.annotations.NotNull()
            com.jarvis.assistant.service.JarvisVoiceService.JarvisVoiceListener $this) {
            }
            
            public static void onSpeakingStopped(@org.jetbrains.annotations.NotNull()
            com.jarvis.assistant.service.JarvisVoiceService.JarvisVoiceListener $this) {
            }
            
            public static void onToolCall(@org.jetbrains.annotations.NotNull()
            com.jarvis.assistant.service.JarvisVoiceService.JarvisVoiceListener $this, @org.jetbrains.annotations.NotNull()
            java.lang.String name, @org.jetbrains.annotations.NotNull()
            org.json.JSONObject args, @org.jetbrains.annotations.NotNull()
            java.lang.String callId) {
            }
            
            /**
             * A turn was heard but ignored because it didn't start with "Jarvis".
             */
            public static void onCommandIgnored(@org.jetbrains.annotations.NotNull()
            com.jarvis.assistant.service.JarvisVoiceService.JarvisVoiceListener $this) {
            }
            
            public static void onScreenShareStateChanged(@org.jetbrains.annotations.NotNull()
            com.jarvis.assistant.service.JarvisVoiceService.JarvisVoiceListener $this, boolean isSharing) {
            }
            
            public static void onCameraVisionStateChanged(@org.jetbrains.annotations.NotNull()
            com.jarvis.assistant.service.JarvisVoiceService.JarvisVoiceListener $this, boolean isActive, boolean isFront) {
            }
            
            public static void onResearchStateChanged(@org.jetbrains.annotations.NotNull()
            com.jarvis.assistant.service.JarvisVoiceService.JarvisVoiceListener $this, boolean isSearching, @org.jetbrains.annotations.NotNull()
            java.lang.String query) {
            }
            
            public static void onShutdownRequested(@org.jetbrains.annotations.NotNull()
            com.jarvis.assistant.service.JarvisVoiceService.JarvisVoiceListener $this) {
            }
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\b\u0086\u0004\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0006\u0010\u0003\u001a\u00020\u0004\u00a8\u0006\u0005"}, d2 = {"Lcom/jarvis/assistant/service/JarvisVoiceService$LocalBinder;", "Landroid/os/Binder;", "(Lcom/jarvis/assistant/service/JarvisVoiceService;)V", "getService", "Lcom/jarvis/assistant/service/JarvisVoiceService;", "app_release"})
    public final class LocalBinder extends android.os.Binder {
        
        public LocalBinder() {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.jarvis.assistant.service.JarvisVoiceService getService() {
            return null;
        }
    }
}