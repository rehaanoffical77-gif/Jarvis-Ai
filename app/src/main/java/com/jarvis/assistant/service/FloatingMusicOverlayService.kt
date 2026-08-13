package com.jarvis.assistant.service

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.jarvis.assistant.R
import com.jarvis.assistant.ui.main.MainActivity
import com.jarvis.assistant.util.JarvisSongsManager
import java.io.File
import java.util.Locale
import kotlin.math.abs

/**
 * System Overlay Service that renders a draggable, floating Music HUD Player
 * on screen with Play/Pause, Previous, Next, Song Title, and Timing SeekBar.
 */
class FloatingMusicOverlayService : Service() {

    companion object {
        private const val TAG = "FloatingMusicOverlay"
        private const val NOTIFICATION_ID = 205
        private const val CHANNEL_ID = "jarvis_floating_music_channel"

        const val ACTION_PLAY_FILE = "com.jarvis.assistant.action.PLAY_FILE"
        const val ACTION_TOGGLE_PLAY_PAUSE = "com.jarvis.assistant.action.TOGGLE_PLAY_PAUSE"
        const val ACTION_NEXT_TRACK = "com.jarvis.assistant.action.NEXT_TRACK"
        const val ACTION_PREV_TRACK = "com.jarvis.assistant.action.PREV_TRACK"
        const val ACTION_STOP = "com.jarvis.assistant.action.STOP_FLOATING_MUSIC"

        const val EXTRA_FILE_PATH = "extra_file_path"
        const val EXTRA_SONG_TITLE = "extra_song_title"

        fun playSong(context: Context, filePath: String, title: String) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                Log.w(TAG, "Cannot start FloatingMusicOverlayService: SYSTEM_ALERT_WINDOW missing")
            }
            val intent = Intent(context, FloatingMusicOverlayService::class.java).apply {
                action = ACTION_PLAY_FILE
                putExtra(EXTRA_FILE_PATH, filePath)
                putExtra(EXTRA_SONG_TITLE, title)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun togglePlayPause(context: Context) {
            val intent = Intent(context, FloatingMusicOverlayService::class.java).apply {
                action = ACTION_TOGGLE_PLAY_PAUSE
            }
            context.startService(intent)
        }

        fun nextTrack(context: Context) {
            val intent = Intent(context, FloatingMusicOverlayService::class.java).apply {
                action = ACTION_NEXT_TRACK
            }
            context.startService(intent)
        }

        fun previousTrack(context: Context) {
            val intent = Intent(context, FloatingMusicOverlayService::class.java).apply {
                action = ACTION_PREV_TRACK
            }
            context.startService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, FloatingMusicOverlayService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var tvSongTitle: TextView? = null
    private var tvCurrentTime: TextView? = null
    private var tvTotalTime: TextView? = null
    private var sbSongProgress: SeekBar? = null
    private var btnPrevious: TextView? = null
    private var btnPlayPause: TextView? = null
    private var btnNext: TextView? = null
    private var btnCloseOverlay: TextView? = null

    private var mediaPlayer: MediaPlayer? = null
    private var currentFilePath: String? = null
    private var currentSongTitle: String = "JARVIS Music Player"
    private var isSeeking = false

    private val progressHandler = Handler(Looper.getMainLooper())
    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            updatePlaybackProgressUI()
            progressHandler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        setupOverlayWindow()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_STICKY

        when (action) {
            ACTION_PLAY_FILE -> {
                val filePath = intent.getStringExtra(EXTRA_FILE_PATH) ?: ""
                val title = intent.getStringExtra(EXTRA_SONG_TITLE) ?: "Music"
                if (filePath.isNotBlank()) {
                    startPlaybackForFile(filePath, title)
                }
            }
            ACTION_TOGGLE_PLAY_PAUSE -> handleTogglePlayPause()
            ACTION_NEXT_TRACK -> playNextTrackInPlaylist()
            ACTION_PREV_TRACK -> playPreviousTrackInPlaylist()
            ACTION_STOP -> stopSelf()
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "JARVIS Background Music Player",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background music player and floating HUD"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("JARVIS Music Playing")
            .setContentText(currentSongTitle)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun setupOverlayWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.e(TAG, "Missing SYSTEM_ALERT_WINDOW permission for music overlay")
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager?.defaultDisplay?.getMetrics(metrics)

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val density = resources.displayMetrics.density
        val widthPx = (290 * density).toInt()

        layoutParams = WindowManager.LayoutParams(
            widthPx,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (metrics.widthPixels - widthPx) / 2
            y = (metrics.heightPixels / 4)
        }

        val themedContext = android.view.ContextThemeWrapper(this, R.style.Theme_Jarvis)
        val inflater = LayoutInflater.from(themedContext)
        overlayView = inflater.inflate(R.layout.layout_floating_music_overlay, null)

        tvSongTitle = overlayView?.findViewById(R.id.tvSongTitle)
        tvCurrentTime = overlayView?.findViewById(R.id.tvCurrentTime)
        tvTotalTime = overlayView?.findViewById(R.id.tvTotalTime)
        sbSongProgress = overlayView?.findViewById(R.id.sbSongProgress)
        btnPrevious = overlayView?.findViewById(R.id.btnPrevious)
        btnPlayPause = overlayView?.findViewById(R.id.btnPlayPause)
        btnNext = overlayView?.findViewById(R.id.btnNext)
        btnCloseOverlay = overlayView?.findViewById(R.id.btnCloseOverlay)

        setupListeners()
        setupTouchDrag(metrics.widthPixels)

        try {
            windowManager?.addView(overlayView, layoutParams)
            Log.d(TAG, "Floating Music Overlay added to WindowManager.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add music overlay view: ${e.message}", e)
        }
    }

    private fun setupListeners() {
        btnPlayPause?.setOnClickListener { handleTogglePlayPause() }
        btnNext?.setOnClickListener { playNextTrackInPlaylist() }
        btnPrevious?.setOnClickListener { playPreviousTrackInPlaylist() }
        btnCloseOverlay?.setOnClickListener { stopSelf() }

        sbSongProgress?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = mediaPlayer?.duration ?: 0
                    if (duration > 0) {
                        val seekPos = ((progress / 100f) * duration).toInt()
                        tvCurrentTime?.text = formatMs(seekPos)
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val progress = seekBar?.progress ?: 0
                val duration = mediaPlayer?.duration ?: 0
                if (duration > 0) {
                    val targetMs = ((progress / 100f) * duration).toInt()
                    mediaPlayer?.seekTo(targetMs)
                }
                isSeeking = false
            }
        })
    }

