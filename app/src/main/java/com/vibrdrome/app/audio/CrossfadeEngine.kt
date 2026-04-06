package com.vibrdrome.app.audio

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Crossfade curve types for volume blending between two players.
 */
enum class CrossfadeCurve {
    /** Linear ramp — simple but can sound like a volume dip in the middle. */
    LINEAR,
    /** Equal power (sine/cosine) — maintains perceived loudness throughout the blend. */
    EQUAL_POWER,
    /** S-curve — slow start, fast middle, slow end. Smooth and musical. */
    S_CURVE,
}

/**
 * Manages true dual-player crossfade using a secondary ExoPlayer instance.
 *
 * Architecture:
 * - The primary [ExoPlayer] (owned by [PlaybackManager]) holds the full queue and handles
 *   normal playback, gapless transitions, and all queue operations.
 * - This engine creates a secondary "overlay" [ExoPlayer] that loads the NEXT track and
 *   starts playing it during the crossfade window.
 * - During crossfade: primary volume ramps down, overlay volume ramps up.
 * - When crossfade completes: primary seeks to the next track (instant since it was queued),
 *   primary volume restores to full, overlay stops.
 *
 * This approach keeps queue management simple (single source of truth in PlaybackManager)
 * while achieving true audio overlap.
 */
@OptIn(UnstableApi::class)
class CrossfadeEngine(
    private val context: Context,
    private val eqCoefficientsStore: EQCoefficientsStore,
) {
    /** The overlay player used for crossfade blending. Created lazily. */
    private var overlayPlayer: ExoPlayer? = null
    private val overlayBiquadProcessor = BiquadAudioProcessor(eqCoefficientsStore)

    private var crossfadeJob: Job? = null
    private var overlayPrepared = false

    var curve: CrossfadeCurve = CrossfadeCurve.EQUAL_POWER
    var enabled = false

    /**
     * Check if crossfade should begin and manage the overlay player.
     * Called from PlaybackManager's position tracking loop.
     *
     * @param primaryPlayer The main ExoPlayer holding the queue
     * @param nextMediaItem The next track's MediaItem (built with HTTP stream URL)
     * @param remainingMs Milliseconds remaining in the current track
     * @param crossfadeDurationMs The configured crossfade duration
     * @param scope Coroutine scope for the crossfade animation
     * @param baseVolume The base volume to apply (from ReplayGain, sleep timer, etc.)
     * @param onCrossfadeComplete Called when the crossfade finishes — primary should advance
     */
    fun checkCrossfade(
        primaryPlayer: ExoPlayer,
        nextMediaItem: MediaItem?,
        remainingMs: Long,
        crossfadeDurationMs: Long,
        scope: CoroutineScope,
        baseVolume: Float,
        onCrossfadeComplete: () -> Unit,
    ) {
        if (!enabled || nextMediaItem == null) return
        if (crossfadeJob?.isActive == true) return // Already crossfading

        val dur = primaryPlayer.duration
        if (dur <= 0 || remainingMs > crossfadeDurationMs || remainingMs <= 0) return
        // Don't crossfade very short tracks
        if (dur < crossfadeDurationMs * 2) return

        startCrossfade(
            primaryPlayer = primaryPlayer,
            nextMediaItem = nextMediaItem,
            durationMs = crossfadeDurationMs,
            scope = scope,
            baseVolume = baseVolume,
            onComplete = onCrossfadeComplete,
        )
    }

    private fun startCrossfade(
        primaryPlayer: ExoPlayer,
        nextMediaItem: MediaItem,
        durationMs: Long,
        scope: CoroutineScope,
        baseVolume: Float,
        onComplete: () -> Unit,
    ) {
        val overlay = getOrCreateOverlayPlayer()

        // Load and start the next track on overlay
        overlay.setMediaItem(nextMediaItem)
        overlay.prepare()
        overlay.volume = 0f
        overlay.play()
        overlayPrepared = true

        crossfadeJob = scope.launch {
            val stepMs = 33L
            val totalSteps = (durationMs / stepMs).toInt().coerceAtLeast(1)

            for (step in 1..totalSteps) {
                if (!isActive) break

                val progress = step.toFloat() / totalSteps
                val (primaryVol, overlayVol) = computeVolumes(progress)

                primaryPlayer.volume = (primaryVol * baseVolume).coerceIn(0f, 1f)
                overlay.volume = (overlayVol * baseVolume).coerceIn(0f, 1f)

                delay(stepMs)
            }

            // Crossfade complete
            primaryPlayer.volume = baseVolume
            overlay.stop()
            overlay.clearMediaItems()
            overlayPrepared = false

            onComplete()
        }
    }

    /**
     * Compute volume pair (primaryVolume, overlayVolume) for a given crossfade progress (0→1).
     */
    private fun computeVolumes(progress: Float): Pair<Float, Float> {
        return when (curve) {
            CrossfadeCurve.LINEAR -> {
                Pair(1f - progress, progress)
            }
            CrossfadeCurve.EQUAL_POWER -> {
                // Sine/cosine equal power: maintains constant loudness
                val angle = progress * (PI / 2).toFloat()
                Pair(cos(angle), sin(angle))
            }
            CrossfadeCurve.S_CURVE -> {
                // Hermite S-curve: 3t^2 - 2t^3
                val s = progress * progress * (3f - 2f * progress)
                Pair(1f - s, s)
            }
        }
    }

    /**
     * Cancel any active crossfade and reset volumes.
     * Called when the user skips, seeks, or changes queue during a crossfade.
     */
    fun cancelCrossfade(primaryPlayer: ExoPlayer, baseVolume: Float) {
        crossfadeJob?.cancel()
        crossfadeJob = null
        primaryPlayer.volume = baseVolume
        overlayPlayer?.let {
            it.stop()
            it.clearMediaItems()
        }
        overlayPrepared = false
    }

    /**
     * Whether a crossfade is currently in progress.
     */
    val isCrossfading: Boolean get() = crossfadeJob?.isActive == true

    private fun getOrCreateOverlayPlayer(): ExoPlayer {
        overlayPlayer?.let { return it }

        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessorChain(
                        DefaultAudioSink.DefaultAudioProcessorChain(
                            overlayBiquadProcessor as AudioProcessor
                        )
                    )
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
            }
        }

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Vibrdrome/1.0 (Android)")
            .setConnectTimeoutMs(30_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        return ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .also { overlayPlayer = it }
    }

    fun release() {
        crossfadeJob?.cancel()
        overlayPlayer?.release()
        overlayPlayer = null
    }
}
