@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.jhaiian.clint.mediacapture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.preference.PreferenceManager
import com.jhaiian.clint.R
import com.jhaiian.clint.base.ClintActivity
import com.jhaiian.clint.ui.theme.ClintComposeTheme
import com.jhaiian.clint.ui.theme.LocalClintColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
                AllInOneVideoPlayer(
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
    return if (current in 0.01f..1.0f) current else {
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

data class TrackItem(val id: Int, val label: String, val isSelected: Boolean)

@Composable
private fun PlayIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.28f, size.height * 0.18f)
            lineTo(size.width * 0.82f, size.height * 0.5f)
            lineTo(size.width * 0.28f, size.height * 0.82f)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
private fun PauseIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val barWidth = size.width * 0.22f
        val barHeight = size.height * 0.65f
        val top = size.height * 0.175f
        drawRect(
            color = color,
            topLeft = Offset(size.width * 0.22f, top),
            size = Size(barWidth, barHeight)
        )
        drawRect(
            color = color,
            topLeft = Offset(size.width * 0.56f, top),
            size = Size(barWidth, barHeight)
        )
    }
}

@Composable
private fun AllInOneVideoPlayer(
    media: DetectedMedia,
    onVideoSizeDetected: (Int, Int) -> Unit,
    onKeepScreenOnChanged: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val clintColors = LocalClintColors.current

    var playbackState by remember(media.id) { mutableIntStateOf(Player.STATE_IDLE) }
    var isPlaying by remember(media.id) { mutableStateOf(false) }
    var hasError by remember(media.id) { mutableStateOf(false) }

    // UI state
    var controlsVisible by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var lockButtonVisible by remember { mutableStateOf(false) }
    var hudText by remember { mutableStateOf<String?>(null) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    // Clock state
    var currentTimeString by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val formatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
        while (true) {
            currentTimeString = formatter.format(Date())
            delay(10000)
        }
    }

    // Progress timeline
    var currentPosition by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    // Resize modes: Fit, Zoom, Stretch
    val resizeModes = remember {
        listOf(
            AspectRatioFrameLayout.RESIZE_MODE_FIT to "FIT",
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "ZOOM",
            AspectRatioFrameLayout.RESIZE_MODE_FILL to "STRETCH"
        )
    }
    var resizeIndex by remember { mutableIntStateOf(0) }

    // Speeds: 0.5x, 0.75x, 1.0x, 1.25x, 1.5x, 2.0x
    val speeds = remember { listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f) }
    var speedIndex by remember { mutableIntStateOf(2) }

    // Audio & Subtitles
    var showAudioDialog by remember { mutableStateOf(false) }
    var showSubDialog by remember { mutableStateOf(false) }
    var audioTracks by remember { mutableStateOf<List<TrackItem>>(emptyList()) }
    var subTracks by remember { mutableStateOf<List<TrackItem>>(emptyList()) }
    var isSubEnabled by remember { mutableStateOf(false) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }

    fun showHud(message: String) {
        hudText = message
    }

    LaunchedEffect(hudText) {
        if (hudText != null) {
            delay(1500)
            hudText = null
        }
    }

    // Auto-hide controls after 4s
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    fun resetControlsTimeout() {
        lastInteraction = System.currentTimeMillis()
    }

    LaunchedEffect(controlsVisible, lastInteraction, isPlaying, isLocked) {
        if (controlsVisible && isPlaying && !isLocked) {
            delay(4000)
            controlsVisible = false
        }
    }

    // Auto-hide Unlock button after 3s
    LaunchedEffect(lockButtonVisible) {
        if (lockButtonVisible) {
            delay(3000)
            lockButtonVisible = false
        }
    }

    // ExoPlayer Builder with Subtitles DISABLED by default
    val player = remember(media.id) {
        val dataSourceFactory = createMediaPreviewDataSourceFactory(context, media)
        ExoPlayer.Builder(context).build().apply {
            setMediaSource(buildMediaPreviewSource(media, dataSourceFactory))
            // Auto subtitle OFF by default:
            trackSelectionParameters = trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            prepare()
            playWhenReady = true
        }
    }

    LaunchedEffect(player) {
        while (true) {
            if (!isScrubbing) {
                currentPosition = player.currentPosition.coerceAtLeast(0L)
                totalDuration = player.duration.coerceAtLeast(0L)
            }
            delay(400)
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

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
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

    fun refreshTracks() {
        val currentTracks = player.currentTracks
        val audios = mutableListOf<TrackItem>()
        val subs = mutableListOf<TrackItem>()
        var aIdx = 0
        var sIdx = 0

        for (group in currentTracks.groups) {
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val label = format.label ?: format.language?.uppercase() ?: "Audio ${aIdx + 1}"
                    audios.add(TrackItem(aIdx, label, group.isTrackSelected(i)))
                    aIdx++
                }
            } else if (group.type == C.TRACK_TYPE_TEXT) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val label = format.label ?: format.language?.uppercase() ?: "Subtitle ${sIdx + 1}"
                    subs.add(TrackItem(sIdx, label, group.isTrackSelected(i)))
                    sIdx++
                }
            }
        }
        audioTracks = audios
        subTracks = subs
        isSubEnabled = subs.any { it.isSelected } && !player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Player Surface
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    useController = false
                    setPlayer(player)
                    resizeMode = resizeModes[resizeIndex].first
                    playerViewRef = this
                }
            },
            update = {
                it.resizeMode = resizeModes[resizeIndex].first
            },
            modifier = Modifier.fillMaxSize()
        )

        // Gesture Overlay (with Double-Tap Seek and Long-Press 2x)
        var pendingSeekTarget by remember { mutableLongStateOf(-1L) }
        var isFastForwarding by remember { mutableStateOf(false) }
        var lastTapTime by remember { mutableLongStateOf(0L) }
        var lastTapX by remember { mutableFloatStateOf(0f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isLocked) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downTime = System.currentTimeMillis()
                        val startX = down.position.x
                        val startY = down.position.y
                        var isDragging = false
                        var dragMode = 0 // 1: Seek, 2: Brightness, 3: Volume
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

                            // Long press for 2x speed
                            if (!isLocked && !isDragging && !isFastForwarding && (System.currentTimeMillis() - downTime > 450L)) {
                                isFastForwarding = true
                                player.playbackParameters = PlaybackParameters(2.0f)
                                showHud("2.0x >>")
                            }

                            if (!change.pressed) {
                                if (isFastForwarding) {
                                    isFastForwarding = false
                                    player.playbackParameters = PlaybackParameters(speeds[speedIndex])
                                    showHud("${speeds[speedIndex]}x")
                                } else if (isDragging) {
                                    if (dragMode == 1 && pendingSeekTarget >= 0) {
                                        player.seekTo(pendingSeekTarget)
                                        currentPosition = pendingSeekTarget
                                        pendingSeekTarget = -1L
                                    }
                                } else {
                                    // Taps: Check double-tap vs single-tap
                                    val now = System.currentTimeMillis()
                                    if (!isLocked && (now - lastTapTime < 320L) && abs(startX - lastTapX) < 160f) {
                                        // Double Tap Seek!
                                        if (startX < size.width / 2f) {
                                            val newPos = (player.currentPosition - 10_000L).coerceAtLeast(0L)
                                            player.seekTo(newPos)
                                            currentPosition = newPos
                                            showHud("<< 10s")
                                        } else {
                                            val dur = player.duration.coerceAtLeast(0L)
                                            val newPos = (player.currentPosition + 10_000L).coerceAtMost(dur)
                                            player.seekTo(newPos)
                                            currentPosition = newPos
                                            showHud("10s >>")
                                        }
                                        lastTapTime = 0L
                                    } else {
                                        lastTapTime = now
                                        lastTapX = startX
                                        if (isLocked) {
                                            lockButtonVisible = !lockButtonVisible
                                        } else {
                                            controlsVisible = !controlsVisible
                                            resetControlsTimeout()
                                        }
                                    }
                                }
                                break
                            }

                            val deltaX = change.position.x - startX
                            val deltaY = change.position.y - startY

                            if (!isLocked && !isFastForwarding) {
                                if (!isDragging) {
                                    if (abs(deltaX) > touchSlop || abs(deltaY) > touchSlop) {
                                        isDragging = true
                                        change.consume()
                                        dragMode = if (abs(deltaX) > abs(deltaY)) 1 else if (startX < size.width / 2f) 2 else 3
                                    }
                                } else {
                                    change.consume()
                                    val moveDeltaY = change.position.y - currentEvent.position.y
                                    val moveDeltaX = change.position.x - currentEvent.position.x

                                    when (dragMode) {
                                        1 -> {
                                            totalDeltaX += moveDeltaX
                                            val seekOffsetMs = (totalDeltaX / size.width * 120_000L).toLong()
                                            val target = (initialPosition + seekOffsetMs).coerceIn(0L, duration)
                                            pendingSeekTarget = target
                                            val diffSec = (target - initialPosition) / 1000L
                                            val sign = if (diffSec >= 0) "+" else ""
                                            showHud("${if (diffSec >= 0) ">>" else "<<"} ${formatTime(target)} / ${formatTime(duration)} (${sign}${diffSec}s)")
                                        }
                                        2 -> {
                                            totalDeltaY -= moveDeltaY
                                            val deltaFraction = totalDeltaY / size.height
                                            val newBrightness = (initialBrightness + deltaFraction).coerceIn(0.01f, 1.0f)
                                            setScreenBrightness(activity, newBrightness)
                                            showHud("Brightness: ${(newBrightness * 100).toInt()}%")
                                        }
                                        3 -> {
                                            totalDeltaY -= moveDeltaY
                                            val deltaFraction = totalDeltaY / size.height
                                            val newVol = (initialVol + deltaFraction * maxVolume).toInt().coerceIn(0, maxVolume)
                                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                            showHud("Volume: ${(newVol * 100 / maxVolume)}%")
                                        }
                                    }
                                }
                            }
                            currentEvent = change
                        }
                    }
                }
        )

        // Buffering Spinner
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

        // Center HUD Feedback Toast
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
                        .background(Color.Black.copy(alpha = 0.82f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = text,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Center Play / Pause & Skip Buttons
        AnimatedVisibility(
            visible = controlsVisible && !isLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(38.dp)
            ) {
                // -10s
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                        .clickable {
                            val newPos = (player.currentPosition - 10_000L).coerceAtLeast(0L)
                            player.seekTo(newPos)
                            currentPosition = newPos
                            showHud("<< 10s")
                            resetControlsTimeout()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("-10s", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Play / Pause Button with Glow & Accent
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(clintColors.primary)
                        .clickable {
                            if (player.isPlaying) player.pause() else player.play()
                            resetControlsTimeout()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isPlaying) {
                        PauseIcon(color = clintColors.onPrimary, modifier = Modifier.size(26.dp))
                    } else {
                        PlayIcon(color = clintColors.onPrimary, modifier = Modifier.size(26.dp))
                    }
                }

                // +10s
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                        .clickable {
                            val dur = player.duration.coerceAtLeast(0L)
                            val newPos = (player.currentPosition + 10_000L).coerceAtMost(dur)
                            player.seekTo(newPos)
                            currentPosition = newPos
                            showHud("10s >>")
                            resetControlsTimeout()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("+10s", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Top Gradient Bar (with Clock, Audio, CC, Resize, Speed, Rotate, Lock)
        AnimatedVisibility(
            visible = controlsVisible && !isLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                        )
                    )
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .displayCutoutPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.action_close),
                            tint = Color.White
                        )
                    }

                    if (currentTimeString.isNotEmpty()) {
                        Text(
                            text = currentTimeString,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Audio Selector
                        ModernPlayerPill(label = "AUDIO") {
                            refreshTracks()
                            if (audioTracks.isNotEmpty()) showAudioDialog = true
                            else showHud("Single Audio Track")
                            resetControlsTimeout()
                        }

                        // Subtitle (CC) Selector
                        ModernPlayerPill(
                            label = if (isSubEnabled) "CC (ON)" else "CC",
                            isActive = isSubEnabled,
                            activeColor = clintColors.primary
                        ) {
                            refreshTracks()
                            if (subTracks.isNotEmpty()) showSubDialog = true
                            else showHud("No Subtitles Detected")
                            resetControlsTimeout()
                        }

                        // Resize
                        ModernPlayerPill(label = resizeModes[resizeIndex].second) {
                            resizeIndex = (resizeIndex + 1) % resizeModes.size
                            showHud("Resize: ${resizeModes[resizeIndex].second}")
                            resetControlsTimeout()
                        }

                        // Speed
                        ModernPlayerPill(
                            label = "${speeds[speedIndex]}x",
                            isActive = speeds[speedIndex] != 1.0f,
                            activeColor = clintColors.primary
                        ) {
                            speedIndex = (speedIndex + 1) % speeds.size
                            val newSpeed = speeds[speedIndex]
                            player.playbackParameters = PlaybackParameters(newSpeed)
                            showHud("Speed: ${newSpeed}x")
                            resetControlsTimeout()
                        }

                        // Rotate
                        ModernPlayerPill(label = "ROTATE") {
                            activity?.let { act ->
                                val isLandscape = act.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                                act.requestedOrientation = if (isLandscape) {
                                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                                } else {
                                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                }
                            }
                            resetControlsTimeout()
                        }

                        // Lock
                        ModernPlayerPill(label = "LOCK") {
                            isLocked = true
                            controlsVisible = false
                            lockButtonVisible = true
                            showHud("Controls Locked")
                        }
                    }
                }
            }
        }

        // Bottom Gradient Bar & Timeline
        AnimatedVisibility(
            visible = controlsVisible && !isLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isScrubbing) formatTime((scrubFraction * totalDuration).toLong()) else formatTime(currentPosition),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(Modifier.width(10.dp))

                    val sliderVal = if (totalDuration > 0) {
                        if (isScrubbing) scrubFraction else (currentPosition.toFloat() / totalDuration).coerceIn(0f, 1f)
                    } else 0f

                    Slider(
                        value = sliderVal,
                        onValueChange = { fraction ->
                            isScrubbing = true
                            scrubFraction = fraction
                        },
                        onValueChangeFinished = {
                            val target = (scrubFraction * totalDuration).toLong()
                            player.seekTo(target)
                            currentPosition = target
                            isScrubbing = false
                            resetControlsTimeout()
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = clintColors.primary,
                            activeTrackColor = clintColors.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.28f)
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(Modifier.width(10.dp))

                    Text(
                        text = formatTime(totalDuration),
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Auto-hiding Floating Unlock Button
        AnimatedVisibility(
            visible = isLocked && lockButtonVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.systemBars)
                .displayCutoutPadding()
                .padding(16.dp)
        ) {
            ModernPlayerPill(label = "UNLOCK") {
                isLocked = false
                lockButtonVisible = false
                controlsVisible = true
                showHud("Unlocked")
                resetControlsTimeout()
            }
        }

        // Audio Track Selection Dialog
        if (showAudioDialog) {
            AlertDialog(
                onDismissRequest = { showAudioDialog = false },
                title = { Text("Select Audio Track", fontWeight = FontWeight.Bold) },
                text = {
                    LazyColumn {
                        itemsIndexed(audioTracks) { idx, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        var count = 0
                                        val current = player.currentTracks
                                        for (group in current.groups) {
                                            if (group.type == C.TRACK_TYPE_AUDIO) {
                                                for (i in 0 until group.length) {
                                                    if (count == idx) {
                                                        player.trackSelectionParameters = player.trackSelectionParameters
                                                            .buildUpon()
                                                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                                                            .build()
                                                        showHud("Audio: ${item.label}")
                                                        showAudioDialog = false
                                                        return@clickable
                                                    }
                                                    count++
                                                }
                                            }
                                        }
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = item.isSelected,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(selectedColor = clintColors.primary)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(item.label, fontSize = 14.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAudioDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }

        // Subtitle (CC) Selection Dialog (Off by default)
        if (showSubDialog) {
            AlertDialog(
                onDismissRequest = { showSubDialog = false },
                title = { Text("Subtitles (CC)", fontWeight = FontWeight.Bold) },
                text = {
                    LazyColumn {
                        // Option 1: Off (Default)
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        player.trackSelectionParameters = player.trackSelectionParameters
                                            .buildUpon()
                                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                            .build()
                                        isSubEnabled = false
                                        showHud("Subtitles Disabled")
                                        showSubDialog = false
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = !isSubEnabled,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(selectedColor = clintColors.primary)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Off (Disable)", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                        }

                        // Option 2..N: Detected tracks
                        itemsIndexed(subTracks) { idx, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        var count = 0
                                        val current = player.currentTracks
                                        for (group in current.groups) {
                                            if (group.type == C.TRACK_TYPE_TEXT) {
                                                for (i in 0 until group.length) {
                                                    if (count == idx) {
                                                        player.trackSelectionParameters = player.trackSelectionParameters
                                                            .buildUpon()
                                                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                                                            .build()
                                                        isSubEnabled = true
                                                        showHud("Sub: ${item.label}")
                                                        showSubDialog = false
                                                        return@clickable
                                                    }
                                                    count++
                                                }
                                            }
                                        }
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSubEnabled && item.isSelected,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(selectedColor = clintColors.primary)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(item.label, fontSize = 14.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSubDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

@Composable
private fun ModernPlayerPill(
    label: String,
    isActive: Boolean = false,
    activeColor: Color = Color.White,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isActive) activeColor.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.16f))
            .border(
                1.dp,
                if (isActive) activeColor.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.12f),
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isActive) Color.White else Color.White.copy(alpha = 0.9f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
