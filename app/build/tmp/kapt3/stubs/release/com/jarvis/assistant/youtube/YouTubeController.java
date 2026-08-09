package com.jarvis.assistant.youtube;

/**
 * Everything JARVIS needs to actually control YouTube:
 * - search + jump straight to a video (deep link, skips typing in-app)
 * - play/pause/skip via standard Android media key events (works because
 *   YouTube registers a MediaSession while a video is playing, the same
 *   mechanism headset buttons use)
 * - volume control
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000(\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0005\b\u00c6\u0002\u0018\u00002\u00020\u0001:\u0001\u0010B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0016\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\bJ\u0016\u0010\t\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\n\u001a\u00020\bJ\u0016\u0010\u000b\u001a\u00020\f2\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\r\u001a\u00020\bJ\u0016\u0010\u000e\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u000f\u001a\u00020\b\u00a8\u0006\u0011"}, d2 = {"Lcom/jarvis/assistant/youtube/YouTubeController;", "", "()V", "adjustVolume", "", "context", "Landroid/content/Context;", "direction", "", "playVideoId", "videoId", "searchAndPlay", "Lcom/jarvis/assistant/youtube/YouTubeController$PlayResult;", "query", "sendMediaKey", "action", "PlayResult", "app_release"})
public final class YouTubeController {
    @org.jetbrains.annotations.NotNull()
    public static final com.jarvis.assistant.youtube.YouTubeController INSTANCE = null;
    
    private YouTubeController() {
        super();
    }
    
    /**
     * Searches YouTube for [query] and opens the top result directly in the
     * player (via the `vnd.youtube:` deep link, which YouTube's app handles
     * natively). Falls back to opening YouTube's search results screen if no
     * API key is configured or the search fails — still one voice command
     * away from playing, just not fully automatic.
     */
    @org.jetbrains.annotations.NotNull()
    public final com.jarvis.assistant.youtube.YouTubeController.PlayResult searchAndPlay(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.NotNull()
    java.lang.String query) {
        return null;
    }
    
    /**
     * Opens a specific video ID directly in the YouTube app's player.
     */
    public final boolean playVideoId(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.NotNull()
    java.lang.String videoId) {
        return false;
    }
    
    /**
     * Sends a standard media key event (play/pause/next/previous/stop).
     * Controls whatever app currently holds the active media session —
     * in practice this is YouTube whenever a video is actively playing.
     */
    public final boolean sendMediaKey(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.NotNull()
    java.lang.String action) {
        return false;
    }
    
    /**
     * direction: "up" or "down". Adjusts the media/music volume stream.
     */
    public final boolean adjustVolume(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.NotNull()
    java.lang.String direction) {
        return false;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u000e\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001B!\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\b\u0010\u0004\u001a\u0004\u0018\u00010\u0005\u0012\b\u0010\u0006\u001a\u0004\u0018\u00010\u0005\u00a2\u0006\u0002\u0010\u0007J\t\u0010\r\u001a\u00020\u0003H\u00c6\u0003J\u000b\u0010\u000e\u001a\u0004\u0018\u00010\u0005H\u00c6\u0003J\u000b\u0010\u000f\u001a\u0004\u0018\u00010\u0005H\u00c6\u0003J+\u0010\u0010\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\n\b\u0002\u0010\u0004\u001a\u0004\u0018\u00010\u00052\n\b\u0002\u0010\u0006\u001a\u0004\u0018\u00010\u0005H\u00c6\u0001J\u0013\u0010\u0011\u001a\u00020\u00032\b\u0010\u0012\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0013\u001a\u00020\u0014H\u00d6\u0001J\t\u0010\u0015\u001a\u00020\u0005H\u00d6\u0001R\u0013\u0010\u0006\u001a\u0004\u0018\u00010\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000bR\u0013\u0010\u0004\u001a\u0004\u0018\u00010\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\t\u00a8\u0006\u0016"}, d2 = {"Lcom/jarvis/assistant/youtube/YouTubeController$PlayResult;", "", "success", "", "title", "", "message", "(ZLjava/lang/String;Ljava/lang/String;)V", "getMessage", "()Ljava/lang/String;", "getSuccess", "()Z", "getTitle", "component1", "component2", "component3", "copy", "equals", "other", "hashCode", "", "toString", "app_release"})
    public static final class PlayResult {
        private final boolean success = false;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.String title = null;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.String message = null;
        
        public PlayResult(boolean success, @org.jetbrains.annotations.Nullable()
        java.lang.String title, @org.jetbrains.annotations.Nullable()
        java.lang.String message) {
            super();
        }
        
        public final boolean getSuccess() {
            return false;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String getTitle() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String getMessage() {
            return null;
        }
        
        public final boolean component1() {
            return false;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String component2() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String component3() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.jarvis.assistant.youtube.YouTubeController.PlayResult copy(boolean success, @org.jetbrains.annotations.Nullable()
        java.lang.String title, @org.jetbrains.annotations.Nullable()
        java.lang.String message) {
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