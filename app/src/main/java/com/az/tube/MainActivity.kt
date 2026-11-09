package com.az.tube

import android.Manifest
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller by mutableStateOf<MediaController?>(null)
    private var inPip by mutableStateOf(false)

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            AppTheme {
                // Android 13+: no preview of the app in Recents while the password lock is on.
                LaunchedEffect(AppLock.enabled) {
                    if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(!AppLock.enabled)
                }
                LaunchedEffect(AppLock.notice) {
                    AppLock.notice?.let { vm.message = it; AppLock.notice = null }
                }
                Box {
                    // While locked the app stays underneath (keeps its place) but is hidden from screen readers.
                    Box(if (AppLock.locked) Modifier.clearAndSetSemantics { } else Modifier) {
                        AzApp(vm, controller, inPip)
                    }
                    if (AppLock.locked) LockScreen()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** "Share" from the YouTube app or a browser opens the link here. */
    private fun handleShare(intent: Intent?) {
        val text = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        } ?: return
        val url = Regex("https?://\\S+").find(text)?.value
        if (url != null) vm.openLink(url) else vm.submitSearch(text)
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            if (controllerFuture === future && !future.isCancelled) controller = future.get()
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onResume() {
        super.onResume()
        AppLock.skipNextLock = false
    }

    override fun onStop() {
        AppLock.onLeave(isChangingConfigurations)
        // Screen off or app in the background: stop downloading video, keep the sound.
        controller?.let { setVideoEnabled(it, false) }
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }

    /** Pressing Home while watching a video shrinks it into a floating window. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val c = controller ?: return
        if (vm.watchExpanded && !vm.audioOnly && c.isPlaying) {
            try {
                enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
            } catch (_: Exception) {
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        // Back from the floating window to full screen: ask for the password again.
        if (inPip && !isInPictureInPictureMode) AppLock.onPipClosed()
        inPip = isInPictureInPictureMode
    }
}
