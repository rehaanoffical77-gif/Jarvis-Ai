package com.jarvis.assistant.ai;

/**
 * Handles mic capture (AudioRecord, 16kHz mono PCM16) and
 * speaker playback (AudioTrack, 24kHz mono PCM16) — mirrors the
 * Python `sounddevice` reference pipeline.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000l\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u0007\n\u0002\u0010\u0002\n\u0002\b\u0005\n\u0002\u0010\u0012\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u000b\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0012\u0018\u0000 B2\u00020\u0001:\u0001BB\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u0010\u00103\u001a\u00020\u00182\u0006\u00104\u001a\u00020\u001fH\u0002J\u0006\u00105\u001a\u00020\u0019J\u0006\u00106\u001a\u00020\u0010J\u0006\u0010\u0011\u001a\u00020\u0010J\u000e\u00107\u001a\u00020\u00192\u0006\u00108\u001a\u00020\u001fJ\u0006\u00109\u001a\u00020\u0019J\u000e\u0010:\u001a\u00020\u00192\u0006\u0010;\u001a\u00020\u0010J\u000e\u0010<\u001a\u00020\u00192\u0006\u0010=\u001a\u00020\u0010J\u0006\u0010>\u001a\u00020\u0019J\b\u0010?\u001a\u00020\u0019H\u0007J\u0006\u0010@\u001a\u00020\u0019J\u0006\u0010A\u001a\u00020\u0019R\u0010\u0010\u0005\u001a\u0004\u0018\u00010\u0006X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0007\u001a\u0004\u0018\u00010\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010\t\u001a\u0004\u0018\u00010\nX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u000b\u001a\u0004\u0018\u00010\fX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\r\u001a\u00020\u000eX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000f\u001a\u00020\u0010X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0011\u001a\u00020\u0010X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0012\u001a\u00020\u0010X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0013\u001a\u00020\u0010X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0014\u001a\u0004\u0018\u00010\u0015X\u0082\u000e\u00a2\u0006\u0002\n\u0000R(\u0010\u0016\u001a\u0010\u0012\u0004\u0012\u00020\u0018\u0012\u0004\u0012\u00020\u0019\u0018\u00010\u0017X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u001a\u0010\u001b\"\u0004\b\u001c\u0010\u001dR(\u0010\u001e\u001a\u0010\u0012\u0004\u0012\u00020\u001f\u0012\u0004\u0012\u00020\u0019\u0018\u00010\u0017X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b \u0010\u001b\"\u0004\b!\u0010\u001dR\"\u0010\"\u001a\n\u0012\u0004\u0012\u00020\u0019\u0018\u00010#X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b$\u0010%\"\u0004\b&\u0010\'R\"\u0010(\u001a\n\u0012\u0004\u0012\u00020\u0019\u0018\u00010#X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b)\u0010%\"\u0004\b*\u0010\'R\"\u0010+\u001a\n\u0012\u0004\u0012\u00020\u0019\u0018\u00010#X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b,\u0010%\"\u0004\b-\u0010\'R\u0010\u0010.\u001a\u0004\u0018\u00010/X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0014\u00100\u001a\b\u0012\u0004\u0012\u00020\u001f01X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u00102\u001a\u0004\u0018\u00010/X\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006C"}, d2 = {"Lcom/jarvis/assistant/ai/AudioEngine;", "", "context", "Landroid/content/Context;", "(Landroid/content/Context;)V", "aec", "Landroid/media/audiofx/AcousticEchoCanceler;", "audioManager", "Landroid/media/AudioManager;", "audioRecord", "Landroid/media/AudioRecord;", "audioTrack", "Landroid/media/AudioTrack;", "engineScope", "Lkotlinx/coroutines/CoroutineScope;", "isExternalSpeaking", "", "isMuted", "isRecording", "isSpeaking", "ns", "Landroid/media/audiofx/NoiseSuppressor;", "onAmplitudeChanged", "Lkotlin/Function1;", "", "", "getOnAmplitudeChanged", "()Lkotlin/jvm/functions/Function1;", "setOnAmplitudeChanged", "(Lkotlin/jvm/functions/Function1;)V", "onAudioChunkCaptured", "", "getOnAudioChunkCaptured", "setOnAudioChunkCaptured", "onInterruptTriggered", "Lkotlin/Function0;", "getOnInterruptTriggered", "()Lkotlin/jvm/functions/Function0;", "setOnInterruptTriggered", "(Lkotlin/jvm/functions/Function0;)V", "onSpeakingStarted", "getOnSpeakingStarted", "setOnSpeakingStarted", "onSpeakingStopped", "getOnSpeakingStopped", "setOnSpeakingStopped", "playbackJob", "Lkotlinx/coroutines/Job;", "playbackQueue", "Ljava/util/concurrent/ConcurrentLinkedQueue;", "recordJob", "calculateRms", "chunk", "clearPlaybackQueue", "isCurrentlySpeaking", "queueAudio", "pcmBytes", "release", "setExternalSpeaking", "speaking", "setMuted", "muted", "startPlayback", "startRecording", "stopPlayback", "stopRecording", "Companion", "app_release"})
public final class AudioEngine {
    @org.jetbrains.annotations.NotNull()
    private final android.content.Context context = null;
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "AudioEngine";
    public static final int MIC_SAMPLE_RATE = 16000;
    public static final int SPEAKER_SAMPLE_RATE = 24000;
    public static final int CHUNK_SIZE = 640;
    private static final int PREBUFFER_BYTES = 3840;
    private static final long SPEAK_STOP_DEBOUNCE_MS = 150L;
    private static final float BARGE_IN_RMS_THRESHOLD = 0.07F;
    private static final int BARGE_IN_CONSECUTIVE_CHUNKS = 2;
    @org.jetbrains.annotations.Nullable()
    private kotlin.jvm.functions.Function1<? super byte[], kotlin.Unit> onAudioChunkCaptured;
    @org.jetbrains.annotations.Nullable()
    private kotlin.jvm.functions.Function1<? super java.lang.Float, kotlin.Unit> onAmplitudeChanged;
    @org.jetbrains.annotations.Nullable()
    private kotlin.jvm.functions.Function0<kotlin.Unit> onSpeakingStarted;
    @org.jetbrains.annotations.Nullable()
    private kotlin.jvm.functions.Function0<kotlin.Unit> onSpeakingStopped;
    @org.jetbrains.annotations.Nullable()
    private kotlin.jvm.functions.Function0<kotlin.Unit> onInterruptTriggered;
    @org.jetbrains.annotations.Nullable()
    private android.media.AudioRecord audioRecord;
    @org.jetbrains.annotations.Nullable()
    private android.media.AudioTrack audioTrack;
    @org.jetbrains.annotations.Nullable()
    private android.media.audiofx.AcousticEchoCanceler aec;
    @org.jetbrains.annotations.Nullable()
    private android.media.audiofx.NoiseSuppressor ns;
    @org.jetbrains.annotations.Nullable()
    private final android.media.AudioManager audioManager = null;
    private boolean isRecording = false;
    private boolean isMuted = false;
    @kotlin.jvm.Volatile()
    private volatile boolean isSpeaking = false;
    @kotlin.jvm.Volatile()
    private volatile boolean isExternalSpeaking = false;
    @org.jetbrains.annotations.NotNull()
    private final java.util.concurrent.ConcurrentLinkedQueue<byte[]> playbackQueue = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.CoroutineScope engineScope = null;
    @org.jetbrains.annotations.Nullable()
    private kotlinx.coroutines.Job recordJob;
    @org.jetbrains.annotations.Nullable()
    private kotlinx.coroutines.Job playbackJob;
    @org.jetbrains.annotations.NotNull()
    public static final com.jarvis.assistant.ai.AudioEngine.Companion Companion = null;
    
    public AudioEngine(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        super();
    }
    
    @org.jetbrains.annotations.Nullable()
    public final kotlin.jvm.functions.Function1<byte[], kotlin.Unit> getOnAudioChunkCaptured() {
        return null;
    }
    
    public final void setOnAudioChunkCaptured(@org.jetbrains.annotations.Nullable()
    kotlin.jvm.functions.Function1<? super byte[], kotlin.Unit> p0) {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final kotlin.jvm.functions.Function1<java.lang.Float, kotlin.Unit> getOnAmplitudeChanged() {
        return null;
    }
    
    public final void setOnAmplitudeChanged(@org.jetbrains.annotations.Nullable()
    kotlin.jvm.functions.Function1<? super java.lang.Float, kotlin.Unit> p0) {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final kotlin.jvm.functions.Function0<kotlin.Unit> getOnSpeakingStarted() {
        return null;
    }
    
    public final void setOnSpeakingStarted(@org.jetbrains.annotations.Nullable()
    kotlin.jvm.functions.Function0<kotlin.Unit> p0) {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final kotlin.jvm.functions.Function0<kotlin.Unit> getOnSpeakingStopped() {
        return null;
    }
    
    public final void setOnSpeakingStopped(@org.jetbrains.annotations.Nullable()
    kotlin.jvm.functions.Function0<kotlin.Unit> p0) {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final kotlin.jvm.functions.Function0<kotlin.Unit> getOnInterruptTriggered() {
        return null;
    }
    
    public final void setOnInterruptTriggered(@org.jetbrains.annotations.Nullable()
    kotlin.jvm.functions.Function0<kotlin.Unit> p0) {
    }
    
    /**
     * True while JARVIS's audio is playing — used to suppress mic echo.
     */
    public final boolean isCurrentlySpeaking() {
        return false;
    }
    
    public final void setExternalSpeaking(boolean speaking) {
    }
    
    @android.annotation.SuppressLint(value = {"MissingPermission"})
    public final void startRecording() {
    }
    
    public final void stopRecording() {
    }
    
    public final void startPlayback() {
    }
    
    public final void stopPlayback() {
    }
    
    /**
     * Queue a chunk of 24kHz PCM16 audio received from Gemini for playback.
     */
    public final void queueAudio(@org.jetbrains.annotations.NotNull()
    byte[] pcmBytes) {
    }
    
    /**
     * Clear pending playback and reset speaking state (used on interrupt / long-press).
     */
    public final void clearPlaybackQueue() {
    }
    
    public final void setMuted(boolean muted) {
    }
    
    public final boolean isMuted() {
        return false;
    }
    
    public final void release() {
    }
    
    private final float calculateRms(byte[] chunk) {
        return 0.0F;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u0007\n\u0002\b\u0005\n\u0002\u0010\t\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\fX\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\r\u001a\u00020\u000eX\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u000f"}, d2 = {"Lcom/jarvis/assistant/ai/AudioEngine$Companion;", "", "()V", "BARGE_IN_CONSECUTIVE_CHUNKS", "", "BARGE_IN_RMS_THRESHOLD", "", "CHUNK_SIZE", "MIC_SAMPLE_RATE", "PREBUFFER_BYTES", "SPEAKER_SAMPLE_RATE", "SPEAK_STOP_DEBOUNCE_MS", "", "TAG", "", "app_release"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}