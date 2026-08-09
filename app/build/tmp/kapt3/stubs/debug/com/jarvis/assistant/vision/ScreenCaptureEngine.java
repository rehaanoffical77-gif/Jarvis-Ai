package com.jarvis.assistant.vision;

/**
 * Captures live screen frames using Android MediaProjection + VirtualDisplay + ImageReader
 * and compresses them to JPEG byte arrays at ~1 FPS for real-time Gemini Live vision input.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000L\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u0012\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\t\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0006\u0018\u0000 \u001b2\u00020\u0001:\u0001\u001bB)\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0012\u0010\u0006\u001a\u000e\u0012\u0004\u0012\u00020\b\u0012\u0004\u0012\u00020\t0\u0007\u00a2\u0006\u0002\u0010\nJ\u0010\u0010\u0017\u001a\u00020\t2\u0006\u0010\u0018\u001a\u00020\u0010H\u0002J\u0006\u0010\u0019\u001a\u00020\tJ\u0006\u0010\u001a\u001a\u00020\tR\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u000b\u001a\u0004\u0018\u00010\fX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\r\u001a\u0004\u0018\u00010\u000eX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u000f\u001a\u0004\u0018\u00010\u0010X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0011\u001a\u00020\u0012X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0013\u001a\u00020\u0014X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001a\u0010\u0006\u001a\u000e\u0012\u0004\u0012\u00020\b\u0012\u0004\u0012\u00020\t0\u0007X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0015\u001a\u0004\u0018\u00010\u0016X\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u001c"}, d2 = {"Lcom/jarvis/assistant/vision/ScreenCaptureEngine;", "", "context", "Landroid/content/Context;", "mediaProjection", "Landroid/media/projection/MediaProjection;", "onFrameCaptured", "Lkotlin/Function1;", "", "", "(Landroid/content/Context;Landroid/media/projection/MediaProjection;Lkotlin/jvm/functions/Function1;)V", "handler", "Landroid/os/Handler;", "handlerThread", "Landroid/os/HandlerThread;", "imageReader", "Landroid/media/ImageReader;", "isCapturing", "", "lastCapturedTimeMs", "", "virtualDisplay", "Landroid/hardware/display/VirtualDisplay;", "processNextFrame", "reader", "start", "stop", "Companion", "app_debug"})
public final class ScreenCaptureEngine {
    @org.jetbrains.annotations.NotNull()
    private final android.content.Context context = null;
    @org.jetbrains.annotations.NotNull()
    private final android.media.projection.MediaProjection mediaProjection = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.jvm.functions.Function1<byte[], kotlin.Unit> onFrameCaptured = null;
    @org.jetbrains.annotations.Nullable()
    private android.hardware.display.VirtualDisplay virtualDisplay;
    @org.jetbrains.annotations.Nullable()
    private android.media.ImageReader imageReader;
    @org.jetbrains.annotations.Nullable()
    private android.os.HandlerThread handlerThread;
    @org.jetbrains.annotations.Nullable()
    private android.os.Handler handler;
    private boolean isCapturing = false;
    private long lastCapturedTimeMs = 0L;
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "ScreenCaptureEngine";
    private static final long CAPTURE_INTERVAL_MS = 1500L;
    private static final int MAX_WIDTH = 360;
    private static final int MAX_HEIGHT = 640;
    @org.jetbrains.annotations.NotNull()
    public static final com.jarvis.assistant.vision.ScreenCaptureEngine.Companion Companion = null;
    
    public ScreenCaptureEngine(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.NotNull()
    android.media.projection.MediaProjection mediaProjection, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function1<? super byte[], kotlin.Unit> onFrameCaptured) {
        super();
    }
    
    public final void start() {
    }
    
    private final void processNextFrame(android.media.ImageReader reader) {
    }
    
    public final void stop() {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\t\n\u0000\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0006X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\tX\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\n"}, d2 = {"Lcom/jarvis/assistant/vision/ScreenCaptureEngine$Companion;", "", "()V", "CAPTURE_INTERVAL_MS", "", "MAX_HEIGHT", "", "MAX_WIDTH", "TAG", "", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}