@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.jhaiian.clint.mediacapture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import androidx.media3.ui.R as Media3UiR
import androidx.preference.PreferenceManager
import com.jhaiian.clint.R
import com.jhaiian.clint.base.ClintActivity
import com.jhaiian.clint.ui.theme.ClintComposeTheme
import com.jhaiian.clint.ui.theme.LocalClintColors
import kotlinx.coroutines.delay
import kotlin.math.abs

private const val EXTRA_TAB_ID = "tab_id"
private const val EXTRA_MEDIA_ID = "media_id"

private fun orientationFor(width: Int?, height: Int?): Int = when {
    width == null || height == null || width <= 0 || height <= 0 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    height > width -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
}

class VideoPreviewActivity : ClintActivity() {

    companion object {
        fun start(context: Context, tabId: String, mediaId: String) {
            context.startActivity(
                Intent(context, VideoPreviewActivity::class.java)
                    .putExtra(EXTRA_TAB_ID, tabId)
                    .putExtra(EXTRA_MEDIA_ID, mediaId)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        val tabId = intent.getStringExtra(EXTRA_TAB_ID)
        val mediaId = intent.getStringExtra(EXTRA_MEDIA_ID)
        val media = tabId?.let { id -> MediaCaptureStore.observe(id).value.firstOrNull { it.id == mediaId } }
        if (media == null) {
            finish()
            return
        }

        requestedOrientation = orientationFor(media.width, media.height)

        val theme = PreferenceManager.getDefaultSharedPreferences(this).getString("app_theme", "system") ?: "system"

        setContent {
            ClintComposeTheme(theme = theme) {
                VideoPreviewPlayer(
                    media = media,
                    onVideoSizeDetected = { width, height -> requestedOrientation = orientationFor(width, height) },
                    onKeepScreenOnChanged = { keepOn -> setKeepScreenOn(keepOn) },
                    onClose = { finish() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        window.decorView.post { hideSystemBars() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
            window.decorView.post { hideSystemBars() }
        }
    }

    private fun setKeepScreenOn(keepOn: Boolean) {
        if (keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun hideSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}

private fun PlayerView.applyAccentControls(accent: Int, onAccent: Int) {
    val accentTint = ColorStateList.valueOf(accent)
    (findViewById<View?>(Media3UiR.id.exo_play_pause) as? ImageView)?.apply {
        backgroundTintList = accentTint
        ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(onAccent))
    }
    listOf(
        Media3UiR.id.exo_rew_with_amount,
        Media3UiR.id.exo_ffwd_with_amount,
        Media3UiR.id.exo_prev,
        Media3UiR.id.exo_next,
        Media3UiR.id.exo_settings
    ).forEach { id ->
        (findViewById<View?>(id) as? ImageView)?.let {
            ImageViewCompat.setImageTintList(it, accentTint)
        }
    }
    (findViewById<View?>(Media3UiR.id.exo_progress) as? DefaultTimeBar)?.apply {
        setPlayedColor(accent)
        setScrubberColor(accent)
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

private fun getScreenBrightness(activity: Activity?): Float {
    if (activity == null) return 0.5f
    val current = activity.window.attributes.screenBrightness
    return if (current in 0.01f..1.0f) {
        current
    } else {
        try {
            Settings.System.getInt(
                activity.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS
            ) / 255f
        } catch (_: Exception) {
            0.5f
        }
    }
}

private fun setScreenBrightness(activity: Activity?, brightness: Float) {
    activity?.let {
        val lp = it.window.attributes
        lp.screenBrightness = brightness.coerceIn(0.01f, 1.0f)
        it.window.attributes = lp
    }
}

@Composable
private fun VideoPreviewPlayer(
    media: DetectedMedia,
    onVideoSizeDetected: (Int, Int) -> Unit,
    onKeepScreenOnChanged: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val clintColors = LocalClintColors.current
    val accentColor = remember(clintColors.primary) { clintColors.primary.toArgb() }
    val onAccentColor = remember(clintColors.onPrimary) { clintColors.onPrimary.toArgb() }
    var playbackState by remember(media.id) { mutableIntStateOf(Player.STATE_IDLE) }
    var hasError by remember(media.id) { mutableStateOf(false) }
    var controlsVisible by remember(media.id) { mutableStateOf(false) }

    // Custom features state
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var isLocked by remember { mutableStateOf(false) }
    var hudText by remember { mutableStateOf<String?>(null) }

    // Resize modes: Fit, Zoom (Crop), Stretch (Fill)
    val resizeModes = remember {
        listOf(
            AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Zoom",
            AspectRatioFrameLayout.RESIZE_MODE_FILL to "Stretch"
        )
    }
    var resizeIndex by remember { mutableIntStateOf(0) }

    // Speeds: 0.5x, 0.75x, 1.0x, 1.25x, 1.5x, 2.0x
    val speeds = remember { listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f) }
    var speedIndex by remember { mutableIntStateOf(2) } // default 1.0f

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }

    // Auto-hide HUD after 1.5 seconds
    LaunchedEffect(hudText) {
        if (hudText != null) {
            delay(1500)
            hudText = null
        }
    }

    val player = remember(media.id) {
        val dataSourceFactory = createMediaPreviewDataSourceFactory(context, media)
        ExoPlayer.Builder(context).build().apply {
            setMediaSource(buildMediaPreviewSource(media, dataSourceFactory))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player) {
        fun syncKeepScreenOn() {
            val active = player.playWhenReady &&
                (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING)
            onKeepScreenOnChanged(active)
        }
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                if (state == Player.STATE_READY) hasError = false
                syncKeepScreenOn()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                syncKeepScreenOn()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                syncKeepScreenOn()
            }

            override fun onPlayerError(error: PlaybackException) {
                hasError = true
                onKeepScreenOnChanged(false)
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    onVideoSizeDetected(videoSize.width, videoSize.height)
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            onKeepScreenOnChanged(false)
            player.release()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> player.pause()
                Lifecycle.Event.ON_RESUME -> if (!hasError) player.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Main ExoPlayer View
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    setUseController(true)
                    setPlayer(player)
                    resizeMode = resizeModes[resizeIndex].first
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            controlsVisible = visibility == View.VISIBLE
                        }
                    )
                    applyAccentControls(accentColor, onAccentColor)
                    playerViewRef = this
                }
            },
            update = {
                it.applyAccentControls(accentColor, onAccentColor)
                it.resizeMode = resizeModes[resizeIndex].first
            },
            modifier = Modifier.fillMaxSize()
        )

        // Web Video Cast style Gestures overlay (active when controls hidden or locked)
        var pendingSeekPos by remember { mutableLongStateOf(-1L) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isLocked, controlsVisible) {
                    if (isLocked) return@pointerInput

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startX = down.position.x
                        val startY = down.position.y
                        var isDragging = false
                        var dragMode = 0 // 0: None, 1: Seek, 2: Brightness, 3: Volume
                        var totalDeltaX = 0f
                        var totalDeltaY = 0f
                        val touchSlop = viewConfiguration.touchSlop
                        val initialPosition = player.currentPosition
                        val duration = player.duration.coerceAtLeast(0L)
                        val initialBrightness = getScreenBrightness(activity)
                        val initialVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

                        var currentEvent = down
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                if (isDragging) {
                                    if (dragMode == 1 && pendingSeekPos >= 0) {
                                        player.seekTo(pendingSeekPos)
                                        pendingSeekPos = -1L
                                    }
                                } else {
                                    // Single tap toggles default controls
                                    playerViewRef?.let { pv ->
                                        if (pv.isControllerFullyVisible) pv.hideController() else pv.showController()
                                    }
                                }
                                break
                            }

                            val deltaX = change.position.x - startX
                            val deltaY = change.position.y - startY

                            if (!isDragging) {
                                if (abs(deltaX) > touchSlop || abs(deltaY) > touchSlop) {
                                    isDragging = true
                                    change.consume()
                                    dragMode = if (abs(deltaX) > abs(deltaY)) {
                                        1 // Horizontal Seek
                                    } else if (startX < size.width / 2f) {
                                        2 // Left Vertical = Brightness
                                    } else {
                                        3 // Right Vertical = Volume
                                    }
                                }
                            } else {
                                change.consume()
                                val moveDeltaY = change.position.y - currentEvent.position.y
                                val moveDeltaX = change.position.x - currentEvent.position.x

                                when (dragMode) {
                                    1 -> { // Seek
                                        totalDeltaX += moveDeltaX
                                        val seekOffsetMs = (totalDeltaX / size.width * 120_000L).toLong()
                                        val target = (initialPosition + seekOffsetMs).coerceIn(0L, duration)
                                        pendingSeekPos = target
                                        val diffSec = (target - initialPosition) / 1000L
                                        val sign = if (diffSec >= 0) "+" else ""
                                        hudText = "${if (diffSec >= 0) "⏩" else "⏪"} ${formatTime(target)} / ${formatTime(duration)} (${sign}${diffSec}s)"
                                    }
                                    2 -> { // Brightness
                                        totalDeltaY -= moveDeltaY
                                        val deltaFraction = totalDeltaY / size.height
                                        val newBrightness = (initialBrightness + deltaFraction).coerceIn(0.01f, 1.0f)
                                        setScreenBrightness(activity, newBrightness)
                                        hudText = "☀️ ${(newBrightness * 100).toInt()}%"
                                    }
                                    3 -> { // Volume
                                        totalDeltaY -= moveDeltaY
                                        val deltaFraction = totalDeltaY / size.height
                                        val newVol = (initialVol + deltaFraction * maxVolume).toInt().coerceIn(0, maxVolume)
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                        hudText = "🔊 ${(newVol * 100 / maxVolume)}%"
                                    }
                                }
                            }
                            currentEvent = change
                        }
                    }
                }
        )

