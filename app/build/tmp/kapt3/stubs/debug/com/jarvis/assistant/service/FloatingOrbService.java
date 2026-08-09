package com.jarvis.assistant.service;

/**
 * System Overlay Service that renders a draggable, glowing 3D liquid fluid orb
 * widget floating over the Android Home Screen and all other applications.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000h\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u000e\n\u0002\u0018\u0002\n\u0002\b\u0002\u0018\u0000 /2\u00020\u0001:\u0001/B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0018\u0010\u0013\u001a\u00020\u00142\u0006\u0010\u0015\u001a\u00020\u00162\u0006\u0010\u0017\u001a\u00020\u0016H\u0002J\b\u0010\u0018\u001a\u00020\u0014H\u0002J\b\u0010\u0019\u001a\u00020\u001aH\u0002J\b\u0010\u001b\u001a\u00020\u0014H\u0002J\u0014\u0010\u001c\u001a\u0004\u0018\u00010\u001d2\b\u0010\u001e\u001a\u0004\u0018\u00010\u001fH\u0016J\b\u0010 \u001a\u00020\u0014H\u0016J\b\u0010!\u001a\u00020\u0014H\u0016J\"\u0010\"\u001a\u00020\u00162\b\u0010\u001e\u001a\u0004\u0018\u00010\u001f2\u0006\u0010#\u001a\u00020\u00162\u0006\u0010$\u001a\u00020\u0016H\u0016J\b\u0010%\u001a\u00020\u0014H\u0002J\b\u0010&\u001a\u00020\u0014H\u0002J\u0018\u0010\'\u001a\u00020\u00142\u0006\u0010(\u001a\u00020\u00162\u0006\u0010)\u001a\u00020\u0016H\u0002J\b\u0010*\u001a\u00020\u0014H\u0002J\b\u0010+\u001a\u00020\u0014H\u0002J\u0010\u0010,\u001a\u00020\u00142\u0006\u0010-\u001a\u00020.H\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0005\u001a\u0004\u0018\u00010\u0006X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0007\u001a\u0004\u0018\u00010\bX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\t\u001a\u0004\u0018\u00010\nX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\fX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\r\u001a\u00020\u000eX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u000f\u001a\u0004\u0018\u00010\u0010X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0011\u001a\u0004\u0018\u00010\u0012X\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u00060"}, d2 = {"Lcom/jarvis/assistant/service/FloatingOrbService;", "Landroid/app/Service;", "()V", "isBoundToVoiceService", "", "layoutParams", "Landroid/view/WindowManager$LayoutParams;", "orbView", "Lcom/jarvis/assistant/ui/main/OrbAnimationView;", "overlayContainer", "Landroid/widget/FrameLayout;", "serviceConnection", "Landroid/content/ServiceConnection;", "voiceListener", "Lcom/jarvis/assistant/service/JarvisVoiceService$JarvisVoiceListener;", "voiceService", "Lcom/jarvis/assistant/service/JarvisVoiceService;", "windowManager", "Landroid/view/WindowManager;", "animateSnapToEdge", "", "startX", "", "endX", "bindVoiceService", "createNotification", "Landroid/app/Notification;", "createNotificationChannel", "onBind", "Landroid/os/IBinder;", "intent", "Landroid/content/Intent;", "onCreate", "onDestroy", "onStartCommand", "flags", "startId", "openMainActivity", "setupOverlayWindow", "setupTouchListener", "screenWidth", "orbSizePx", "triggerVoiceAction", "unbindVoiceService", "updateOrbState", "state", "Lcom/jarvis/assistant/ui/main/OrbAnimationView$OrbState;", "Companion", "app_debug"})
public final class FloatingOrbService extends android.app.Service {
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "FloatingOrbService";
    private static final int NOTIFICATION_ID = 202;
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String CHANNEL_ID = "jarvis_floating_orb_channel";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String ACTION_START = "com.jarvis.assistant.action.START_FLOATING_ORB";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String ACTION_STOP = "com.jarvis.assistant.action.STOP_FLOATING_ORB";
    @org.jetbrains.annotations.Nullable()
    private android.view.WindowManager windowManager;
    @org.jetbrains.annotations.Nullable()
    private android.widget.FrameLayout overlayContainer;
    @org.jetbrains.annotations.Nullable()
    private com.jarvis.assistant.ui.main.OrbAnimationView orbView;
    @org.jetbrains.annotations.Nullable()
    private android.view.WindowManager.LayoutParams layoutParams;
    @org.jetbrains.annotations.Nullable()
    private com.jarvis.assistant.service.JarvisVoiceService voiceService;
    private boolean isBoundToVoiceService = false;
    @org.jetbrains.annotations.NotNull()
    private final android.content.ServiceConnection serviceConnection = null;
    @org.jetbrains.annotations.NotNull()
    private final com.jarvis.assistant.service.JarvisVoiceService.JarvisVoiceListener voiceListener = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.jarvis.assistant.service.FloatingOrbService.Companion Companion = null;
    
    public FloatingOrbService() {
        super();
    }
    
    @java.lang.Override()
    public void onCreate() {
    }
    
    @java.lang.Override()
    public int onStartCommand(@org.jetbrains.annotations.Nullable()
    android.content.Intent intent, int flags, int startId) {
        return 0;
    }
    
    private final void createNotificationChannel() {
    }
    
    private final android.app.Notification createNotification() {
        return null;
    }
    
    private final void setupOverlayWindow() {
    }
    
    private final void setupTouchListener(int screenWidth, int orbSizePx) {
    }
    
    private final void animateSnapToEdge(int startX, int endX) {
    }
    
    private final void triggerVoiceAction() {
    }
    
    private final void openMainActivity() {
    }
    
    private final void updateOrbState(com.jarvis.assistant.ui.main.OrbAnimationView.OrbState state) {
    }
    
    private final void bindVoiceService() {
    }
    
    private final void unbindVoiceService() {
    }
    
    @java.lang.Override()
    public void onDestroy() {
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public android.os.IBinder onBind(@org.jetbrains.annotations.Nullable()
    android.content.Intent intent) {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\n\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\rJ\u000e\u0010\u000e\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\rR\u000e\u0010\u0003\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\bX\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u000f"}, d2 = {"Lcom/jarvis/assistant/service/FloatingOrbService$Companion;", "", "()V", "ACTION_START", "", "ACTION_STOP", "CHANNEL_ID", "NOTIFICATION_ID", "", "TAG", "startService", "", "context", "Landroid/content/Context;", "stopService", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
        
        public final void startService(@org.jetbrains.annotations.NotNull()
        android.content.Context context) {
        }
        
        public final void stopService(@org.jetbrains.annotations.NotNull()
        android.content.Context context) {
        }
    }
}