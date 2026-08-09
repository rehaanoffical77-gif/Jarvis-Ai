package com.jarvis.assistant.ai
import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.sqrt

/**
 * Handles mic capture (AudioRecord, 16kHz mono PCM16) and
 * speaker playback (AudioTrack, 24kHz mono PCM16) — mirrors the
 * Python `sounddevice` reference pipeline.
 */
class AudioEngine(private val context: Context) {

    companion object {
        private const val TAG = "AudioEngine"
        const val MIC_SAMPLE_RATE = 16000
        const val SPEAKER_SAMPLE_RATE = 24000
        const val CHUNK_SIZE = 640

        // Low latency audio buffer: ~80ms of audio before starting playback.
        // Enough to prevent underruns while keeping latency minimal.
        private const val PREBUFFER_BYTES = 3840

        // Debounce before marking speaking as stopped — prevents false gaps
        // Debounce before marking speaking as stopped — prevents false gaps
        // between sentence chunks that cause the orb to flicker.
        private const val SPEAK_STOP_DEBOUNCE_MS = 150L

        // RMS threshold & consecutive frames for barge-in / smooth voice interruption
        // Set to 0.25f (deliberate, loud user speech) to prevent speaker audio from triggering self-interruption
        private const val BARGE_IN_RMS_THRESHOLD = 0.25f
        private const val BARGE_IN_CONSECUTIVE_CHUNKS = 5
        private const val MAX_QUEUE_CHUNKS = 25
    }

    var onAudioChunkCaptured: ((ByteArray) -> Unit)? = null
    var onAmplitudeChanged: ((Float) -> Unit)? = null
    var onSpeakingStarted: (() -> Unit)? = null
    var onSpeakingStopped: (() -> Unit)? = null
    var onInterruptTriggered: (() -> Unit)? = null

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var isRecording = false
    private var isMuted = false
    @Volatile private var isSpeaking = false
    @Volatile private var isExternalSpeaking = false

    private val playbackQueue = ConcurrentLinkedQueue<ByteArray>()
    private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var recordJob: Job? = null
    private var playbackJob: Job? = null

    /** True while JARVIS's audio is playing — used to suppress mic echo. */
    fun isCurrentlySpeaking() = isSpeaking || isExternalSpeaking

    fun setExternalSpeaking(speaking: Boolean) {
        isExternalSpeaking = speaking
    }

    @SuppressLint("MissingPermission")
    fun startRecording() {
        if (isRecording) return

        val minBufSize = AudioRecord.getMinBufferSize(
            MIC_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBufSize, CHUNK_SIZE * 4)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MIC_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            return
        }

        val sessionId = audioRecord?.audioSessionId ?: 0
        if (sessionId != 0) {
            if (AcousticEchoCanceler.isAvailable()) {
                try {
                    aec = AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to enable AcousticEchoCanceler: ${e.message}")
                }
            }
            if (NoiseSuppressor.isAvailable()) {
                try {
                    ns = NoiseSuppressor.create(sessionId)?.apply { enabled = true }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to enable NoiseSuppressor: ${e.message}")
                }
            }
        }

        audioRecord?.startRecording()
        isRecording = true

