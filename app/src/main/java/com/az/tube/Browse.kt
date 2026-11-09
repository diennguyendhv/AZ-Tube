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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

@Composable
fun SearchResultsScreen(vm: AppViewModel, route: Route.Search) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) { Icon(AppIcons.Back, "Quay lại") }
            Text(
                route.query,
                Modifier.weight(1f).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { vm.openSearch(route.query) }.padding(horizontal = 16.dp, vertical = 9.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
        }
        EntryList(vm.pagedFor(route), Modifier.weight(1f))
    }
}

/** Home: a recommended feed by topic when recommendations are on, otherwise only your subscriptions. */
@Composable
fun HomeScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize()) {
        TopBar(onSearch = { vm.openSearch() })
        if (vm.recommendationsOn) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = ChipSpacing,
            ) {
                items(HomeCategories) { c -> Chip(c, c == vm.category) { vm.selectCategory(c) } }
            }
            EntryList(vm.homeFeed, Modifier.weight(1f))
        } else {
            SubscriptionFeed(
                vm,
                emptyText = "Trang chủ chỉ hiện video mới từ kênh bạn đăng ký.\n" +
                    "Bấm nút tìm kiếm, rồi bấm \"Đăng ký\" kênh bạn muốn theo dõi.\n\n" +
                    "Muốn có video đề xuất? Bật \"Đề xuất\" trong tab Bạn.",
            )
        }
    }
}

@Composable
fun SubscriptionsScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize()) {
        TopBar(title = "Kênh đăng ký", onSearch = { vm.openSearch() })
        SubscriptionFeed(vm, "Bạn chưa đăng ký kênh nào.\nBấm \"Đăng ký\" trên trang video hoặc trang kênh để video mới của kênh hiện ở đây.")
    }
}

