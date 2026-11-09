package com.az.tube

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

/** Mirrors the player's state into Compose. */
class PlayerState(val player: Player) {
    var isPlaying by mutableStateOf(false)
    var playWhenReady by mutableStateOf(false)
    var current by mutableStateOf<MediaItem?>(null)
    var index by mutableIntStateOf(0)
    var queue by mutableStateOf<List<MediaItem>>(emptyList())
    var buffering by mutableStateOf(false)
    var ended by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var speed by mutableFloatStateOf(1f)

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    init {
        player.addListener(listener)
        refresh()
    }

    private fun refresh() {
        isPlaying = player.isPlaying
        playWhenReady = player.playWhenReady
        current = player.currentMediaItem
        index = player.currentMediaItemIndex
        queue = List(player.mediaItemCount) { player.getMediaItemAt(it) }
        buffering = player.playbackState == Player.STATE_BUFFERING
        ended = player.playbackState == Player.STATE_ENDED
        error = player.playerError?.let { it.cause?.message ?: it.errorCodeName }
        speed = player.playbackParameters.speed
    }

    fun dispose() = player.removeListener(listener)
}

@Composable
private fun rememberPosition(player: Player): Pair<Long, Long> {
    val pos by produceState(0L to 0L, player) {
        while (true) {
            value = player.currentPosition to player.bufferedPosition
            delay(250)
        }
    }
    return pos
}

/** Thin YouTube-style seek bar: red progress, grey buffer, round thumb. */
@Composable
fun SeekBar(
    position: Long,
    buffered: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    onPreview: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    showThumb: Boolean = true,
) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val fraction = drag ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val bufferedFraction = if (duration > 0) (buffered.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Canvas(
        modifier.fillMaxWidth().height(24.dp)
            .pointerInput(duration) {
                detectTapGestures { o -> if (duration > 0) onSeek((o.x / size.width * duration).toLong()) }
            }
            .pointerInput(duration) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = {
                        drag?.let { if (duration > 0) onSeek((it * duration).toLong()) }
                        drag = null
                        onPreview(null)
                    },
                    onDragCancel = { drag = null; onPreview(null) },
                ) { change, _ ->
                    val f = (change.position.x / size.width).coerceIn(0f, 1f)
                    drag = f
                    onPreview((f * duration).toLong())
                }
            },
    ) {
        val y = size.height / 2
        val stroke = if (drag != null) 5.dp.toPx() else 3.dp.toPx()
        drawLine(Color(0x66FFFFFF), Offset(0f, y), Offset(size.width, y), stroke)
        drawLine(Color(0xAAFFFFFF), Offset(0f, y), Offset(size.width * bufferedFraction, y), stroke)
        drawLine(Brand, Offset(0f, y), Offset(size.width * fraction, y), stroke)
        if (showThumb) drawCircle(Brand, if (drag != null) 8.dp.toPx() else 6.dp.toPx(), Offset(size.width * fraction, y))
    }
}

private fun clock(ms: Long): String = formatDuration(ms / 1000).ifEmpty { "0:00" }

