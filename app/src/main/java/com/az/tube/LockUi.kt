package com.az.tube

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Full-screen lock shown over the app until the right password is entered. */
@Composable
fun LockScreen() {
    val activity = LocalActivity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var showForgot by remember { mutableStateOf(false) }
    var wait by remember { mutableLongStateOf(AppLock.secondsToWait()) }
    val focus = remember { FocusRequester() }

    // Back on the lock screen leaves the app instead of going "behind" the lock.
    BackHandler { activity?.moveTaskToBack(true) }

    LaunchedEffect(AppLock.waitUntil) {
        while (true) {
            wait = AppLock.secondsToWait()
            if (wait == 0L) break
            delay(1000)
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val confirmPhoneLock = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            AppLock.disable()
            AppLock.notice = "Đã tắt khóa ứng dụng. Đặt mật khẩu mới trong tab Bạn nếu muốn"
        }
    }

    fun submit() {
        if (checking || wait > 0 || password.isEmpty()) return
        checking = true
        error = null
        scope.launch {
            val ok = withContext(Dispatchers.Default) { AppLock.tryUnlock(password) }
            checking = false
            if (!ok) {
                password = ""
                error = if (AppLock.secondsToWait() > 0) null else "Sai mật khẩu"
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            LogoMark(72.dp)
            Spacer(Modifier.height(20.dp))
            Text("AZ Tube đang khóa", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Nhập mật khẩu để mở", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            PasswordField(
                value = password,
                onChange = { password = it; error = null },
                label = "Mật khẩu",
                modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth().focusRequester(focus),
                enabled = wait == 0L && !checking,
                imeAction = ImeAction.Done,
                onDone = ::submit,
            )
            val note = when {
                wait > 0 -> "Nhập sai quá nhiều lần. Thử lại sau $wait giây"
                error != null -> error
                else -> null
            }
            Text(
                note.orEmpty(),
                Modifier.padding(top = 8.dp).height(40.dp),
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = ::submit,
                enabled = wait == 0L && !checking && password.isNotEmpty(),
                modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Brand, contentColor = androidx.compose.ui.graphics.Color.White),
            ) {
                if (checking) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = androidx.compose.ui.graphics.Color.White)
                else Text("Mở khóa", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { showForgot = true }) { Text("Quên mật khẩu?", color = MaterialTheme.colorScheme.secondary) }
        }
    }

    if (showForgot) {
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (keyguard.isDeviceSecure) {
            AlertDialog(
                onDismissRequest = { showForgot = false },
                title = { Text("Quên mật khẩu") },
                text = { Text("Xác minh bằng khóa màn hình của điện thoại (mã PIN, hình vẽ, vân tay…). Sau đó khóa của AZ Tube sẽ tắt, kênh đăng ký và lịch sử vẫn giữ nguyên. Bạn có thể đặt mật khẩu mới trong tab Bạn.") },
                confirmButton = {
                    TextButton(onClick = {
                        showForgot = false
                        @Suppress("DEPRECATION")
                        val intent = keyguard.createConfirmDeviceCredentialIntent("Mở khóa AZ Tube", "Xác minh để tắt khóa của AZ Tube")
                        if (intent != null) {
                            AppLock.skipNextLock = true
                            confirmPhoneLock.launch(intent)
                        }
                    }) { Text("Xác minh") }
                },
                dismissButton = { TextButton(onClick = { showForgot = false }) { Text("Hủy") } },
            )
        } else {
            AlertDialog(
                onDismissRequest = { showForgot = false },
                title = { Text("Quên mật khẩu") },
                text = { Text("Điện thoại chưa đặt khóa màn hình nên không thể xác minh bạn là chủ máy. Cách duy nhất là gỡ AZ Tube rồi cài lại. Kênh đăng ký, lịch sử và cài đặt sẽ bị xóa.") },
                confirmButton = { TextButton(onClick = { showForgot = false }) { Text("Đã hiểu") } },
            )
        }
    }
}

/** What the password dialog in Settings is for. */
enum class LockDialog { Enable, Change, Disable }

/** Set, change or remove the password, from the Settings screen. */
@Composable
fun LockSettingsDialog(mode: LockDialog, onResult: (String) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val needsCurrent = mode != LockDialog.Enable
    val needsNew = mode != LockDialog.Disable

    fun submit() {
        if (busy) return
        if (needsCurrent && current.isEmpty()) { error = "Nhập mật khẩu hiện tại"; return }
        if (needsNew) {
            if (new.length < AppLock.MIN_LENGTH) { error = "Mật khẩu cần ít nhất ${AppLock.MIN_LENGTH} ký tự"; return }
            if (new != again) { error = "Hai lần nhập mật khẩu mới không giống nhau"; return }
        }
        busy = true
        error = null
        scope.launch {
            val okCurrent = !needsCurrent || withContext(Dispatchers.Default) { AppLock.matches(current) }
            if (!okCurrent) {
                busy = false
                current = ""
                error = "Mật khẩu hiện tại không đúng"
                return@launch
            }
            when (mode) {
                LockDialog.Enable -> withContext(Dispatchers.Default) { AppLock.setPassword(new) }.also { onResult("Đã bật khóa. AZ Tube sẽ hỏi mật khẩu mỗi khi mở") }
                LockDialog.Change -> withContext(Dispatchers.Default) { AppLock.setPassword(new) }.also { onResult("Đã đổi mật khẩu") }
                LockDialog.Disable -> AppLock.disable().also { onResult("Đã tắt khóa ứng dụng") }
            }
            busy = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Text(
                when (mode) {
                    LockDialog.Enable -> "Đặt mật khẩu"
                    LockDialog.Change -> "Đổi mật khẩu"
                    LockDialog.Disable -> "Tắt khóa ứng dụng"
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (needsNew) {
                    Text(
                        (if (mode == LockDialog.Enable) "AZ Tube sẽ hỏi mật khẩu mỗi khi mở hoặc quay lại app. Nhạc đang phát nền vẫn tiếp tục. " else "") +
                            "Mật khẩu cần ít nhất ${AppLock.MIN_LENGTH} ký tự, chữ hoặc số đều được.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (needsCurrent) {
                    PasswordField(current, { current = it; error = null }, "Mật khẩu hiện tại", Modifier.fillMaxWidth(), !busy,
                        if (needsNew) ImeAction.Next else ImeAction.Done, ::submit)
                }
                if (needsNew) {
                    PasswordField(new, { new = it; error = null }, "Mật khẩu mới", Modifier.fillMaxWidth(), !busy, ImeAction.Next, ::submit)
                    PasswordField(again, { again = it; error = null }, "Nhập lại mật khẩu mới", Modifier.fillMaxWidth(), !busy, ImeAction.Done, ::submit)
                }
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = ::submit, enabled = !busy) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(if (mode == LockDialog.Disable) "Tắt khóa" else "Lưu")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Hủy") } },
    )
}

@Composable
private fun PasswordField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier,
    enabled: Boolean,
    imeAction: ImeAction,
    onDone: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(64)) },
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        modifier = modifier,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        trailingIcon = {
            TextButton(onClick = { visible = !visible }) { Text(if (visible) "Ẩn" else "Hiện") }
        },
    )
}
