package com.jarvis.assistant.ai;

/**
 * Handles the WebSocket connection to Gemini Live (BidiGenerateContent).
 *
 * Mirrors the behaviour of the Python reference implementation:
 * - Sends a `setup` message immediately on open
 * - Streams mic PCM as `realtime_input.media_chunks`
 * - Sends free-form text via `client_content`
 * - Renews the session every SESSION_RENEW_AFTER seconds
 * - Sends a silent keep-alive chunk every KEEPALIVE_INTERVAL seconds
 * - Auto-reconnects 3s after any disconnect
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000h\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u0012\n\u0002\u0010\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0017\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\n\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0018\u0018\u0000 ^2\u00020\u0001:\u0001^B\'\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u0012\u0006\u0010\u0005\u001a\u00020\u0003\u0012\b\b\u0002\u0010\u0006\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0007J\b\u0010H\u001a\u00020\u0016H\u0002J\u0006\u0010I\u001a\u00020\u0016J\u0010\u0010J\u001a\u00020\u00162\b\b\u0002\u0010K\u001a\u00020\u000fJ\u0010\u0010L\u001a\u00020\u00162\u0006\u0010M\u001a\u00020\u0003H\u0002J\u0006\u0010N\u001a\u00020\u000fJ\b\u0010O\u001a\u00020\u0016H\u0002J\u000e\u0010P\u001a\u00020\u00162\u0006\u0010Q\u001a\u00020\u0015J\u0006\u0010R\u001a\u00020\u0016J\u0010\u0010S\u001a\u00020\u00162\u0006\u0010T\u001a\u00020GH\u0002J\u0018\u0010U\u001a\u00020\u00162\u0006\u0010M\u001a\u00020\u00032\b\b\u0002\u0010V\u001a\u00020\u000fJ\u001e\u0010W\u001a\u00020\u00162\u0006\u00109\u001a\u00020\u00032\u0006\u0010X\u001a\u00020\u00032\u0006\u0010Y\u001a\u000207J\u000e\u0010Z\u001a\u00020\u00162\u0006\u0010[\u001a\u00020\u0015J\b\u0010\\\u001a\u00020\u0016H\u0002J\b\u0010]\u001a\u00020\u0016H\u0002R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001b\u0010\b\u001a\u00020\t8BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\f\u0010\r\u001a\u0004\b\n\u0010\u000bR\u000e\u0010\u000e\u001a\u00020\u000fX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0010\u001a\u00020\u000fX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0011\u001a\u0004\u0018\u00010\u0012X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0004\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R(\u0010\u0013\u001a\u0010\u0012\u0004\u0012\u00020\u0015\u0012\u0004\u0012\u00020\u0016\u0018\u00010\u0014X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0017\u0010\u0018\"\u0004\b\u0019\u0010\u001aR\"\u0010\u001b\u001a\n\u0012\u0004\u0012\u00020\u0016\u0018\u00010\u001cX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u001d\u0010\u001e\"\u0004\b\u001f\u0010 R\"\u0010!\u001a\n\u0012\u0004\u0012\u00020\u0016\u0018\u00010\u001cX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\"\u0010\u001e\"\u0004\b#\u0010 R(\u0010$\u001a\u0010\u0012\u0004\u0012\u00020\u0003\u0012\u0004\u0012\u00020\u0016\u0018\u00010\u0014X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b%\u0010\u0018\"\u0004\b&\u0010\u001aR(\u0010\'\u001a\u0010\u0012\u0004\u0012\u00020\u0003\u0012\u0004\u0012\u00020\u0016\u0018\u00010\u0014X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b(\u0010\u0018\"\u0004\b)\u0010\u001aR\"\u0010*\u001a\n\u0012\u0004\u0012\u00020\u0016\u0018\u00010\u001cX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b+\u0010\u001e\"\u0004\b,\u0010 R(\u0010-\u001a\u0010\u0012\u0004\u0012\u00020\u0003\u0012\u0004\u0012\u00020\u0016\u0018\u00010\u0014X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b.\u0010\u0018\"\u0004\b/\u0010\u001aR\"\u00100\u001a\n\u0012\u0004\u0012\u00020\u0016\u0018\u00010\u001cX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b1\u0010\u001e\"\u0004\b2\u0010 Ra\u00103\u001aI\u0012\u0013\u0012\u00110\u0003\u00a2\u0006\f\b5\u0012\b\b6\u0012\u0004\b\b(6\u0012\u0013\u0012\u001107\u00a2\u0006\f\b5\u0012\b\b6\u0012\u0004\b\b(8\u0012\u0013\u0012\u00110\u0003\u00a2\u0006\f\b5\u0012\b\b6\u0012\u0004\b\b(9\u0012\u0004\u0012\u00020\u0016\u0018\u000104X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b:\u0010;\"\u0004\b<\u0010=R\"\u0010>\u001a\n\u0012\u0004\u0012\u00020\u0016\u0018\u00010\u001cX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b?\u0010\u001e\"\u0004\b@\u0010 R\u000e\u0010A\u001a\u00020BX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010C\u001a\u00020DX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010E\u001a\u0004\u0018\u00010\u0012X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010F\u001a\u0004\u0018\u00010GX\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006_"}, d2 = {"Lcom/jarvis/assistant/ai/GeminiLiveClient;", "", "apiKey", "", "modelName", "systemPrompt", "voiceName", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", "client", "Lokhttp3/OkHttpClient;", "getClient", "()Lokhttp3/OkHttpClient;", "client$delegate", "Lkotlin/Lazy;", "isManuallyClosed", "", "isSetupComplete", "keepAliveJob", "Lkotlinx/coroutines/Job;", "onAudioReceived", "Lkotlin/Function1;", "", "", "getOnAudioReceived", "()Lkotlin/jvm/functions/Function1;", "setOnAudioReceived", "(Lkotlin/jvm/functions/Function1;)V", "onConnected", "Lkotlin/Function0;", "getOnConnected", "()Lkotlin/jvm/functions/Function0;", "setOnConnected", "(Lkotlin/jvm/functions/Function0;)V", "onDisconnected", "getOnDisconnected", "setOnDisconnected", "onError", "getOnError", "setOnError", "onInputTranscript", "getOnInputTranscript", "setOnInputTranscript", "onInterrupted", "getOnInterrupted", "setOnInterrupted", "onOutputTranscript", "getOnOutputTranscript", "setOnOutputTranscript", "onSetupComplete", "getOnSetupComplete", "setOnSetupComplete", "onToolCall", "Lkotlin/Function3;", "Lkotlin/ParameterName;", "name", "Lorg/json/JSONObject;", "args", "callId", "getOnToolCall", "()Lkotlin/jvm/functions/Function3;", "setOnToolCall", "(Lkotlin/jvm/functions/Function3;)V", "onTurnComplete", "getOnTurnComplete", "setOnTurnComplete", "reconnectAttempt", "", "scope", "Lkotlinx/coroutines/CoroutineScope;", "sessionRenewJob", "webSocket", "Lokhttp3/WebSocket;", "cleanupTimers", "connect", "disconnect", "manual", "handleServerMessage", "text", "isConnected", "scheduleReconnect", "sendAudioChunk", "pcmBytes", "sendInterrupt", "sendSetupMessage", "ws", "sendText", "turnComplete", "sendToolResponse", "functionName", "result", "sendVideoFrame", "jpegBytes", "startKeepAlive", "startSessionRenewalTimer", "Companion", "app_release"})
public final class GeminiLiveClient {
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String apiKey = null;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String modelName = null;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String systemPrompt = null;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String voiceName = null;
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "GeminiLiveClient";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String BASE_WS_URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent";
    private static final long SESSION_RENEW_AFTER_MS = 540000L;
    private static final long KEEPALIVE_INTERVAL_MS = 8000L;
    private static final long RECONNECT_BASE_DELAY_MS = 3000L;
    private static final long RECONNECT_MAX_DELAY_MS = 30000L;
    @org.jetbrains.annotations.Nullable()
    private kotlin.jvm.functions.Function0<kotlin.Unit> onConnected;
    @org.jetbrains.annotations.Nullable()
    private kotlin.jvm.functions.Function0<kotlin.Unit> onDisconnected;
    @org.jetbrains.annotations.Nullable()
    private kotlin.jvm.functions.Function0<kotlin.Unit> onSetupComplete;
    @org.jetbrains.annotations.Nullable()
    private kotlin.jvm.functions.Function1<? super byte[], kotlin.Unit> onAudioReceived;
    @org.jetbrains.annotations.Nullable()
    private kotlin.jvm.functions.Function1<? super java.lang.String, kotlin.Unit> onInputTranscript;
    @org.jetbrains.annotations.Nullable()
    private kotlin.jvm.functions.Function1<? super java.lang.String, kotlin.Unit> onOutputTranscript;
    @org.jetbrains.annotations.Nullable()
    private kotlin.jvm.functions.Function0<kotlin.Unit> onTurnComplete;
    @org.jetbrains.annotations.Nullable()
    private kotlin.jvm.functions.Function0<kotlin.Unit> onInterrupted;
    @org.jetbrains.annotations.Nullable()
    private kotlin.jvm.functions.Function1<? super java.lang.String, kotlin.Unit> onError;
    
    /**
     * Fired when Gemini decides to call a tool, e.g. open_app("YouTube").
     */
    @org.jetbrains.annotations.Nullable()
    private kotlin.jvm.functions.Function3<? super java.lang.String, ? super org.json.JSONObject, ? super java.lang.String, kotlin.Unit> onToolCall;
    @org.jetbrains.annotations.Nullable()
    private okhttp3.WebSocket webSocket;
    private boolean isManuallyClosed = false;
    private boolean isSetupComplete = false;
    private int reconnectAttempt = 0;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.CoroutineScope scope = null;
    @org.jetbrains.annotations.Nullable()
    private kotlinx.coroutines.Job keepAliveJob;
    @org.jetbrains.annotations.Nullable()
    private kotlinx.coroutines.Job sessionRenewJob;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy client$delegate = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.jarvis.assistant.ai.GeminiLiveClient.Companion Companion = null;
    
    public GeminiLiveClient(@org.jetbrains.annotations.NotNull()
    java.lang.String apiKey, @org.jetbrains.annotations.NotNull()
    java.lang.String modelName, @org.jetbrains.annotations.NotNull()
    java.lang.String systemPrompt, @org.jetbrains.annotations.NotNull()
    java.lang.String voiceName) {
        super();
    }
    
    @org.jetbrains.annotations.Nullable()
    public final kotlin.jvm.functions.Function0<kotlin.Unit> getOnConnected() {
        return null;
    }
    
    public final void setOnConnected(@org.jetbrains.annotations.Nullable()
    kotlin.jvm.functions.Function0<kotlin.Unit> p0) {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final kotlin.jvm.functions.Function0<kotlin.Unit> getOnDisconnected() {
        return null;
    }
    
    public final void setOnDisconnected(@org.jetbrains.annotations.Nullable()
    kotlin.jvm.functions.Function0<kotlin.Unit> p0) {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final kotlin.jvm.functions.Function0<kotlin.Unit> getOnSetupComplete() {
        return null;
    }
    
    public final void setOnSetupComplete(@org.jetbrains.annotations.Nullable()
    kotlin.jvm.functions.Function0<kotlin.Unit> p0) {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final kotlin.jvm.functions.Function1<byte[], kotlin.Unit> getOnAudioReceived() {
        return null;
    }
    
    public final void setOnAudioReceived(@org.jetbrains.annotations.Nullable()
    kotlin.jvm.functions.Function1<? super byte[], kotlin.Unit> p0) {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final kotlin.jvm.functions.Function1<java.lang.String, kotlin.Unit> getOnInputTranscript() {
        return null;
    }
    
    public final void setOnInputTranscript(@org.jetbrains.annotations.Nullable()
    kotlin.jvm.functions.Function1<? super java.lang.String, kotlin.Unit> p0) {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final kotlin.jvm.functions.Function1<java.lang.String, kotlin.Unit> getOnOutputTranscript() {
        return null;
    }
    
    public final void setOnOutputTranscript(@org.jetbrains.annotations.Nullable()
    kotlin.jvm.functions.Function1<? super java.lang.String, kotlin.Unit> p0) {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final kotlin.jvm.functions.Function0<kotlin.Unit> getOnTurnComplete() {
        return null;
    }
    
    public final void setOnTurnComplete(@org.jetbrains.annotations.Nullable()
    kotlin.jvm.functions.Function0<kotlin.Unit> p0) {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final kotlin.jvm.functions.Function0<kotlin.Unit> getOnInterrupted() {
        return null;
    }
    
    public final void setOnInterrupted(@org.jetbrains.annotations.Nullable()
    kotlin.jvm.functions.Function0<kotlin.Unit> p0) {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final kotlin.jvm.functions.Function1<java.lang.String, kotlin.Unit> getOnError() {
        return null;
    }
    
    public final void setOnError(@org.jetbrains.annotations.Nullable()
    kotlin.jvm.functions.Function1<? super java.lang.String, kotlin.Unit> p0) {
    }
    
    /**
     * Fired when Gemini decides to call a tool, e.g. open_app("YouTube").
     */
    @org.jetbrains.annotations.Nullable()
    public final kotlin.jvm.functions.Function3<java.lang.String, org.json.JSONObject, java.lang.String, kotlin.Unit> getOnToolCall() {
        return null;
    }
    
    /**
     * Fired when Gemini decides to call a tool, e.g. open_app("YouTube").
     */
    public final void setOnToolCall(@org.jetbrains.annotations.Nullable()
    kotlin.jvm.functions.Function3<? super java.lang.String, ? super org.json.JSONObject, ? super java.lang.String, kotlin.Unit> p0) {
    }
    
    private final okhttp3.OkHttpClient getClient() {
        return null;
    }
    
    public final void connect() {
    }
    
    private final void scheduleReconnect() {
    }
    
    private final void sendSetupMessage(okhttp3.WebSocket ws) {
    }
    
    /**
     * Send a chunk of 16kHz mono PCM16 mic audio.
     */
    public final void sendAudioChunk(@org.jetbrains.annotations.NotNull()
    byte[] pcmBytes) {
    }
    
    /**
     * Send a live vision screen capture frame (JPEG image) to Gemini Live.
     */
    public final void sendVideoFrame(@org.jetbrains.annotations.NotNull()
    byte[] jpegBytes) {
    }
    
    /**
     * Send a free-form text turn to JARVIS (e.g. text chat, phone-action confirmations).
     */
    public final void sendText(@org.jetbrains.annotations.NotNull()
    java.lang.String text, boolean turnComplete) {
    }
    
    /**
     * Interrupt JARVIS mid-speech (e.g. on long-press of mic button).
     */
    public final void sendInterrupt() {
    }
    
    /**
     * Send the result of a tool call back to Gemini so it can react
     * (e.g. confirm out loud that the app was opened, or apologize if not found).
     */
    public final void sendToolResponse(@org.jetbrains.annotations.NotNull()
    java.lang.String callId, @org.jetbrains.annotations.NotNull()
    java.lang.String functionName, @org.jetbrains.annotations.NotNull()
    org.json.JSONObject result) {
    }
    
    private final void handleServerMessage(java.lang.String text) {
    }
    
    private final void startKeepAlive() {
    }
    
    private final void startSessionRenewalTimer() {
    }
    
    private final void cleanupTimers() {
    }
    
    public final void disconnect(boolean manual) {
    }
    
    public final boolean isConnected() {
        return false;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u001a\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\t\n\u0002\b\u0005\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0006X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0006X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\u0006X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u000b"}, d2 = {"Lcom/jarvis/assistant/ai/GeminiLiveClient$Companion;", "", "()V", "BASE_WS_URL", "", "KEEPALIVE_INTERVAL_MS", "", "RECONNECT_BASE_DELAY_MS", "RECONNECT_MAX_DELAY_MS", "SESSION_RENEW_AFTER_MS", "TAG", "app_release"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}