    private fun setupTouchDrag(screenWidth: Int) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        overlayView?.setOnTouchListener { _, event ->
            val params = layoutParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(overlayView, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun startPlaybackForFile(filePath: String, title: String) {
        currentFilePath = filePath
        currentSongTitle = title
        tvSongTitle?.text = title

        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null

            val file = File(filePath)
            if (!file.exists()) {
                Log.e(TAG, "Audio file does not exist at $filePath")
                return
            }

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(filePath)
                prepare()
                start()
                setOnCompletionListener {
                    btnPlayPause?.text = "▶"
                    playNextTrackInPlaylist()
                }
            }

            btnPlayPause?.text = "⏸"
            progressHandler.removeCallbacks(updateProgressRunnable)
            progressHandler.post(updateProgressRunnable)

        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio file $filePath", e)
        }
    }

    private fun handleTogglePlayPause() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            player.pause()
            btnPlayPause?.text = "▶"
        } else {
            player.start()
            btnPlayPause?.text = "⏸"
        }
    }

    private fun playNextTrackInPlaylist() {
        val tracks = JarvisSongsManager.getPlaylistTracks()
        if (tracks.isEmpty()) return

        val currentIndex = tracks.indexOfFirst { it.file.absolutePath == currentFilePath }
        val nextIndex = if (currentIndex in 0 until tracks.size - 1) currentIndex + 1 else 0
        val nextTrack = tracks[nextIndex]

        startPlaybackForFile(nextTrack.file.absolutePath, nextTrack.title)
    }

    private fun playPreviousTrackInPlaylist() {
        val tracks = JarvisSongsManager.getPlaylistTracks()
        if (tracks.isEmpty()) return

        val currentIndex = tracks.indexOfFirst { it.file.absolutePath == currentFilePath }
        val prevIndex = if (currentIndex > 0) currentIndex - 1 else tracks.size - 1
        val prevTrack = tracks[prevIndex]

        startPlaybackForFile(prevTrack.file.absolutePath, prevTrack.title)
    }

    private fun updatePlaybackProgressUI() {
        if (isSeeking) return
        val player = mediaPlayer ?: return
        try {
            if (player.isPlaying) {
                val curMs = player.currentPosition
                val durMs = player.duration
                if (durMs > 0) {
                    val progressPercent = ((curMs.toFloat() / durMs.toFloat()) * 100).toInt()
                    sbSongProgress?.progress = progressPercent
                    tvCurrentTime?.text = formatMs(curMs)
                    tvTotalTime?.text = formatMs(durMs)
                }
            }
        } catch (_: Exception) {}
    }

    private fun formatMs(ms: Int): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        progressHandler.removeCallbacks(updateProgressRunnable)
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null

        if (overlayView != null && windowManager != null) {
            try { windowManager?.removeView(overlayView) } catch (_: Exception) {}
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
