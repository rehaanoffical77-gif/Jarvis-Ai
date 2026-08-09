package com.jarvis.assistant.util;

/**
 * Lets JARVIS place phone calls by spoken contact name or raw phone number.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00002\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0006\b\u00c6\u0002\u0018\u00002\u00020\u0001:\u0002\u0013\u0014B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0016\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\bJ\u0010\u0010\t\u001a\u00020\b2\u0006\u0010\n\u001a\u00020\bH\u0002J\u001e\u0010\u000b\u001a\b\u0012\u0004\u0012\u00020\r0\f2\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\bH\u0002J\u0018\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0010\u001a\u00020\bH\u0002J\u0018\u0010\u0011\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0012\u001a\u00020\rH\u0002\u00a8\u0006\u0015"}, d2 = {"Lcom/jarvis/assistant/util/ContactCaller;", "", "()V", "callContact", "Lcom/jarvis/assistant/util/ContactCaller$CallResult;", "context", "Landroid/content/Context;", "spokenName", "", "cleanQueryString", "raw", "findMatches", "", "Lcom/jarvis/assistant/util/ContactCaller$Contact;", "hasPermission", "", "permission", "placeCall", "target", "CallResult", "Contact", "app_debug"})
public final class ContactCaller {
    @org.jetbrains.annotations.NotNull()
    public static final com.jarvis.assistant.util.ContactCaller INSTANCE = null;
    
    private ContactCaller() {
        super();
    }
    
    private final boolean hasPermission(android.content.Context context, java.lang.String permission) {
        return false;
    }
    
    private final java.lang.String cleanQueryString(java.lang.String raw) {
        return null;
    }
    
    /**
     * Looks up contacts stored on the device, matching case-insensitively and flexibly.
     */
    private final java.util.List<com.jarvis.assistant.util.ContactCaller.Contact> findMatches(android.content.Context context, java.lang.String spokenName) {
        return null;
    }
    
    /**
     * Places a call to whichever saved contact best matches [spokenName].
     */
    @org.jetbrains.annotations.NotNull()
    public final com.jarvis.assistant.util.ContactCaller.CallResult callContact(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.NotNull()
    java.lang.String spokenName) {
        return null;
    }
    
    private final com.jarvis.assistant.util.ContactCaller.CallResult placeCall(android.content.Context context, com.jarvis.assistant.util.ContactCaller.Contact target) {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\b6\u0018\u00002\u00020\u0001:\u0005\u0003\u0004\u0005\u0006\u0007B\u0007\b\u0004\u00a2\u0006\u0002\u0010\u0002\u0082\u0001\u0005\b\t\n\u000b\f\u00a8\u0006\r"}, d2 = {"Lcom/jarvis/assistant/util/ContactCaller$CallResult;", "", "()V", "CallFailed", "MissingPermission", "MultipleMatches", "NoMatch", "Success", "Lcom/jarvis/assistant/util/ContactCaller$CallResult$CallFailed;", "Lcom/jarvis/assistant/util/ContactCaller$CallResult$MissingPermission;", "Lcom/jarvis/assistant/util/ContactCaller$CallResult$MultipleMatches;", "Lcom/jarvis/assistant/util/ContactCaller$CallResult$NoMatch;", "Lcom/jarvis/assistant/util/ContactCaller$CallResult$Success;", "app_debug"})
    public static abstract class CallResult {
        
        private CallResult() {
            super();
        }
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002\u00a8\u0006\u0003"}, d2 = {"Lcom/jarvis/assistant/util/ContactCaller$CallResult$CallFailed;", "Lcom/jarvis/assistant/util/ContactCaller$CallResult;", "()V", "app_debug"})
        public static final class CallFailed extends com.jarvis.assistant.util.ContactCaller.CallResult {
            @org.jetbrains.annotations.NotNull()
            public static final com.jarvis.assistant.util.ContactCaller.CallResult.CallFailed INSTANCE = null;
            
            private CallFailed() {
            }
        }
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002\u00a8\u0006\u0003"}, d2 = {"Lcom/jarvis/assistant/util/ContactCaller$CallResult$MissingPermission;", "Lcom/jarvis/assistant/util/ContactCaller$CallResult;", "()V", "app_debug"})
        public static final class MissingPermission extends com.jarvis.assistant.util.ContactCaller.CallResult {
            @org.jetbrains.annotations.NotNull()
            public static final com.jarvis.assistant.util.ContactCaller.CallResult.MissingPermission INSTANCE = null;
            
            private MissingPermission() {
            }
        }
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\t\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001B\u001b\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\f\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005\u00a2\u0006\u0002\u0010\u0007J\t\u0010\f\u001a\u00020\u0003H\u00c6\u0003J\u000f\u0010\r\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005H\u00c6\u0003J#\u0010\u000e\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\u000e\b\u0002\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005H\u00c6\u0001J\u0013\u0010\u000f\u001a\u00020\u00102\b\u0010\u0011\u001a\u0004\u0018\u00010\u0012H\u00d6\u0003J\t\u0010\u0013\u001a\u00020\u0014H\u00d6\u0001J\t\u0010\u0015\u001a\u00020\u0003H\u00d6\u0001R\u0017\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\u0016"}, d2 = {"Lcom/jarvis/assistant/util/ContactCaller$CallResult$MultipleMatches;", "Lcom/jarvis/assistant/util/ContactCaller$CallResult;", "query", "", "matches", "", "Lcom/jarvis/assistant/util/ContactCaller$Contact;", "(Ljava/lang/String;Ljava/util/List;)V", "getMatches", "()Ljava/util/List;", "getQuery", "()Ljava/lang/String;", "component1", "component2", "copy", "equals", "", "other", "", "hashCode", "", "toString", "app_debug"})
        public static final class MultipleMatches extends com.jarvis.assistant.util.ContactCaller.CallResult {
            @org.jetbrains.annotations.NotNull()
            private final java.lang.String query = null;
            @org.jetbrains.annotations.NotNull()
            private final java.util.List<com.jarvis.assistant.util.ContactCaller.Contact> matches = null;
            
            public MultipleMatches(@org.jetbrains.annotations.NotNull()
            java.lang.String query, @org.jetbrains.annotations.NotNull()
            java.util.List<com.jarvis.assistant.util.ContactCaller.Contact> matches) {
            }
            
            @org.jetbrains.annotations.NotNull()
            public final java.lang.String getQuery() {
                return null;
            }
            
            @org.jetbrains.annotations.NotNull()
            public final java.util.List<com.jarvis.assistant.util.ContactCaller.Contact> getMatches() {
                return null;
            }
            
            @org.jetbrains.annotations.NotNull()
            public final java.lang.String component1() {
                return null;
            }
            
            @org.jetbrains.annotations.NotNull()
            public final java.util.List<com.jarvis.assistant.util.ContactCaller.Contact> component2() {
                return null;
            }
            
            @org.jetbrains.annotations.NotNull()
            public final com.jarvis.assistant.util.ContactCaller.CallResult.MultipleMatches copy(@org.jetbrains.annotations.NotNull()
            java.lang.String query, @org.jetbrains.annotations.NotNull()
            java.util.List<com.jarvis.assistant.util.ContactCaller.Contact> matches) {
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
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0006\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\t\u0010\u0007\u001a\u00020\u0003H\u00c6\u0003J\u0013\u0010\b\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u0003H\u00c6\u0001J\u0013\u0010\t\u001a\u00020\n2\b\u0010\u000b\u001a\u0004\u0018\u00010\fH\u00d6\u0003J\t\u0010\r\u001a\u00020\u000eH\u00d6\u0001J\t\u0010\u000f\u001a\u00020\u0003H\u00d6\u0001R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006\u00a8\u0006\u0010"}, d2 = {"Lcom/jarvis/assistant/util/ContactCaller$CallResult$NoMatch;", "Lcom/jarvis/assistant/util/ContactCaller$CallResult;", "query", "", "(Ljava/lang/String;)V", "getQuery", "()Ljava/lang/String;", "component1", "copy", "equals", "", "other", "", "hashCode", "", "toString", "app_debug"})
        public static final class NoMatch extends com.jarvis.assistant.util.ContactCaller.CallResult {
            @org.jetbrains.annotations.NotNull()
            private final java.lang.String query = null;
            
            public NoMatch(@org.jetbrains.annotations.NotNull()
            java.lang.String query) {
            }
            
            @org.jetbrains.annotations.NotNull()
            public final java.lang.String getQuery() {
                return null;
            }
            
            @org.jetbrains.annotations.NotNull()
            public final java.lang.String component1() {
                return null;
            }
            
            @org.jetbrains.annotations.NotNull()
            public final com.jarvis.assistant.util.ContactCaller.CallResult.NoMatch copy(@org.jetbrains.annotations.NotNull()
            java.lang.String query) {
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
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u0086\b\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\t\u0010\u0007\u001a\u00020\u0003H\u00c6\u0003J\u0013\u0010\b\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u0003H\u00c6\u0001J\u0013\u0010\t\u001a\u00020\n2\b\u0010\u000b\u001a\u0004\u0018\u00010\fH\u00d6\u0003J\t\u0010\r\u001a\u00020\u000eH\u00d6\u0001J\t\u0010\u000f\u001a\u00020\u0010H\u00d6\u0001R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006\u00a8\u0006\u0011"}, d2 = {"Lcom/jarvis/assistant/util/ContactCaller$CallResult$Success;", "Lcom/jarvis/assistant/util/ContactCaller$CallResult;", "contact", "Lcom/jarvis/assistant/util/ContactCaller$Contact;", "(Lcom/jarvis/assistant/util/ContactCaller$Contact;)V", "getContact", "()Lcom/jarvis/assistant/util/ContactCaller$Contact;", "component1", "copy", "equals", "", "other", "", "hashCode", "", "toString", "", "app_debug"})
        public static final class Success extends com.jarvis.assistant.util.ContactCaller.CallResult {
            @org.jetbrains.annotations.NotNull()
            private final com.jarvis.assistant.util.ContactCaller.Contact contact = null;
            
            public Success(@org.jetbrains.annotations.NotNull()
            com.jarvis.assistant.util.ContactCaller.Contact contact) {
            }
            
            @org.jetbrains.annotations.NotNull()
            public final com.jarvis.assistant.util.ContactCaller.Contact getContact() {
                return null;
            }
            
            @org.jetbrains.annotations.NotNull()
            public final com.jarvis.assistant.util.ContactCaller.Contact component1() {
                return null;
            }
            
            @org.jetbrains.annotations.NotNull()
            public final com.jarvis.assistant.util.ContactCaller.CallResult.Success copy(@org.jetbrains.annotations.NotNull()
            com.jarvis.assistant.util.ContactCaller.Contact contact) {
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
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0002\b\t\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0005J\t\u0010\t\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\n\u001a\u00020\u0003H\u00c6\u0003J\u001d\u0010\u000b\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u0003H\u00c6\u0001J\u0013\u0010\f\u001a\u00020\r2\b\u0010\u000e\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u000f\u001a\u00020\u0010H\u00d6\u0001J\t\u0010\u0011\u001a\u00020\u0003H\u00d6\u0001R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\u0007\u00a8\u0006\u0012"}, d2 = {"Lcom/jarvis/assistant/util/ContactCaller$Contact;", "", "name", "", "number", "(Ljava/lang/String;Ljava/lang/String;)V", "getName", "()Ljava/lang/String;", "getNumber", "component1", "component2", "copy", "equals", "", "other", "hashCode", "", "toString", "app_debug"})
    public static final class Contact {
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String name = null;
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String number = null;
        
        public Contact(@org.jetbrains.annotations.NotNull()
        java.lang.String name, @org.jetbrains.annotations.NotNull()
        java.lang.String number) {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getName() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getNumber() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component1() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component2() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.jarvis.assistant.util.ContactCaller.Contact copy(@org.jetbrains.annotations.NotNull()
        java.lang.String name, @org.jetbrains.annotations.NotNull()
        java.lang.String number) {
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