        // Loading Buffering Indicator
        if (playbackState == Player.STATE_BUFFERING && !hasError) {
            CircularProgressIndicator(
                color = clintColors.primary,
                trackColor = clintColors.primary.copy(alpha = 0.25f),
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Error message
        if (hasError) {
            Text(
                stringResource(R.string.media_capture_video_preview_error),
                color = Color.White,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 16.dp)
            )
        }

        // Center HUD Feedback Card (Volume / Brightness / Seek / Speed / Resize)
        AnimatedVisibility(
            visible = hudText != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            hudText?.let { text ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = text,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Top Controls Bar (Web Video Cast style controls)
        AnimatedVisibility(
            visible = controlsVisible && !isLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .displayCutoutPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Close button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = Color.White
                    )
                }

                // Action buttons: Seek -10s, +10s, Resize, Speed, Rotate, Lock
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Rewind 10s
                    PlayerActionChip(text = "⏪ 10s") {
                        val target = (player.currentPosition - 10_000L).coerceAtLeast(0L)
                        player.seekTo(target)
                        hudText = "⏪ -10s"
                    }

                    // Forward 10s
                    PlayerActionChip(text = "10s ⏩") {
                        val target = (player.currentPosition + 10_000L).coerceAtMost(player.duration.coerceAtLeast(0L))
                        player.seekTo(target)
                        hudText = "⏩ +10s"
                    }

                    // Resize / Aspect Ratio
                    PlayerActionChip(text = resizeModes[resizeIndex].second) {
                        resizeIndex = (resizeIndex + 1) % resizeModes.size
                        hudText = "Resize: ${resizeModes[resizeIndex].second}"
                    }

                    // Speed
                    PlayerActionChip(text = "${speeds[speedIndex]}x") {
                        speedIndex = (speedIndex + 1) % speeds.size
                        val newSpeed = speeds[speedIndex]
                        player.playbackParameters = PlaybackParameters(newSpeed)
                        hudText = "Speed: ${newSpeed}x"
                    }

                    // Orientation rotate
                    PlayerActionChip(text = "🔄") {
                        activity?.let { act ->
                            val isLandscape = act.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                            act.requestedOrientation = if (isLandscape) {
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            }
                        }
                    }

                    // Lock button
                    PlayerActionChip(text = "🔒 Lock") {
                        isLocked = true
                        playerViewRef?.hideController()
                        hudText = "Controls Locked"
                    }
                }
            }
        }

        // Floating Unlock button when locked
        if (isLocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .displayCutoutPadding()
                    .padding(16.dp)
            ) {
                PlayerActionChip(text = "🔓 Unlock") {
                    isLocked = false
                    hudText = "Unlocked"
                    playerViewRef?.showController()
                }
            }
        }
    }
}

@Composable
private fun PlayerActionChip(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