/** Newest videos from subscribed channels, with a row of channel avatars on top. */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.SubscriptionFeed(vm: AppViewModel, emptyText: String) {
    if (vm.subscriptions.isEmpty()) {
        CenterMessage(emptyText)
        return
    }
    val actions = LocalActions.current
    EntryList(vm.pagedFor(Route.Subscriptions), Modifier.weight(1f), empty = "Chưa có video mới") {
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(vm.subscriptions, key = { it.url }) { ch ->
                    Column(
                        Modifier.width(64.dp).clickable { actions.openChannel(ch.url, ch.name) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Avatar(ch.avatar, 56.dp)
                        Text(ch.name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun LibraryScreen(vm: AppViewModel, onQuality: () -> Unit) {
    val actions = LocalActions.current
    var lockDialog by remember { mutableStateOf<LockDialog?>(null) }
    lockDialog?.let { mode ->
        LockSettingsDialog(mode, onResult = { vm.message = it; lockDialog = null }, onDismiss = { lockDialog = null })
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(title = "Bạn", onSearch = { vm.openSearch() })
        LazyColumn(Modifier.weight(1f)) {
            if (vm.watchHistoryOn) {
                item {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Video đã xem", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (vm.watchHistory.isNotEmpty()) {
                            TextButton(onClick = { vm.clearHistory() }) { Text("Xoá lịch sử", color = MaterialTheme.colorScheme.secondary) }
                        }
                    }
                }
                if (vm.watchHistory.isEmpty()) {
                    item { CenterMessage("Video bạn xem sẽ hiện ở đây") }
                } else {
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(vm.watchHistory.take(30), key = { it.url }) { v ->
                                Column(Modifier.width(150.dp).clickable { actions.play(v) }) {
                                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                        AsyncImage(v.thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                        DurationBadge(v.duration, Modifier.align(Alignment.BottomEnd).padding(4.dp))
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(v.title, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(v.channel, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                            }
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                }
            }
            item {
                Text(
                    "Kênh đã đăng ký (${vm.subscriptions.size})",
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            items(vm.subscriptions, key = { "sub" + it.url }) { ch ->
                Row(
                    Modifier.fillMaxWidth().clickable { actions.openChannel(ch.url, ch.name) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(ch.avatar, 40.dp)
                    Spacer(Modifier.width(16.dp))
                    Text(ch.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    SubscribeButton(ch)
                }
            }
            if (Blocked.channels.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Text(
                        "Kênh không đề xuất (${Blocked.channels.size})",
                        Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                items(Blocked.channels, key = { "blk" + it.url }) { ch ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(ch.avatar, 40.dp)
                        Spacer(Modifier.width(16.dp))
                        Text(ch.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { Blocked.unblock(ch.url) }) { Text("Bỏ chặn", color = MaterialTheme.colorScheme.secondary) }
                    }
                }
            }
            item {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Text("Quyền riêng tư", Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                SwitchLine("Lịch sử xem", "Lưu video đã xem. Tắt sẽ xoá lịch sử đã lưu", vm.watchHistoryOn) { vm.setWatchHistory(it) }
                SwitchLine("Lịch sử tìm kiếm", "Lưu từ khoá đã tìm. Tắt sẽ xoá lịch sử đã lưu", vm.searchHistoryOn) { vm.setSearchHistory(it) }
                SwitchLine("Đề xuất", "Trang chủ theo chủ đề: kiến thức, bài học hay. Tắt thì trang chủ chỉ có video từ kênh đã đăng ký", vm.recommendationsOn) { vm.setRecommendations(it) }
                SwitchLine("Tự động phát", "Hết hàng chờ thì phát tiếp video cùng kênh", vm.autoplay) { vm.changeAutoplay(it) }
                SwitchLine("Khóa ứng dụng bằng mã PIN", "Hỏi mã PIN mỗi khi mở hoặc quay lại AZ Tube", AppLock.enabled) {
                    lockDialog = if (it) LockDialog.Enable else LockDialog.Disable
                }
                if (AppLock.enabled) SettingLine("Đổi mã PIN", "") { lockDialog = LockDialog.Change }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Text("Cài đặt", Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                SettingLine("Chất lượng video tối đa", "${vm.maxQuality}p", onClick = onQuality)
                SwitchLine("Chỉ nghe âm thanh", "Không tải hình, tiết kiệm pin và dữ liệu", vm.audioOnly) {
                    vm.audioOnly = it
                    Store.audioOnly = it
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Text("Cập nhật", Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                UpdateSettings(onMessage = { vm.message = it })
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun SettingLine(title: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SwitchLine(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun ChannelScreen(vm: AppViewModel, route: Route.Channel) {
    val paged = vm.pagedFor(route)
    val header = paged.header as? ChannelHeader
    Column(Modifier.fillMaxSize()) {
        TopBar(title = header?.channel?.name ?: route.name, onBack = { vm.back() }, onSearch = { vm.openSearch() })
        EntryList(paged, Modifier.weight(1f), empty = "Kênh chưa có video") {
            if (header != null) {
                item {
                    Column {
                        if (header.banner != null) {
                            AsyncImage(
                                header.banner,
                                null,
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp).aspectRatio(6f).clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(header.channel.avatar, 72.dp)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                NameWithBadge(header.channel.name, header.channel.verified, 22.sp)
                                Text(
                                    subsText(header.channel.subscribers),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (header.description.isNotBlank()) {
                            Text(
                                header.description,
                                Modifier.padding(horizontal = 16.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        SubscribeButton(header.channel, Modifier.padding(16.dp).fillMaxWidth())
                        if (Blocked.isBlocked(header.channel.url)) {
                            CenterMessage(
                                "Bạn đã chọn không đề xuất kênh này nên video của kênh bị ẩn",
                                action = "Bỏ chặn",
                            ) { Blocked.unblock(header.channel.url) }
                        }
                        Text("Video", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontWeight = FontWeight.Bold)
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistScreen(vm: AppViewModel, route: Route.Playlist) {
    val actions = LocalActions.current
    val paged = vm.pagedFor(route)
    val header = paged.header as? PlaylistHeader
    // Plays the list from [index] on right away, then adds the pages not loaded yet to the queue.
    fun playFrom(list: List<VideoItem>, index: Int) {
        if (list.isEmpty()) return
        actions.playList(list, index)
        paged.fetchRest(max = 1000, known = list.map { it.url }.toSet()) { rest ->
            val videos = rest.filterIsInstance<VideoItem>()
            if (videos.isNotEmpty()) actions.appendToQueue(list.first().url, videos)
        }
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(title = header?.name ?: route.name, onBack = { vm.back() }, onSearch = { vm.openSearch() })
        // In a playlist, tapping a video plays the list from that video on.
        EntryList(paged, Modifier.weight(1f), onVideo = { list, v -> playFrom(list, list.indexOf(v)) }) {
            if (header != null) {
                item {
                    Column(Modifier.padding(16.dp)) {
                        Text(header.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            listOf(header.uploader, if (header.count >= 0) "${header.count} video" else "").filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { playFrom(paged.items.filterIsInstance<VideoItem>(), 0) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onSurface,
                                contentColor = MaterialTheme.colorScheme.surface,
                            ),
                        ) {
                            Icon(AppIcons.Play, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Phát tất cả")
                        }
                    }
                }
            }
        }
    }
}

/** Full-screen search box. Search history appears only when its switch is on; suggestions always do. */
@Composable
fun SearchOverlay(vm: AppViewModel) {
    val focus = remember { FocusRequester() }
    var field by remember { mutableStateOf(TextFieldValue(vm.searchText, TextRange(vm.searchText.length))) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.searchOpen = false }) { Icon(AppIcons.Back, "Quay lại") }
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
                        if (field.text.isEmpty()) {
                            Text("Tìm kiếm hoặc dán link YouTube", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        BasicTextField(
                            value = field,
                            onValueChange = { field = it; vm.updateSearchText(it.text) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { vm.submitSearch(field.text) }),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                    if (field.text.isNotEmpty()) {
                        IconButton(onClick = { field = TextFieldValue(""); vm.updateSearchText("") }, Modifier.size(36.dp)) {
                            Icon(AppIcons.Close, "Xoá", Modifier.size(20.dp))
                        }
                    }
                }
                IconButton(onClick = { vm.submitSearch(field.text) }) { Icon(AppIcons.Search, "Tìm") }
            }
            val text = field.text.trim()
            val historyMatches = vm.searchHistory.filter { text.isEmpty() || it.contains(text, ignoreCase = true) }.take(if (text.isEmpty()) 20 else 3)
            val suggestions = vm.suggestions.filter { s -> historyMatches.none { it.equals(s, true) } }
            LazyColumn(Modifier.weight(1f)) {
                items(historyMatches, key = { "h$it" }) { q ->
                    SuggestionRow(q, AppIcons.History, onClick = { vm.submitSearch(q) }, onFill = {
                        field = TextFieldValue("$q ", TextRange(q.length + 1)); vm.updateSearchText(q)
                    }, onRemove = { vm.removeSearchHistory(q) })
                }
                items(suggestions, key = { "s$it" }) { q ->
                    SuggestionRow(q, AppIcons.Search, onClick = { vm.submitSearch(q) }, onFill = {
                        field = TextFieldValue("$q ", TextRange(q.length + 1)); vm.updateSearchText(q)
                    })
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    onFill: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(20.dp))
        Text(text, Modifier.weight(1f).padding(vertical = 14.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (onRemove != null) {
            IconButton(onClick = onRemove) { Icon(AppIcons.Close, "Xoá khỏi lịch sử", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        IconButton(onClick = onFill) { Icon(AppIcons.NorthWest, "Điền vào ô tìm kiếm", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

