package com.jarvis.assistant.ui.main;

/**
 * The JARVIS orb, ported 1:1 from the reference "JARVIS Professional Blue" WebGL
 * fragment shader (identical vertex/fragment GLSL source, uniforms, and color
 * math) onto native OpenGL ES 2.0.
 *
 * Uses [TextureView] (backed by a self-managed EGL render thread) rather than
 * [android.opengl.GLSurfaceView] / [android.view.SurfaceView], because those
 * composite via a "hole punch" that can misbehave behind non-rectangular
 * backgrounds (e.g. the floating overlay's circular frame) or overlapping
 * sibling views (e.g. the overlay's close button). TextureView is a regular
 * View for compositing purposes, so it just works everywhere this orb is used.
 *
 * Public API (`OrbState`, `setState()`, `setAmplitude()`, plus `onResume()` /
 * `onPause()` for lifecycle parity with the old GLSurfaceView-shaped API) is
 * unchanged, so this drops in with no layout or call-site changes.
 *
 * State → color mapping (blue "JARVIS Professional Blue" family, shared with
 * colors.xml and MainActivity's waveform/mic-ring tinting):
 * - IDLE                → cool grey-blue, slow drift
 * - LISTENING / ACTIVE  → primary blue rings
 * - THINKING            → cyan arc
 * - SPEAKING            → steel-blue waves
 *
 * Transitions between states ease in over ~10 frames (not an instant snap) and
 * briefly brighten ("light up") right at the moment of change, so switching
 * Idle/Listen/Think/Speak reads as a deliberate cue instead of a jump cut.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000P\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0007\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\b\u0005\u0018\u00002\u00020\u00012\u00020\u0002:\u0002!\"B\u001b\b\u0007\u0012\u0006\u0010\u0003\u001a\u00020\u0004\u0012\n\b\u0002\u0010\u0005\u001a\u0004\u0018\u00010\u0006\u00a2\u0006\u0002\u0010\u0007J\u0006\u0010\r\u001a\u00020\u000eJ\u0006\u0010\u000f\u001a\u00020\u000eJ \u0010\u0010\u001a\u00020\u000e2\u0006\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u0013\u001a\u00020\u00142\u0006\u0010\u0015\u001a\u00020\u0014H\u0016J\u0010\u0010\u0016\u001a\u00020\u00172\u0006\u0010\u0011\u001a\u00020\u0012H\u0016J \u0010\u0018\u001a\u00020\u000e2\u0006\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u0013\u001a\u00020\u00142\u0006\u0010\u0015\u001a\u00020\u0014H\u0016J\u0010\u0010\u0019\u001a\u00020\u000e2\u0006\u0010\u0011\u001a\u00020\u0012H\u0016J\u000e\u0010\u001a\u001a\u00020\u000e2\u0006\u0010\u001b\u001a\u00020\tJ\u000e\u0010\u001c\u001a\u00020\u000e2\u0006\u0010\u001d\u001a\u00020\u001eJ\u0010\u0010\u001f\u001a\u00020\t2\u0006\u0010 \u001a\u00020\u001eH\u0002R\u000e\u0010\b\u001a\u00020\tX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\tX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u000b\u001a\u0004\u0018\u00010\fX\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006#"}, d2 = {"Lcom/jarvis/assistant/ui/main/OrbAnimationView;", "Landroid/view/TextureView;", "Landroid/view/TextureView$SurfaceTextureListener;", "context", "Landroid/content/Context;", "attrs", "Landroid/util/AttributeSet;", "(Landroid/content/Context;Landroid/util/AttributeSet;)V", "pendingAmplitude", "", "pendingState", "renderThread", "Lcom/jarvis/assistant/ui/main/OrbAnimationView$OrbRenderThread;", "onPause", "", "onResume", "onSurfaceTextureAvailable", "surface", "Landroid/graphics/SurfaceTexture;", "width", "", "height", "onSurfaceTextureDestroyed", "", "onSurfaceTextureSizeChanged", "onSurfaceTextureUpdated", "setAmplitude", "value", "setState", "newState", "Lcom/jarvis/assistant/ui/main/OrbAnimationView$OrbState;", "stateToUniform", "state", "OrbRenderThread", "OrbState", "app_release"})
public final class OrbAnimationView extends android.view.TextureView implements android.view.TextureView.SurfaceTextureListener {
    @org.jetbrains.annotations.Nullable()
    private com.jarvis.assistant.ui.main.OrbAnimationView.OrbRenderThread renderThread;
    private float pendingState = 0.0F;
    private float pendingAmplitude = 0.0F;
    
    @kotlin.jvm.JvmOverloads()
    public OrbAnimationView(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.Nullable()
    android.util.AttributeSet attrs) {
        super(null);
    }
    
    public final void setState(@org.jetbrains.annotations.NotNull()
    com.jarvis.assistant.ui.main.OrbAnimationView.OrbState newState) {
    }
    
    public final void setAmplitude(float value) {
    }
    
    /**
     * No-op if the surface is already live; kept for API parity with the Activity/Service lifecycle wiring.
     */
    public final void onResume() {
    }
    
    /**
     * Pauses the draw loop without tearing down the EGL context, so resuming is instant.
     */
    public final void onPause() {
    }
    
    private final float stateToUniform(com.jarvis.assistant.ui.main.OrbAnimationView.OrbState state) {
        return 0.0F;
    }
    
    @java.lang.Override()
    public void onSurfaceTextureAvailable(@org.jetbrains.annotations.NotNull()
    android.graphics.SurfaceTexture surface, int width, int height) {
    }
    
    @java.lang.Override()
    public void onSurfaceTextureSizeChanged(@org.jetbrains.annotations.NotNull()
    android.graphics.SurfaceTexture surface, int width, int height) {
    }
    
    @java.lang.Override()
    public boolean onSurfaceTextureDestroyed(@org.jetbrains.annotations.NotNull()
    android.graphics.SurfaceTexture surface) {
        return false;
    }
    
    @java.lang.Override()
    public void onSurfaceTextureUpdated(@org.jetbrains.annotations.NotNull()
    android.graphics.SurfaceTexture surface) {
    }
    
    @kotlin.jvm.JvmOverloads()
    public OrbAnimationView(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        super(null);
    }
    
    /**
     * Owns its own EGL context/surface and a tight draw loop targeting ~60fps.
     * All GL calls happen on this thread only.
     */
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000Z\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0010\t\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\u0007\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u000b\n\u0002\b\r\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0002\b\u000f\b\u0002\u0018\u00002\u00020\u0001B\u001d\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0007J\u0018\u0010/\u001a\u00020\u00052\u0006\u00100\u001a\u00020\u00052\u0006\u00101\u001a\u00020\u000bH\u0002J\b\u00102\u001a\u000203H\u0002J\b\u00104\u001a\u00020\u001fH\u0002J\b\u00105\u001a\u000203H\u0002J\u000e\u00106\u001a\u0002032\u0006\u00107\u001a\u00020\u000eJ\b\u00108\u001a\u000203H\u0002J\u0016\u00109\u001a\u0002032\u0006\u0010:\u001a\u00020\u00052\u0006\u0010;\u001a\u00020\u0005J\b\u0010<\u001a\u000203H\u0016J\u0010\u0010=\u001a\u0002032\u0006\u0010>\u001a\u00020\tH\u0002J\u000e\u0010?\u001a\u0002032\u0006\u0010@\u001a\u00020\u000eJ\u0006\u0010A\u001a\u000203R\u000e\u0010\b\u001a\u00020\tX\u0082D\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u000bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\u000bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001a\u0010\r\u001a\u00020\u000eX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u000f\u0010\u0010\"\u0004\b\u0011\u0010\u0012R\u000e\u0010\u0013\u001a\u00020\u0005X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0014\u001a\u00020\u000eX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0015\u001a\u00020\u0016X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0017\u001a\u00020\u0018X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0019\u001a\u00020\u001aX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u001b\u001a\u00020\u0005X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u001c\u001a\u00020\tX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u001d\u001a\u00020\u0005X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u001a\u0010\u001e\u001a\u00020\u001fX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b \u0010!\"\u0004\b\"\u0010#R\u000e\u0010$\u001a\u00020\u0005X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010%\u001a\u00020\u0005X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010&\u001a\u00020\u0005X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\'\u001a\u00020\u001fX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010(\u001a\u00020\tX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010)\u001a\u00020\u0005X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010*\u001a\u00020\u000eX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010+\u001a\u00020\u0005X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010,\u001a\u0004\u0018\u00010-X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010.\u001a\u00020\u0005X\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006B"}, d2 = {"Lcom/jarvis/assistant/ui/main/OrbAnimationView$OrbRenderThread;", "Ljava/lang/Thread;", "surfaceTexture", "Landroid/graphics/SurfaceTexture;", "initialWidth", "", "initialHeight", "(Landroid/graphics/SurfaceTexture;II)V", "FLASH_DURATION_NS", "", "FRAGMENT_SHADER", "", "VERTEX_SHADER", "amplitude", "", "getAmplitude", "()F", "setAmplitude", "(F)V", "amplitudeHandle", "currentState", "eglContext", "Landroid/opengl/EGLContext;", "eglDisplay", "Landroid/opengl/EGLDisplay;", "eglSurface", "Landroid/opengl/EGLSurface;", "flashHandle", "flashStartNs", "height", "paused", "", "getPaused", "()Z", "setPaused", "(Z)V", "positionHandle", "program", "resolutionHandle", "running", "startTimeNs", "stateHandle", "targetState", "timeHandle", "vertexBuffer", "Ljava/nio/FloatBuffer;", "width", "compileShader", "type", "source", "drawFrame", "", "initEgl", "initGl", "primeState", "initial", "releaseEgl", "resize", "w", "h", "run", "safeSleep", "ms", "setTargetState", "newState", "shutdown", "app_release"})
    static final class OrbRenderThread extends java.lang.Thread {
        @org.jetbrains.annotations.NotNull()
        private final android.graphics.SurfaceTexture surfaceTexture = null;
        @kotlin.jvm.Volatile()
        private volatile float targetState = 0.0F;
        @kotlin.jvm.Volatile()
        private volatile float amplitude = 0.0F;
        @kotlin.jvm.Volatile()
        private volatile boolean paused = false;
        @kotlin.jvm.Volatile()
        private volatile boolean running = true;
        @kotlin.jvm.Volatile()
        private volatile int width;
        @kotlin.jvm.Volatile()
        private volatile int height;
        private float currentState = 0.0F;
        private long flashStartNs = 0L;
        private final long FLASH_DURATION_NS = 450000000L;
        @org.jetbrains.annotations.NotNull()
        private android.opengl.EGLDisplay eglDisplay;
        @org.jetbrains.annotations.NotNull()
        private android.opengl.EGLContext eglContext;
        @org.jetbrains.annotations.NotNull()
        private android.opengl.EGLSurface eglSurface;
        private int program = 0;
        private int positionHandle = 0;
        private int timeHandle = 0;
        private int stateHandle = 0;
        private int flashHandle = 0;
        private int amplitudeHandle = 0;
        private int resolutionHandle = 0;
        @org.jetbrains.annotations.Nullable()
        private java.nio.FloatBuffer vertexBuffer;
        private long startTimeNs = 0L;
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String VERTEX_SHADER = null;
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String FRAGMENT_SHADER = null;
        
        public OrbRenderThread(@org.jetbrains.annotations.NotNull()
        android.graphics.SurfaceTexture surfaceTexture, int initialWidth, int initialHeight) {
            super();
        }
        
        public final float getAmplitude() {
            return 0.0F;
        }
        
        public final void setAmplitude(float p0) {
        }
        
        public final boolean getPaused() {
            return false;
        }
        
        public final void setPaused(boolean p0) {
        }
        
        public final void primeState(float initial) {
        }
        
        public final void setTargetState(float newState) {
        }
        
        public final void resize(int w, int h) {
        }
        
        public final void shutdown() {
        }
        
        @java.lang.Override()
        public void run() {
        }
        
        private final void safeSleep(long ms) {
        }
        
        private final boolean initEgl() {
            return false;
        }
        
        private final void initGl() {
        }
        
        private final void drawFrame() {
        }
        
        private final int compileShader(int type, java.lang.String source) {
            return 0;
        }
        
        private final void releaseEgl() {
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0002\b\u0007\b\u0086\u0081\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00000\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002j\u0002\b\u0003j\u0002\b\u0004j\u0002\b\u0005j\u0002\b\u0006j\u0002\b\u0007\u00a8\u0006\b"}, d2 = {"Lcom/jarvis/assistant/ui/main/OrbAnimationView$OrbState;", "", "(Ljava/lang/String;I)V", "IDLE", "LISTENING", "SPEAKING", "THINKING", "ACTIVE", "app_release"})
    public static enum OrbState {
        /*public static final*/ IDLE /* = new IDLE() */,
        /*public static final*/ LISTENING /* = new LISTENING() */,
        /*public static final*/ SPEAKING /* = new SPEAKING() */,
        /*public static final*/ THINKING /* = new THINKING() */,
        /*public static final*/ ACTIVE /* = new ACTIVE() */;
        
        OrbState() {
        }
        
        @org.jetbrains.annotations.NotNull()
        public static kotlin.enums.EnumEntries<com.jarvis.assistant.ui.main.OrbAnimationView.OrbState> getEntries() {
            return null;
        }
    }
}