/**
 * The video with YouTube-like controls on top: tap to show/hide, double-tap left/right to
 * skip 10 seconds, swipe down to minimize.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(
    state: PlayerState,
    audioOnly: Boolean,
    fullscreen: Boolean,
    showControls: Boolean,
    onToggleFullscreen: () -> Unit,
    onCollapse: (() -> Unit)?,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val player = state.player
    val item = state.current
    var visible by remember { mutableStateOf(true) }
    var touchedAt by remember { mutableLongStateOf(0L) }
    var preview by remember { mutableStateOf<Long?>(null) }
    var seekHint by remember { mutableStateOf<Pair<Boolean, Long>?>(null) } // (forward?, time)
    val (position, buffered) = rememberPosition(player)
    val duration = player.duration.coerceAtLeast(0L)

    // Hide the controls 3 seconds after the last touch while playing.
    LaunchedEffect(visible, state.isPlaying, touchedAt) {
        if (visible && state.isPlaying) {
            delay(3000)
            visible = false
        }
    }
    LaunchedEffect(seekHint) {
        if (seekHint != null) {
            delay(700)
            seekHint = null
        }
    }

    Box(modifier.background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.player_view, null) as PlayerView).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    useController = false
                }
            },
            update = { view ->
                if (view.player !== player) view.player = player
                view.keepScreenOn = state.isPlaying && !audioOnly
            },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )
        if (audioOnly) {
            AsyncImage(item?.mediaMetadata?.artworkUri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            if (!visible) {
                Row(
                    Modifier.align(Alignment.TopStart).padding(8.dp).background(Color(0x99000000), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AppIcons.Headphones, null, Modifier.size(14.dp), tint = Color.White)
                    Spacer(Modifier.width(4.dp))
                    Text("Chỉ âm thanh", color = Color.White, fontSize = 11.sp)
                }
            }
        }
        if (!showControls) return@Box

        // Touch layer: taps, double-taps and the swipe-down-to-minimize gesture.
        Box(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { visible = !visible; touchedAt = System.currentTimeMillis() },
                        onDoubleTap = { o ->
                            val forward = o.x > size.width / 2
                            if (forward) player.seekForward() else player.seekBack()
                            seekHint = forward to System.currentTimeMillis()
                        },
                    )
                }
                .pointerInput(onCollapse) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (total > 120f) onCollapse?.invoke() },
                    ) { _, dy -> total += dy }
                },
        )

        seekHint?.let { (forward, _) ->
            Text(
                if (forward) "10 giây  »" else "«  10 giây",
                Modifier.align(if (forward) Alignment.CenterEnd else Alignment.CenterStart).padding(horizontal = 40.dp)
                    .background(Color(0x66000000), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 8.dp),
                color = Color.White,
                fontWeight = FontWeight.Medium,
            )
        }

        if (state.buffering && !visible) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(44.dp), color = Color.White, strokeWidth = 3.dp)
        }

        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color(0x77000000))) {
                // Top row
                Row(Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (onCollapse != null && !fullscreen) {
                        IconButton(onClick = onCollapse) { Icon(AppIcons.ExpandMore, "Thu nhỏ", tint = Color.White) }
                    }
                    if (fullscreen) {
                        Text(
                            item?.mediaMetadata?.title?.toString().orEmpty(),
                            Modifier.weight(1f).padding(start = 16.dp),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    IconButton(onClick = onSettings) { Icon(AppIcons.Settings, "Cài đặt", tint = Color.White) }
                }
                // Center row
                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundButton(AppIcons.Previous, "Bài trước", 48, enabled = true) {
                        player.seekToPrevious(); touchedAt = System.currentTimeMillis()
                    }
                    if (state.buffering) {
                        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(44.dp), color = Color.White, strokeWidth = 3.dp)
                        }
                    } else {
                        val icon = when {
                            state.ended -> AppIcons.Replay
                            state.playWhenReady -> AppIcons.Pause
                            else -> AppIcons.Play
                        }
                        RoundButton(icon, "Phát/Tạm dừng", 64, enabled = true) {
                            when {
                                state.ended -> { player.seekToDefaultPosition(); player.play() }
                                player.playWhenReady -> player.pause()
                                else -> { if (player.playbackState == Player.STATE_IDLE) player.prepare(); player.play() }
                            }
                            touchedAt = System.currentTimeMillis()
                        }
                    }
                    RoundButton(AppIcons.Next, "Bài tiếp", 48, enabled = player.hasNextMediaItem()) {
                        player.seekToNextMediaItem(); touchedAt = System.currentTimeMillis()
                    }
                }
                // Bottom row
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = if (fullscreen) 24.dp else 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${clock(preview ?: position)} / ${clock(duration)}",
                            Modifier.padding(start = 8.dp),
                            color = Color.White,
                            fontSize = 12.sp,
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onToggleFullscreen) {
                            Icon(if (fullscreen) AppIcons.FullscreenExit else AppIcons.Fullscreen, "Toàn màn hình", tint = Color.White)
                        }
                    }
                    SeekBar(
                        position, buffered, duration,
                        onSeek = { player.seekTo(it); touchedAt = System.currentTimeMillis() },
                        onPreview = { preview = it; touchedAt = System.currentTimeMillis() },
                        modifier = Modifier.padding(bottom = if (fullscreen) 16.dp else 0.dp),
                    )
                }
            }
        }
        if (!visible && !fullscreen && duration > 0) {
            // Thin red progress line along the bottom edge, like YouTube.
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(2.dp).background(Color(0x55FFFFFF))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth((position.toFloat() / duration).coerceIn(0f, 1f)).background(Brand))
            }
        }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, sizeDp: Int, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(sizeDp.dp).clip(CircleShape).background(Color(0x44000000)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, Modifier.size((sizeDp * 0.6f).dp), tint = if (enabled) Color.White else Color(0x66FFFFFF))
    }
}

/** The page shown while watching: video on top, details and "up next" below. */
@Composable
fun WatchScreen(
    vm: AppViewModel,
    state: PlayerState,
    onCollapse: () -> Unit,
    onFullscreen: () -> Unit,
    onSettings: () -> Unit,
) {
    val actions = LocalActions.current
    val item = state.current ?: return
    val d = vm.details?.takeIf { it.url == item.mediaId }
    var expanded by remember(item.mediaId) { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.background(Color.Black).statusBarsPadding()) {
                VideoSurface(
                    state = state,
                    audioOnly = vm.audioOnly,
                    fullscreen = false,
                    showControls = true,
                    onToggleFullscreen = onFullscreen,
                    onCollapse = onCollapse,
                    onSettings = onSettings,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                )
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                item {
                    Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 12.dp, vertical = 12.dp)) {
                        Text(
                            d?.title ?: item.mediaMetadata.title?.toString().orEmpty(),
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                            fontWeight = FontWeight.Bold,
                            maxLines = if (expanded) 10 else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            listOfNotNull(
                                d?.let { viewsText(it.views) }?.ifEmpty { null },
                                d?.let { timeAgo(it.uploadMillis, it.uploaded) }?.ifEmpty { null },
                                "...xem thêm".takeIf { !expanded },
                            ).joinToString("  "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item {
                    val channel = d?.channel ?: item.toVideoItem().let { v ->
                        ChannelItem(v.channelUrl.orEmpty(), v.channel, v.channelAvatar)
                    }
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable(enabled = channel.url.isNotEmpty()) { onCollapse(); actions.openChannel(channel.url, channel.name) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(channel.avatar, 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            NameWithBadge(channel.name, channel.verified, 15.sp)
                            Text(compactNumber(channel.subscribers), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (channel.url.isNotEmpty()) SubscribeButton(channel)
                    }
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item { PillButton(AppIcons.ThumbUp, d?.likes?.takeIf { it >= 0 }?.let { compactNumber(it) } ?: "Thích") {} }
                        item { PillButton(AppIcons.Share, "Chia sẻ") { actions.share(item.mediaId) } }
                        item {
                            PillButton(AppIcons.Headphones, if (vm.audioOnly) "Đang chỉ nghe" else "Chỉ nghe", selected = vm.audioOnly) {
                                vm.audioOnly = !vm.audioOnly
                                Store.audioOnly = vm.audioOnly
                            }
                        }
                        item { PillButton(AppIcons.PlaylistPlay, "Hàng chờ (${state.queue.size})", selected = showQueue) { showQueue = !showQueue } }
                    }
                }
                if (expanded && d != null && d.description.isNotBlank()) {
                    item {
                        Text(
                            d.description,
                            Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                if (showQueue) {
                    item {
                        Text(
                            "Hàng chờ · ${state.index + 1}/${state.queue.size}",
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    itemsIndexed(state.queue, key = { i, m -> "q$i${m.mediaId}" }) { i, m ->
                        CompactVideoRow(
                            m.toVideoItem(),
                            onClick = { state.player.seekToDefaultPosition(i); state.player.play() },
                            highlighted = i == state.index,
                            trailing = {
                                if (i != state.index) {
                                    IconButton(onClick = { state.player.removeMediaItem(i) }) { Icon(AppIcons.Close, "Bỏ khỏi hàng chờ", Modifier.size(20.dp)) }
                                } else {
                                    Spacer(Modifier.size(48.dp))
                                }
                            },
                        )
                    }
                    item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                }
                when {
                    d != null -> {
                        // Always shown, whatever "Đề xuất" is set to: more videos from this channel.
                        item {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (d.related.isEmpty()) "Tự động phát tiếp video cùng kênh" else "Video cùng kênh",
                                    Modifier.weight(1f),
                                    fontWeight = FontWeight.Bold,
                                )
                                Text("Tự động phát", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(8.dp))
                                androidx.compose.material3.Switch(
                                    checked = vm.autoplay,
                                    onCheckedChange = { on ->
                                        vm.changeAutoplay(on)
                                        // Off: take back the videos autoplay already queued after this one.
                                        if (!on) {
                                            val p = state.player
                                            for (i in p.mediaItemCount - 1 downTo p.currentMediaItemIndex + 1) {
                                                if (p.getMediaItemAt(i).mediaId in StreamResolver.autoAdded) p.removeMediaItem(i)
                                            }
                                        }
                                    },
                                )
                            }
                        }
                        itemsIndexed(d.related, key = { i, e -> "r$i${e.url}" }) { _, e -> EntryView(e) { actions.play(it) } }
                    }
                    vm.detailsError != null -> item {
                        CenterMessage("Không tải được thông tin video\n${vm.detailsError}", action = "Thử lại") { vm.retryDetails() }
                    }
                    else -> item { Spinner() }
                }
            }
        }
    }
}

@Composable
private fun PillButton(icon: ImageVector, text: String, selected: Boolean = false, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = fg)
        Spacer(Modifier.width(6.dp))
        Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/** Small bar above the bottom navigation while something is playing. */
@Composable
fun MiniPlayer(state: PlayerState, onExpand: () -> Unit, onClose: () -> Unit) {
    val item = state.current ?: return
    val (position, _) = rememberPosition(state.player)
    val duration = state.player.duration
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column {
            Row(
                Modifier.fillMaxWidth().height(60.dp).clickable(onClick = onExpand)
                    .pointerInput(Unit) {
                        var total = 0f
                        detectVerticalDragGestures(onDragStart = { total = 0f }, onDragEnd = { if (total < -60f) onExpand() }) { _, dy -> total += dy }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    item.mediaMetadata.artworkUri,
                    null,
                    Modifier.fillMaxHeight().aspectRatio(16f / 9f).background(Color.Black),
                    contentScale = ContentScale.Crop,
                )
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(item.mediaMetadata.title?.toString().orEmpty(), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        item.mediaMetadata.artist?.toString().orEmpty(),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (state.buffering) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurface)
                    }
                } else {
                    IconButton(onClick = { if (state.player.playWhenReady) state.player.pause() else state.player.play() }) {
                        Icon(if (state.playWhenReady) AppIcons.Pause else AppIcons.Play, "Phát/Tạm dừng")
                    }
                }
                IconButton(onClick = onClose) { Icon(AppIcons.Close, "Đóng") }
            }
            Box(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                if (duration > 0) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth((position.toFloat() / duration).coerceIn(0f, 1f)).background(Brand))
                }
            }
        }
    }
}

