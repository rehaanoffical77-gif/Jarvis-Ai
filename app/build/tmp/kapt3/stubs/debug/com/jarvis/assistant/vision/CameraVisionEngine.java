package com.jarvis.assistant.vision;

/**
 * Live Camera Vision engine using Android Camera2 API + ImageReader.
 * Captures live frames from Front or Back camera at ~1 FPS, compresses to JPEG,
 * and passes byte arrays to Gemini Live for real-time vision processing.
 * Renders a live camera preview onto a target TextureView.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000h\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u0012\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\t\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u000f\n\u0002\u0018\u0002\n\u0002\b\u0002\u0018\u0000 .2\u00020\u0001:\u0001.B!\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0012\u0010\u0004\u001a\u000e\u0012\u0004\u0012\u00020\u0006\u0012\u0004\u0012\u00020\u00070\u0005\u00a2\u0006\u0002\u0010\bJ\u0012\u0010\u001c\u001a\u0004\u0018\u00010\u001d2\u0006\u0010\u001e\u001a\u00020\u0016H\u0002J\u0006\u0010\u001f\u001a\u00020\u0016J\u0006\u0010 \u001a\u00020\u0016J\u0010\u0010!\u001a\u00020\u00072\u0006\u0010\"\u001a\u00020\u0014H\u0002J\u0010\u0010#\u001a\u00020\u00072\b\u0010$\u001a\u0004\u0018\u00010\u001bJ\b\u0010%\u001a\u00020\u0007H\u0002J\u0012\u0010&\u001a\u00020\u00072\b\b\u0002\u0010\u001e\u001a\u00020\u0016H\u0007J\b\u0010\'\u001a\u00020\u0007H\u0002J\b\u0010(\u001a\u00020\u0007H\u0002J\u0006\u0010)\u001a\u00020\u0007J\u0006\u0010*\u001a\u00020\u0007J\u0012\u0010+\u001a\u0004\u0018\u00010\u00062\u0006\u0010,\u001a\u00020-H\u0002R\u0010\u0010\t\u001a\u0004\u0018\u00010\nX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u000b\u001a\u0004\u0018\u00010\fX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\r\u001a\u0004\u0018\u00010\u000eX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000f\u001a\u00020\u0010X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0011\u001a\u0004\u0018\u00010\u0012X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0013\u001a\u0004\u0018\u00010\u0014X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0015\u001a\u00020\u0016X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0017\u001a\u00020\u0016X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0018\u001a\u00020\u0019X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u001a\u0010\u0004\u001a\u000e\u0012\u0004\u0012\u00020\u0006\u0012\u0004\u0012\u00020\u00070\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u001a\u001a\u0004\u0018\u00010\u001bX\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006/"}, d2 = {"Lcom/jarvis/assistant/vision/CameraVisionEngine;", "", "context", "Landroid/content/Context;", "onFrameCaptured", "Lkotlin/Function1;", "", "", "(Landroid/content/Context;Lkotlin/jvm/functions/Function1;)V", "backgroundHandler", "Landroid/os/Handler;", "backgroundThread", "Landroid/os/HandlerThread;", "cameraDevice", "Landroid/hardware/camera2/CameraDevice;", "cameraManager", "Landroid/hardware/camera2/CameraManager;", "captureSession", "Landroid/hardware/camera2/CameraCaptureSession;", "imageReader", "Landroid/media/ImageReader;", "isFrontCamera", "", "isStreaming", "lastFrameTimeMs", "", "previewTextureView", "Landroid/view/TextureView;", "getCameraId", "", "useFront", "isCameraStreaming", "isFrontLens", "processImage", "reader", "setPreviewTextureView", "textureView", "startBackgroundThread", "startCamera", "startCaptureSession", "stopBackgroundThread", "stopCamera", "switchCamera", "yuv420ToJpeg", "image", "Landroid/media/Image;", "Companion", "app_debug"})
public final class CameraVisionEngine {
    @org.jetbrains.annotations.NotNull()
    private final android.content.Context context = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.jvm.functions.Function1<byte[], kotlin.Unit> onFrameCaptured = null;
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "CameraVisionEngine";
    private static final long CAPTURE_INTERVAL_MS = 1000L;
    private static final int TARGET_WIDTH = 480;
    private static final int TARGET_HEIGHT = 640;
    @org.jetbrains.annotations.NotNull()
    private final android.hardware.camera2.CameraManager cameraManager = null;
    @org.jetbrains.annotations.Nullable()
    private android.hardware.camera2.CameraDevice cameraDevice;
    @org.jetbrains.annotations.Nullable()
    private android.hardware.camera2.CameraCaptureSession captureSession;
    @org.jetbrains.annotations.Nullable()
    private android.media.ImageReader imageReader;
    @org.jetbrains.annotations.Nullable()
    private android.os.HandlerThread backgroundThread;
    @org.jetbrains.annotations.Nullable()
    private android.os.Handler backgroundHandler;
    private boolean isFrontCamera = false;
    private boolean isStreaming = false;
    private long lastFrameTimeMs = 0L;
    @org.jetbrains.annotations.Nullable()
    private android.view.TextureView previewTextureView;
    @org.jetbrains.annotations.NotNull()
    public static final com.jarvis.assistant.vision.CameraVisionEngine.Companion Companion = null;
    
    public CameraVisionEngine(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function1<? super byte[], kotlin.Unit> onFrameCaptured) {
        super();
    }
    
    public final void setPreviewTextureView(@org.jetbrains.annotations.Nullable()
    android.view.TextureView textureView) {
    }
    
    public final boolean isFrontLens() {
        return false;
    }
    
    public final boolean isCameraStreaming() {
        return false;
    }
    
    @android.annotation.SuppressLint(value = {"MissingPermission"})
    public final void startCamera(boolean useFront) {
    }
    
    public final void switchCamera() {
    }
    
    public final void stopCamera() {
    }
    
    private final java.lang.String getCameraId(boolean useFront) {
        return null;
    }
    
    private final void startCaptureSession() {
    }
    
    private final void processImage(android.media.ImageReader reader) {
    }
    
    private final byte[] yuv420ToJpeg(android.media.Image image) {
        return null;
    }
    
    private final void startBackgroundThread() {
    }
    
    private final void stopBackgroundThread() {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\t\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\bX\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\bX\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\n"}, d2 = {"Lcom/jarvis/assistant/vision/CameraVisionEngine$Companion;", "", "()V", "CAPTURE_INTERVAL_MS", "", "TAG", "", "TARGET_HEIGHT", "", "TARGET_WIDTH", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}