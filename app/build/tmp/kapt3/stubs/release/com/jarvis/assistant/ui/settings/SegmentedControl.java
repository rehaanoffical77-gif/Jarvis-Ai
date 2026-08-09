package com.jarvis.assistant.ui.settings;

/**
 * An iOS-style segmented control: a rounded track with a sliding accent "pill"
 * indicator behind whichever option is selected. Used for the Personality picker.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000R\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010!\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0002\b\u0005\n\u0002\u0010\u000b\n\u0002\b\r\u0018\u00002\u00020\u0001B%\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\n\b\u0002\u0010\u0004\u001a\u0004\u0018\u00010\u0005\u0012\b\b\u0002\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\bJ\u0010\u0010\u0017\u001a\u00020\u00072\u0006\u0010\u0018\u001a\u00020\u0007H\u0002J\u0010\u0010\u0019\u001a\u00020\u00122\u0006\u0010\u001a\u001a\u00020\u001bH\u0002J\u001a\u0010\u001c\u001a\u00020\u00122\u0012\u0010\u001d\u001a\u000e\u0012\u0004\u0012\u00020\u0007\u0012\u0004\u0012\u00020\u00120\u0011J(\u0010\u001e\u001a\u00020\u00122\u0006\u0010\u001f\u001a\u00020\u00072\u0006\u0010 \u001a\u00020\u00072\u0006\u0010!\u001a\u00020\u00072\u0006\u0010\"\u001a\u00020\u0007H\u0014J\u0018\u0010#\u001a\u00020\u00122\u0006\u0010$\u001a\u00020\u00072\b\b\u0002\u0010\u001a\u001a\u00020\u001bJ\u001e\u0010%\u001a\u00020\u00122\f\u0010&\u001a\b\u0012\u0004\u0012\u00020\u00150\u00142\b\b\u0002\u0010\'\u001a\u00020\u0007R\u000e\u0010\t\u001a\u00020\nX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u000b\u001a\b\u0012\u0004\u0012\u00020\r0\fX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000e\u001a\u00020\u000fX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001c\u0010\u0010\u001a\u0010\u0012\u0004\u0012\u00020\u0007\u0012\u0004\u0012\u00020\u0012\u0018\u00010\u0011X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u0013\u001a\b\u0012\u0004\u0012\u00020\u00150\u0014X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0016\u001a\u00020\u0007X\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006("}, d2 = {"Lcom/jarvis/assistant/ui/settings/SegmentedControl;", "Landroid/widget/FrameLayout;", "context", "Landroid/content/Context;", "attrs", "Landroid/util/AttributeSet;", "defStyleAttr", "", "(Landroid/content/Context;Landroid/util/AttributeSet;I)V", "indicator", "Landroid/view/View;", "labelViews", "", "Landroid/widget/TextView;", "labelsRow", "Landroid/widget/LinearLayout;", "onSelectionChanged", "Lkotlin/Function1;", "", "options", "", "", "selectedIndex", "dp", "value", "layoutIndicator", "animate", "", "onSelectionChange", "listener", "onSizeChanged", "w", "h", "oldw", "oldh", "select", "index", "setOptions", "newOptions", "initialSelected", "app_release"})
public final class SegmentedControl extends android.widget.FrameLayout {
    @org.jetbrains.annotations.NotNull()
    private final android.view.View indicator = null;
    @org.jetbrains.annotations.NotNull()
    private final android.widget.LinearLayout labelsRow = null;
    @org.jetbrains.annotations.NotNull()
    private java.util.List<java.lang.String> options;
    private int selectedIndex = 0;
    @org.jetbrains.annotations.Nullable()
    private kotlin.jvm.functions.Function1<? super java.lang.Integer, kotlin.Unit> onSelectionChanged;
    @org.jetbrains.annotations.NotNull()
    private final java.util.List<android.widget.TextView> labelViews = null;
    
    @kotlin.jvm.JvmOverloads()
    public SegmentedControl(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.Nullable()
    android.util.AttributeSet attrs, int defStyleAttr) {
        super(null);
    }
    
    public final void setOptions(@org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.String> newOptions, int initialSelected) {
    }
    
    public final void onSelectionChange(@org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function1<? super java.lang.Integer, kotlin.Unit> listener) {
    }
    
    public final void select(int index, boolean animate) {
    }
    
    private final void layoutIndicator(boolean animate) {
    }
    
    @java.lang.Override()
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    }
    
    private final int dp(int value) {
        return 0;
    }
    
    @kotlin.jvm.JvmOverloads()
    public SegmentedControl(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        super(null);
    }
    
    @kotlin.jvm.JvmOverloads()
    public SegmentedControl(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.Nullable()
    android.util.AttributeSet attrs) {
        super(null);
    }
}