private val Speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
val Qualities = listOf(1080, 720, 480, 360, 240, 144)

/** Gear menu: quality, speed, audio only. */
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsSheet(vm: AppViewModel, state: PlayerState, onQuality: () -> Unit, onDismiss: () -> Unit) {
    var page by remember { mutableStateOf("main") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            when (page) {
                "speed" -> Speeds.forEach { s ->
                    val label = if (s == 1f) "Chuẩn" else "${s}x".replace(".0x", "x")
                    CheckRow(label, state.speed == s) {
                        state.player.playbackParameters = PlaybackParameters(s)
                        onDismiss()
                    }
                }
                else -> {
                    SettingLine("Chất lượng", "${vm.maxQuality}p") { onDismiss(); onQuality() }
                    SettingLine("Tốc độ phát", if (state.speed == 1f) "Chuẩn" else "${state.speed}x") { page = "speed" }
                    SwitchLine("Chỉ nghe âm thanh", "Không tải hình, tiết kiệm pin và dữ liệu", vm.audioOnly) {
                        vm.audioOnly = it
                        Store.audioOnly = it
                    }
                }
            }
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualitySheet(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            Text(
                "Chất lượng video tối đa",
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            Qualities.forEach { q -> CheckRow("${q}p" + if (q >= 720) " HD" else "", q == current) { onPick(q) } }
        }
    }
}

@Composable
private fun CheckRow(text: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp)) { if (checked) Icon(AppIcons.Check, null) }
        Spacer(Modifier.width(20.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