        recordJob = engineScope.launch {
            val buffer = ByteArray(CHUNK_SIZE)
            var consecutiveSpeechChunks = 0
            while (isActive && isRecording) {
                val read = audioRecord?.read(buffer, 0, CHUNK_SIZE) ?: -1
                if (read > 0) {
                    val chunk = buffer.copyOf(read)
                    if (!isMuted) {
                        val rms = calculateRms(chunk)

                        // Smooth Voice Interruption / Barge-In Detection
                        if (isSpeaking || isExternalSpeaking) {
                            if (rms >= BARGE_IN_RMS_THRESHOLD) {
                                consecutiveSpeechChunks++
                                if (consecutiveSpeechChunks >= BARGE_IN_CONSECUTIVE_CHUNKS) {
                                    consecutiveSpeechChunks = 0
                                    Log.d(TAG, "Voice interruption detected (RMS: $rms)")
                                    withContext(Dispatchers.Main) {
                                        onInterruptTriggered?.invoke()
                                    }
                                }
                            } else {
                                consecutiveSpeechChunks = 0
                            }
                        } else {
                            consecutiveSpeechChunks = 0
                        }

                        // Always stream mic audio to Gemini Live unless muted
                        onAudioChunkCaptured?.invoke(chunk)

                        if (!isSpeaking && !isExternalSpeaking) {
                            onAmplitudeChanged?.invoke(rms)
                        }
                    }
                } else {
                    delay(5)
                }
            }
        }
    }

    fun stopRecording() {
        isRecording = false
        recordJob?.cancel()
        try {
            aec?.release()
            ns?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing audiofx: ${e.message}")
        }
        aec = null
        ns = null

        audioRecord?.let {
            try {
                it.stop()
                it.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping AudioRecord: ${e.message}")
            }
        }
        audioRecord = null
    }

    fun startPlayback() {
        val minBufSize = AudioTrack.getMinBufferSize(
            SPEAKER_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setFlags(AudioAttributes.FLAG_LOW_LATENCY)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(SPEAKER_SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()

        val trackBufferBytes = maxOf(minBufSize, PREBUFFER_BYTES * 2)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(trackBufferBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        audioTrack?.play()

        playbackJob = engineScope.launch {
            var priming = true
            var silenceSinceMs = 0L

            while (isActive) {
                if (priming) {
                    if (playbackQueue.isNotEmpty()) {
                        priming = false
                        silenceSinceMs = 0L
                    } else {
                        if (isSpeaking) {
                            // Debounce: only fire onSpeakingStopped after sustained silence
                            if (silenceSinceMs == 0L) {
                                silenceSinceMs = System.currentTimeMillis()
                            }
                            val elapsed = System.currentTimeMillis() - silenceSinceMs
                            if (elapsed >= SPEAK_STOP_DEBOUNCE_MS && playbackQueue.isEmpty()) {
                                isSpeaking = false
                                silenceSinceMs = 0L
                                withContext(Dispatchers.Main) { onSpeakingStopped?.invoke() }
                            } else {
                                delay(10)
                            }
                        } else {
                            delay(5)
                        }
                        continue
                    }
                }

                val chunk = playbackQueue.poll()
                if (chunk != null) {
                    silenceSinceMs = 0L
                    if (!isSpeaking) {
                        isSpeaking = true
                        withContext(Dispatchers.Main) { onSpeakingStarted?.invoke() }
                    }
                    audioTrack?.write(chunk, 0, chunk.size)
                    onAmplitudeChanged?.invoke(calculateRms(chunk))
                } else {
                    priming = true
                    if (isSpeaking) {
                        // Start debounce timer instead of immediately stopping
                        if (silenceSinceMs == 0L) {
                            silenceSinceMs = System.currentTimeMillis()
                        }
                        val elapsed = System.currentTimeMillis() - silenceSinceMs
                        if (elapsed >= SPEAK_STOP_DEBOUNCE_MS && playbackQueue.isEmpty()) {
                            isSpeaking = false
                            silenceSinceMs = 0L
                            withContext(Dispatchers.Main) { onSpeakingStopped?.invoke() }
                        } else {
                            delay(10)
                        }
                    }
                }
            }
        }
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        playbackQueue.clear()
        audioTrack?.let {
            try {
                it.stop()
                it.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping AudioTrack: ${e.message}")
            }
        }
        audioTrack = null
        isSpeaking = false
    }

    /** Queue a chunk of 24kHz PCM16 audio received from Gemini for playback. */
    fun queueAudio(pcmBytes: ByteArray) {
        while (playbackQueue.size >= MAX_QUEUE_CHUNKS) {
            playbackQueue.poll() // drop oldest chunk if buffer is bloated to prevent lag & voice freeze
        }
        playbackQueue.offer(pcmBytes)
    }

    /** Clear pending playback and reset speaking state (used on interrupt / long-press). */
    fun clearPlaybackQueue() {
        playbackQueue.clear()
        try {
            if (audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                audioTrack?.pause()
                audioTrack?.flush()
                audioTrack?.play()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error flushing AudioTrack: ${e.message}")
        }
        isSpeaking = false
        // Thread-safe: dispatch to main thread since callers may be on any thread
        engineScope.launch(Dispatchers.Main) {
            onSpeakingStopped?.invoke()
        }
    }

    fun setMuted(muted: Boolean) {
        isMuted = muted
    }

    fun isMuted(): Boolean = isMuted

    fun release() {
        stopRecording()
        stopPlayback()
        engineScope.cancel()
    }

    private fun calculateRms(chunk: ByteArray): Float {
        if (chunk.isEmpty()) return 0f
        var sum = 0.0
        var i = 0
        while (i < chunk.size - 1) {
            val sample = ((chunk[i + 1].toInt() shl 8) or (chunk[i].toInt() and 0xFF)).toShort()
            sum += sample * sample
            i += 2
        }
        val samples = chunk.size / 2
        if (samples == 0) return 0f
        val rms = sqrt(sum / samples)
        return (rms / 32768.0).toFloat().coerceIn(0f, 1f)
    }
}
