package com.az.tube

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.Player

fun setVideoEnabled(player: Player, enabled: Boolean) {
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !enabled)
        .build()
}

@Composable
fun AzApp(vm: AppViewModel, player: Player?, inPip: Boolean) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val dark = isSystemInDarkTheme()
    val state = remember(player) { player?.let { PlayerState(it) } }
    DisposableEffect(state) { onDispose { state?.dispose() } }
    val current = state?.current
    var menuFor by remember { mutableStateOf<VideoItem?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showQuality by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    val actions = remember(player) {
        fun withPlayer(block: (Player) -> Unit) {
            if (player == null) vm.message = "Đang kết nối trình phát, thử lại sau giây lát" else block(player)
        }
        UiActions(
            play = { v ->
                withPlayer { p ->
                    p.setMediaItem(v.toMediaItem())
                    p.prepare()
                    p.play()
                    vm.watchExpanded = true
                }
            },
            playList = { list, index ->
                withPlayer { p ->
                    p.setMediaItems(list.map { it.toMediaItem() }, index.coerceAtLeast(0), 0L)
                    p.prepare()
                    p.play()
                    vm.watchExpanded = true
                }
            },
            playNext = { v ->
                withPlayer { p ->
                    if (p.mediaItemCount == 0) {
                        p.setMediaItem(v.toMediaItem()); p.prepare(); p.play()
                    } else {
                        p.addMediaItem(p.currentMediaItemIndex + 1, v.toMediaItem())
                    }
                    vm.message = "Sẽ phát tiếp theo"
                }
            },
            enqueue = { v ->
                withPlayer { p ->
                    val wasEnded = p.playbackState == Player.STATE_ENDED
                    val wasEmpty = p.mediaItemCount == 0
                    p.addMediaItem(v.toMediaItem())
                    if (wasEmpty || wasEnded) {
                        if (wasEnded) p.seekToDefaultPosition(p.mediaItemCount - 1)
                        p.prepare(); p.play()
                    }
                    vm.message = "Đã thêm vào hàng chờ"
                }
            },
            openChannel = { url, name -> vm.watchExpanded = false; vm.open(Route.Channel(url, name)) },
            openPlaylist = { url, name -> vm.watchExpanded = false; vm.open(Route.Playlist(url, name)) },
            share = { url ->
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
                context.startActivity(Intent.createChooser(send, "Chia sẻ"))
            },
            showMenu = { menuFor = it },
            isSubscribed = { vm.isSubscribed(it) },
            toggleSubscribe = { vm.toggleSubscribe(it) },
        )
    }

    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }
    // "Audio only" stops the video download right away.
    LaunchedEffect(player, vm.audioOnly) { player?.let { setVideoEnabled(it, !vm.audioOnly) } }
    // Load the title, channel and description.
    LaunchedEffect(current?.mediaId) {
        current?.let {
            vm.recordWatched(it.toVideoItem()) // does nothing unless watch history is on
            vm.loadDetails(it.mediaId)
        }
    }
    LaunchedEffect(current == null) { if (current == null) vm.watchExpanded = false }
    // A link shared from another app.
    LaunchedEffect(vm.playRequest, player) {
        val r = vm.playRequest
        if (r != null && player != null) {
            actions.play(r)
            vm.playRequest = null
        }
    }

    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val fullscreen = vm.watchExpanded && state != null && current != null && landscape
    fun toggleFullscreen() {
        activity?.requestedOrientation = if (fullscreen) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }
    LaunchedEffect(vm.watchExpanded) {
        if (!vm.watchExpanded) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    // System bars: hidden in fullscreen, white icons over the black video area.
    val window = activity?.window
    DisposableEffect(fullscreen, vm.watchExpanded, dark, window) {
        if (window != null) {
            val c = WindowCompat.getInsetsController(window, window.decorView)
            if (fullscreen) {
                c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                c.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                c.show(WindowInsetsCompat.Type.systemBars())
            }
            c.isAppearanceLightStatusBars = !dark && !vm.watchExpanded
        }
        onDispose { }
    }

    fun applyQuality(q: Int) {
        showQuality = false
        vm.maxQuality = q
        Store.maxQuality = q
        val p = player ?: return
        val item = p.currentMediaItem ?: return
        val i = p.currentMediaItemIndex
        val pos = p.currentPosition
        p.replaceMediaItem(i, item)
        p.seekTo(i, pos)
    }

    CompositionLocalProvider(LocalActions provides actions) {
        when {
            inPip && state != null -> VideoSurface(
                state, vm.audioOnly, fullscreen = true, showControls = false,
                onToggleFullscreen = {}, onCollapse = null, onSettings = {},
                modifier = Modifier.fillMaxSize(),
            )

            fullscreen -> {
                BackHandler { toggleFullscreen() }
                VideoSurface(
                    state!!, vm.audioOnly, fullscreen = true, showControls = true,
                    onToggleFullscreen = { toggleFullscreen() }, onCollapse = null,
                    onSettings = { showSettings = true },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            else -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    snackbarHost = { SnackbarHost(snackbar) },
                    bottomBar = {
                        Column {
                            if (state != null && current != null && !vm.watchExpanded) {
                                MiniPlayer(
                                    state,
                                    onExpand = { vm.watchExpanded = true },
                                    onClose = { player?.stop(); player?.clearMediaItems() },
                                )
                            }
                            BottomNav(vm)
                        }
                    },
                ) { padding ->
                    Box(Modifier.padding(padding)) {
                        when (val r = vm.route) {
                            Route.Home -> HomeScreen(vm)
                            Route.Subscriptions -> SubscriptionsScreen(vm)
                            Route.Library -> LibraryScreen(vm, onQuality = { showQuality = true })
                            is Route.Search -> SearchResultsScreen(vm, r)
                            is Route.Channel -> ChannelScreen(vm, r)
                            is Route.Playlist -> PlaylistScreen(vm, r)
                        }
                    }
                }
                AnimatedVisibility(
                    vm.watchExpanded && state != null && current != null,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    if (state != null) {
                        WatchScreen(
                            vm, state,
                            onCollapse = { vm.watchExpanded = false },
                            onFullscreen = { toggleFullscreen() },
                            onSettings = { showSettings = true },
                        )
                    }
                }
                if (vm.searchOpen) SearchOverlay(vm)
            }
        }

        BackHandler(enabled = !fullscreen && (vm.searchOpen || vm.watchExpanded || vm.stack.size > 1 || vm.route != Route.Home)) {
            when {
                vm.searchOpen -> vm.searchOpen = false
                vm.watchExpanded -> vm.watchExpanded = false
                else -> vm.back()
            }
        }

        menuFor?.let { VideoMenuSheet(it) { menuFor = null } }
        if (showSettings && state != null) {
            PlayerSettingsSheet(vm, state, onQuality = { showQuality = true }) { showSettings = false }
        }
        if (showQuality) QualitySheet(vm.maxQuality, ::applyQuality) { showQuality = false }
    }
}

@Composable
private fun BottomNav(vm: AppViewModel) {
    // Without recommendations, Home already shows your subscriptions, so that tab is hidden.
    val tabs = listOfNotNull(
        Triple(Route.Home, "Trang chủ", AppIcons.Home),
        Triple(Route.Subscriptions, "Kênh đăng ký", AppIcons.Subscriptions).takeIf { vm.recommendationsOn },
        Triple(Route.Library, "Bạn", AppIcons.Library),
    )
    val fg = MaterialTheme.colorScheme.onSurface
    NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
        tabs.forEach { (route, label, icon) ->
            NavigationBarItem(
                selected = vm.stack.first() == route,
                onClick = {
                    vm.watchExpanded = false
                    vm.selectTab(route)
                },
                icon = { Icon(icon, label) },
                label = { Text(label, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = Color.Transparent,
                    selectedIconColor = fg,
                    selectedTextColor = fg,
                    unselectedIconColor = fg.copy(alpha = 0.6f),
                    unselectedTextColor = fg.copy(alpha = 0.6f),
                ),
            )
        }
    }
}
