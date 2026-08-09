package com.jarvis.assistant.util;

/**
 * Lets JARVIS place calls on a specific SIM (SIM 1 or SIM 2) on dual-SIM phones without
 * Android popping up its own "Call with SIM 1 / SIM 2" chooser every time.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000J\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0004\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0005\b\u00c6\u0002\u0018\u00002\u00020\u0001:\u0001\u001eB\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0014\u0010\b\u001a\b\u0012\u0004\u0012\u00020\n0\t2\u0006\u0010\u000b\u001a\u00020\fJ\u0010\u0010\r\u001a\u0004\u0018\u00010\u000e2\u0006\u0010\u000b\u001a\u00020\fJ\u000e\u0010\u000f\u001a\u00020\u00102\u0006\u0010\u000b\u001a\u00020\fJ\u000e\u0010\u0011\u001a\u00020\u00042\u0006\u0010\u000b\u001a\u00020\fJ\u000e\u0010\u0012\u001a\u00020\u00102\u0006\u0010\u000b\u001a\u00020\fJ\u0010\u0010\u0013\u001a\u00020\u00142\u0006\u0010\u000b\u001a\u00020\fH\u0002J\u000e\u0010\u0015\u001a\u00020\u00142\u0006\u0010\u000b\u001a\u00020\fJ\u0018\u0010\u0016\u001a\n \u0018*\u0004\u0018\u00010\u00170\u00172\u0006\u0010\u000b\u001a\u00020\fH\u0002J(\u0010\u0019\u001a\u00020\u001a2\u0006\u0010\u000b\u001a\u00020\f2\b\u0010\u001b\u001a\u0004\u0018\u00010\u000e2\u0006\u0010\u001c\u001a\u00020\u00102\u0006\u0010\u001d\u001a\u00020\u0010R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u001f"}, d2 = {"Lcom/jarvis/assistant/util/SimManager;", "", "()V", "PREF_SIM_COMPONENT", "", "PREF_SIM_ID", "PREF_SIM_INDEX", "PREF_SIM_SUB_ID", "getCallCapableSims", "", "Lcom/jarvis/assistant/util/SimManager$SimOption;", "context", "Landroid/content/Context;", "getPreferredSim", "Landroid/telecom/PhoneAccountHandle;", "getPreferredSimIndex", "", "getPreferredSimLabel", "getPreferredSimSubId", "hasPermission", "", "isDualSim", "prefs", "Landroid/content/SharedPreferences;", "kotlin.jvm.PlatformType", "savePreferredSim", "", "handle", "slotIndex", "subId", "SimOption", "app_release"})
public final class SimManager {
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String PREF_SIM_ID = "preferred_sim_id";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String PREF_SIM_COMPONENT = "preferred_sim_component";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String PREF_SIM_INDEX = "preferred_sim_index";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String PREF_SIM_SUB_ID = "preferred_sim_sub_id";
    @org.jetbrains.annotations.NotNull()
    public static final com.jarvis.assistant.util.SimManager INSTANCE = null;
    
    private SimManager() {
        super();
    }
    
    private final boolean hasPermission(android.content.Context context) {
        return false;
    }
    
    /**
     * Every SIM currently capable of placing a call, mapped to exact slot index and subId.
     */
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<com.jarvis.assistant.util.SimManager.SimOption> getCallCapableSims(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        return null;
    }
    
    /**
     * True if the phone actually has more than one usable SIM.
     */
    public final boolean isDualSim(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        return false;
    }
    
    public final void savePreferredSim(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.Nullable()
    android.telecom.PhoneAccountHandle handle, int slotIndex, int subId) {
    }
    
    public final int getPreferredSimIndex(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        return 0;
    }
    
    public final int getPreferredSimSubId(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        return 0;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getPreferredSimLabel(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final android.telecom.PhoneAccountHandle getPreferredSim(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        return null;
    }
    
    private final android.content.SharedPreferences prefs(android.content.Context context) {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\b\n\u0002\b\u000f\n\u0002\u0010\u000b\n\u0002\b\u0004\b\u0086\b\u0018\u00002\u00020\u0001B\'\u0012\b\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\tJ\u000b\u0010\u0011\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\t\u0010\u0012\u001a\u00020\u0005H\u00c6\u0003J\t\u0010\u0013\u001a\u00020\u0007H\u00c6\u0003J\t\u0010\u0014\u001a\u00020\u0007H\u00c6\u0003J3\u0010\u0015\u001a\u00020\u00002\n\b\u0002\u0010\u0002\u001a\u0004\u0018\u00010\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u00072\b\b\u0002\u0010\b\u001a\u00020\u0007H\u00c6\u0001J\u0013\u0010\u0016\u001a\u00020\u00172\b\u0010\u0018\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0019\u001a\u00020\u0007H\u00d6\u0001J\t\u0010\u001a\u001a\u00020\u0005H\u00d6\u0001R\u0013\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000bR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\rR\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u000fR\u0011\u0010\b\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0010\u0010\u000f\u00a8\u0006\u001b"}, d2 = {"Lcom/jarvis/assistant/util/SimManager$SimOption;", "", "handle", "Landroid/telecom/PhoneAccountHandle;", "label", "", "slotIndex", "", "subId", "(Landroid/telecom/PhoneAccountHandle;Ljava/lang/String;II)V", "getHandle", "()Landroid/telecom/PhoneAccountHandle;", "getLabel", "()Ljava/lang/String;", "getSlotIndex", "()I", "getSubId", "component1", "component2", "component3", "component4", "copy", "equals", "", "other", "hashCode", "toString", "app_release"})
    public static final class SimOption {
        @org.jetbrains.annotations.Nullable()
        private final android.telecom.PhoneAccountHandle handle = null;
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String label = null;
        private final int slotIndex = 0;
        private final int subId = 0;
        
        public SimOption(@org.jetbrains.annotations.Nullable()
        android.telecom.PhoneAccountHandle handle, @org.jetbrains.annotations.NotNull()
        java.lang.String label, int slotIndex, int subId) {
            super();
        }
        
        @org.jetbrains.annotations.Nullable()
        public final android.telecom.PhoneAccountHandle getHandle() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getLabel() {
            return null;
        }
        
        public final int getSlotIndex() {
            return 0;
        }
        
        public final int getSubId() {
            return 0;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final android.telecom.PhoneAccountHandle component1() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component2() {
            return null;
        }
        
        public final int component3() {
            return 0;
        }
        
        public final int component4() {
            return 0;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.jarvis.assistant.util.SimManager.SimOption copy(@org.jetbrains.annotations.Nullable()
        android.telecom.PhoneAccountHandle handle, @org.jetbrains.annotations.NotNull()
        java.lang.String label, int slotIndex, int subId) {
            return null;
        }
        
        @java.lang.Override()
        public boolean equals(@org.jetbrains.annotations.Nullable()
        java.lang.Object other) {
            return false;
        }
        
        @java.lang.Override()
        public int hashCode() {
            return 0;
        }
        
        @java.lang.Override()
        @org.jetbrains.annotations.NotNull()
        public java.lang.String toString() {
            return null;
        }
    }
}