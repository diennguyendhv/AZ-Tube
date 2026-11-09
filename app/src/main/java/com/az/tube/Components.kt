package com.az.tube

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

/** Everything a list item can ask the app to do. */
class UiActions(
    val play: (VideoItem) -> Unit,
    val playList: (List<VideoItem>, Int) -> Unit,
    val playNext: (VideoItem) -> Unit,
    val enqueue: (VideoItem) -> Unit,
    val openChannel: (url: String, name: String) -> Unit,
    val openPlaylist: (url: String, name: String) -> Unit,
    val share: (url: String) -> Unit,
    val showMenu: (VideoItem) -> Unit,
    val isSubscribed: (String?) -> Boolean,
    val toggleSubscribe: (ChannelItem) -> Unit,
)

val LocalActions = compositionLocalOf<UiActions> { error("UiActions not provided") }

@Composable
fun Avatar(url: String?, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url == null) {
            Icon(AppIcons.Person, null, Modifier.size(size * 0.6f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

/** AZ Tube logo: a play button inside a running track, on an orange tile. Same drawing as the launcher icon. */
@Composable
fun LogoMark(size: Dp = 28.dp) {
    androidx.compose.foundation.Canvas(Modifier.size(size)) {
        val u = this.size.width / 108f // the icon is drawn on a 108-unit grid
        drawRoundRect(Brand, cornerRadius = androidx.compose.ui.geometry.CornerRadius(26 * u))
        drawRoundRect(
            Color.White,
            topLeft = androidx.compose.ui.geometry.Offset(18 * u, 30 * u),
            size = androidx.compose.ui.geometry.Size(72 * u, 48 * u),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(24 * u),
            style = androidx.compose.ui.graphics.drawscope.Stroke(8 * u),
        )
        val play = androidx.compose.ui.graphics.Path().apply {
            moveTo(46 * u, 43 * u); lineTo(65 * u, 54 * u); lineTo(46 * u, 65 * u); close()
        }
        drawPath(play, Color.White)
        drawPath(
            play, Color.White,
            style = androidx.compose.ui.graphics.drawscope.Stroke(4 * u, join = androidx.compose.ui.graphics.StrokeJoin.Round),
        )
    }
}

@Composable
fun Logo() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LogoMark()
        Spacer(Modifier.width(6.dp))
        Text("AZ Tube", fontWeight = FontWeight.Bold, fontSize = 19.sp, letterSpacing = (-0.5).sp)
    }
}

@Composable
fun TopBar(
    title: String? = null,
    onBack: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(AppIcons.Back, "Quay lại") }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Box(Modifier.weight(1f).then(if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick) else Modifier)) {
            if (title == null) {
                Logo()
            } else {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (onSearch != null) IconButton(onClick = onSearch) { Icon(AppIcons.Search, "Tìm kiếm") }
    }
}

@Composable
fun DurationBadge(seconds: Long, modifier: Modifier = Modifier) {
    val d = formatDuration(seconds)
    if (d.isEmpty()) return
    Text(
        d,
        modifier.background(Color(0xCC000000), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp),
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
    )
}

