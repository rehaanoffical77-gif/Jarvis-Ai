package com.jarvis.assistant.util;

/**
 * Small collection of iOS-feeling interaction animations: press-down/spring-back
 * scale feedback, staggered list-entrance animation, and a reusable spring builder.
 * These are intentionally lightweight (no external animation libraries) so they
 * drop cleanly into any View.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000:\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\t\n\u0000\n\u0002\u0010\u0007\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\t\n\u0002\u0018\u0002\n\u0000\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u001a\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\n2\b\b\u0002\u0010\u000b\u001a\u00020\u0006H\u0007J\u0010\u0010\f\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\nH\u0007J\"\u0010\r\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\u000e2\u0006\u0010\u000f\u001a\u00020\u00102\b\b\u0002\u0010\u0011\u001a\u00020\u0004H\u0007J\u001a\u0010\u0012\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\n2\b\b\u0002\u0010\u0013\u001a\u00020\u0004H\u0007J\u0010\u0010\u0014\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\nH\u0007J\u0018\u0010\u0015\u001a\u00020\b2\u0006\u0010\u0016\u001a\u00020\n2\u0006\u0010\u0017\u001a\u00020\u0006H\u0007J\u0010\u0010\u0018\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\nH\u0002J\u0010\u0010\u0019\u001a\u00020\u001a2\u0006\u0010\t\u001a\u00020\nH\u0007R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u001b"}, d2 = {"Lcom/jarvis/assistant/util/AnimUtils;", "", "()V", "PRESS_DOWN_DURATION", "", "PRESS_SCALE", "", "attachPressFeedback", "", "view", "Landroid/view/View;", "pressScale", "bouncePulse", "crossFadeText", "Landroid/widget/TextView;", "newText", "", "duration", "entranceAnimate", "delayMs", "lightUpPulse", "slideIndicator", "indicator", "targetX", "springBack", "startBorderBreathing", "Landroid/animation/ValueAnimator;", "app_debug"})
public final class AnimUtils {
    private static final float PRESS_SCALE = 0.94F;
    private static final long PRESS_DOWN_DURATION = 110L;
    @org.jetbrains.annotations.NotNull()
    public static final com.jarvis.assistant.util.AnimUtils INSTANCE = null;
    
    private AnimUtils() {
        super();
    }
    
    /**
     * Applies iOS-style "press and spring back" visual feedback to any view,
     * without interfering with its existing click / long-click listeners.
     */
    @kotlin.jvm.JvmStatic()
    public static final void attachPressFeedback(@org.jetbrains.annotations.NotNull()
    android.view.View view, float pressScale) {
    }
    
    private final void springBack(android.view.View view) {
    }
    
    /**
     * Brief "light up" cue for state changes (Idle → Listen → Think → Speak):
     * a quick brighten-and-settle pulse, distinct from [bouncePulse]'s tap
     * feedback in that it reads as "something changed" rather than "you
     * pressed something".
     */
    @kotlin.jvm.JvmStatic()
    public static final void lightUpPulse(@org.jetbrains.annotations.NotNull()
    android.view.View view) {
    }
    
    /**
     * Slow, continuous alpha "breathing" loop for the mic ring, matching the
     * reference's `pulse-border-anim` (2.5s ease-in-out, infinite). Call once;
     * returns the animator so the caller can cancel it (e.g. in onDestroy) —
     * previously this ran forever with no owner, leaking the view/Activity.
     */
    @kotlin.jvm.JvmStatic()
    @org.jetbrains.annotations.NotNull()
    public static final android.animation.ValueAnimator startBorderBreathing(@org.jetbrains.annotations.NotNull()
    android.view.View view) {
        return null;
    }
    
    /**
     * One-shot bounce (down then up) for views where you just want a "tap" pulse
     * triggered programmatically (e.g. after a state change) rather than on touch.
     */
    @kotlin.jvm.JvmStatic()
    public static final void bouncePulse(@org.jetbrains.annotations.NotNull()
    android.view.View view) {
    }
    
    /**
     * Staggered fade + rise entrance, used for chat bubbles and list rows so
     * new content settles in like iMessage rather than popping into place.
     */
    @kotlin.jvm.JvmStatic()
    public static final void entranceAnimate(@org.jetbrains.annotations.NotNull()
    android.view.View view, long delayMs) {
    }
    
    /**
     * Animates a horizontal "pill" indicator sliding to a new x position — used by
     * the segmented personality control in Settings.
     */
    @kotlin.jvm.JvmStatic()
    public static final void slideIndicator(@org.jetbrains.annotations.NotNull()
    android.view.View indicator, float targetX) {
    }
    
    /**
     * Cross-fades a view's alpha smoothly, e.g. for status text swaps.
     */
    @kotlin.jvm.JvmStatic()
    public static final void crossFadeText(@org.jetbrains.annotations.NotNull()
    android.widget.TextView view, @org.jetbrains.annotations.NotNull()
    java.lang.String newText, long duration) {
    }
}