/** Full-width card used on the home page, search results and "up next". */
@Composable
fun VideoCard(video: VideoItem, onClick: () -> Unit) {
    val actions = LocalActions.current
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(bottom = 16.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(MaterialTheme.colorScheme.surfaceVariant)) {
            AsyncImage(video.thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            DurationBadge(video.duration, Modifier.align(Alignment.BottomEnd).padding(8.dp))
        }
        Row(Modifier.padding(start = 12.dp, top = 12.dp)) {
            Avatar(
                video.channelAvatar,
                36.dp,
                Modifier.clickable(enabled = video.channelUrl != null) {
                    actions.openChannel(video.channelUrl!!, video.channel)
                },
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(video.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(
                    video.metaLine(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { actions.showMenu(video) }) {
                Icon(AppIcons.More, "Tuỳ chọn", Modifier.size(20.dp))
            }
        }
    }
}

/** Small row: thumbnail on the left, text on the right (history, queue). */
@Composable
fun CompactVideoRow(
    video: VideoItem,
    onClick: () -> Unit,
    highlighted: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    val actions = LocalActions.current
    Row(
        Modifier.fillMaxWidth()
            .background(if (highlighted) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(150.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            AsyncImage(video.thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            DurationBadge(video.duration, Modifier.align(Alignment.BottomEnd).padding(4.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(video.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                video.metaLine(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            trailing()
        } else {
            IconButton(onClick = { actions.showMenu(video) }) { Icon(AppIcons.More, "Tuỳ chọn", Modifier.size(20.dp)) }
        }
    }
}

@Composable
fun SubscribeButton(channel: ChannelItem, modifier: Modifier = Modifier) {
    val actions = LocalActions.current
    val subscribed = actions.isSubscribed(channel.url)
    val bg = if (subscribed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.onSurface
    val fg = if (subscribed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface
    Text(
        if (subscribed) "Đã đăng ký" else "Đăng ký",
        modifier.clip(RoundedCornerShape(50)).background(bg).clickable { actions.toggleSubscribe(channel) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = fg,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
    )
}

@Composable
fun ChannelResultRow(channel: ChannelItem) {
    val actions = LocalActions.current
    Row(
        Modifier.fillMaxWidth().clickable { actions.openChannel(channel.url, channel.name) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(channel.avatar, 80.dp)
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            NameWithBadge(channel.name, channel.verified, MaterialTheme.typography.titleMedium.fontSize)
            Text(subsText(channel.subscribers), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            SubscribeButton(channel)
        }
    }
}

@Composable
fun NameWithBadge(name: String, verified: Boolean, size: androidx.compose.ui.unit.TextUnit = 14.sp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, fontSize = size, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (verified) {
            Spacer(Modifier.width(4.dp))
            Icon(AppIcons.Verified, "Đã xác minh", Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PlaylistCard(p: PlaylistItem) {
    val actions = LocalActions.current
    Column(Modifier.fillMaxWidth().clickable { actions.openPlaylist(p.url, p.name) }.padding(bottom = 16.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(MaterialTheme.colorScheme.surfaceVariant)) {
            AsyncImage(p.thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Row(
                Modifier.align(Alignment.BottomEnd).padding(8.dp).background(Color(0xCC000000), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.PlaylistPlay, null, Modifier.size(14.dp), tint = Color.White)
                Spacer(Modifier.width(4.dp))
                Text(if (p.count >= 0) "${p.count} video" else "Danh sách", color = Color.White, fontSize = 12.sp)
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(p.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(p.uploader, "Danh sách phát").filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun EntryView(entry: Entry, onVideo: (VideoItem) -> Unit) {
    when (entry) {
        is VideoItem -> VideoCard(entry) { onVideo(entry) }
        is ChannelItem -> ChannelResultRow(entry)
        is PlaylistItem -> PlaylistCard(entry)
    }
}

@Composable
fun CenterMessage(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onAction) { Text(action, color = MaterialTheme.colorScheme.secondary) }
        }
    }
}

@Composable
fun Spinner(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(32.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, strokeWidth = 3.dp)
    }
}

/** A scrolling list that loads more when you reach the end and refreshes when pulled down. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryList(
    paged: Paged,
    modifier: Modifier = Modifier,
    empty: String = "Không có kết quả",
    onVideo: ((List<VideoItem>, VideoItem) -> Unit)? = null,
    header: (LazyListScope.() -> Unit)? = null,
) {
    val actions = LocalActions.current
    val listState = rememberLazyListState()
    LaunchedEffect(listState, paged) {
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) to info.totalItemsCount
        }.collect { (last, total) -> if (total > 0 && last >= total - 4) paged.loadMore() }
    }
    PullToRefreshBox(isRefreshing = paged.refreshing, onRefresh = { paged.refresh() }, modifier = modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
            header?.invoke(this)
            if (paged.items.isEmpty()) {
                item {
                    when {
                        paged.loading -> Spinner(Modifier.padding(top = 48.dp))
                        paged.error != null -> CenterMessage("Đã xảy ra lỗi\n${paged.error}", action = "Thử lại") { paged.refresh() }
                        else -> CenterMessage(empty)
                    }
                }
            }
            itemsIndexed(paged.items, key = { i, e -> "$i|${e.url}" }) { _, e ->
                EntryView(e) { v ->
                    if (onVideo != null) {
                        onVideo(paged.items.filterIsInstance<VideoItem>(), v)
                    } else {
                        actions.play(v)
                    }
                }
            }
            if (paged.loading && paged.items.isNotEmpty()) item { Spinner() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoMenuSheet(video: VideoItem, onDismiss: () -> Unit) {
    val actions = LocalActions.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            Text(
                video.title,
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            MenuRow(AppIcons.PlaylistPlay, "Phát tiếp theo") { actions.playNext(video); onDismiss() }
            MenuRow(AppIcons.QueueAdd, "Thêm vào cuối hàng chờ") { actions.enqueue(video); onDismiss() }
            if (video.channelUrl != null) {
                MenuRow(AppIcons.Person, "Đi tới kênh") { actions.openChannel(video.channelUrl, video.channel); onDismiss() }
            }
            MenuRow(AppIcons.Share, "Chia sẻ") { actions.share(video.url); onDismiss() }
        }
    }
}

@Composable
fun MenuRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null)
        Spacer(Modifier.width(20.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
    Text(
        text,
        Modifier.clip(RoundedCornerShape(8.dp)).background(bg).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = fg,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
    )
}

val ChipSpacing = Arrangement.spacedBy(8.dp)
