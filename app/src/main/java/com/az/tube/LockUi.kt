package com.az.tube

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Full-screen lock shown over the app until the right PIN is entered. */
@Composable
fun LockScreen() {
    val activity = LocalActivity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var entry by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var showForgot by remember { mutableStateOf(false) }
    var wait by remember { mutableLongStateOf(AppLock.secondsToWait()) }
    val pinLength = AppLock.pinLength

    // Back on the lock screen leaves the app instead of going "behind" the lock.
    BackHandler { activity?.moveTaskToBack(true) }

    LaunchedEffect(AppLock.waitUntil) {
        while (true) {
            wait = AppLock.secondsToWait()
            if (wait == 0L) break
            delay(1000)
        }
    }

    val confirmPhoneLock = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            AppLock.disable()
            AppLock.notice = "Đã tắt khóa ứng dụng. Đặt mã PIN mới trong tab Bạn nếu muốn"
        }
    }

    fun submit(value: String) {
        if (checking || wait > 0 || value.isEmpty()) return
        checking = true
        error = null
        scope.launch {
            val ok = withContext(Dispatchers.Default) { AppLock.tryUnlock(value) }
            checking = false
            if (!ok) {
                entry = ""
                error = if (AppLock.secondsToWait() > 0) null else "Sai mã PIN"
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            LogoMark(56.dp)
            Spacer(Modifier.height(12.dp))
            Text("AZ Tube đang khóa", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                if (pinLength > 0) "Nhập mã PIN để mở" else "Nhập mật khẩu để mở",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            val enabled = wait == 0L && !checking
            if (pinLength > 0) {
                PinDots(entry.length, pinLength)
            } else {
                // A text password from version 1.2: keep accepting it until the user sets a PIN.
                LegacyPasswordField(entry, { entry = it; error = null }, enabled) { submit(entry) }
            }
            val note = when {
                wait > 0 -> "Nhập sai quá nhiều lần. Thử lại sau $wait giây"
                checking -> "Đang kiểm tra…"
                else -> error
            }
            Text(
                note.orEmpty(),
                Modifier.padding(top = 12.dp).height(44.dp),
                color = if (checking) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            if (pinLength > 0) {
                PinPad(
                    enabled = enabled,
                    onDigit = { d ->
                        if (entry.length < pinLength) {
                            entry += d
                            error = null
                            if (entry.length == pinLength) submit(entry)
                        }
                    },
                    onDelete = { entry = entry.dropLast(1) },
                    okEnabled = false,
                    onOk = {},
                )
            } else {
                Button(
                    onClick = { submit(entry) },
                    enabled = enabled && entry.isNotEmpty(),
                    modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Brand, contentColor = Color.White),
                ) { Text("Mở khóa", fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { showForgot = true }) {
                Text(if (pinLength > 0) "Quên mã PIN?" else "Quên mật khẩu?", color = MaterialTheme.colorScheme.secondary)
            }
        }
    }

    if (showForgot) {
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (keyguard.isDeviceSecure) {
            AlertDialog(
                onDismissRequest = { showForgot = false },
                title = { Text("Quên mã PIN") },
                text = { Text("Xác minh bằng khóa màn hình của điện thoại (mã PIN, hình vẽ, vân tay…). Sau đó khóa của AZ Tube sẽ tắt, kênh đăng ký và lịch sử vẫn giữ nguyên. Bạn có thể đặt mã mới trong tab Bạn.") },
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
                title = { Text("Quên mã PIN") },
                text = { Text("Điện thoại chưa đặt khóa màn hình nên không thể xác minh bạn là chủ máy. Cách duy nhất là gỡ AZ Tube rồi cài lại. Kênh đăng ký, lịch sử và cài đặt sẽ bị xóa.") },
                confirmButton = { TextButton(onClick = { showForgot = false }) { Text("Đã hiểu") } },
            )
        }
    }
}

/** What the PIN screen in Settings is for. */
enum class LockDialog { Enable, Change, Disable }

private enum class Step { Current, New, Confirm }

/** Set, change or remove the PIN from the Settings screen, on the same big keypad as the lock screen. */
@Composable
fun LockSettingsDialog(mode: LockDialog, onResult: (String) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(if (mode == LockDialog.Enable) Step.New else Step.Current) }
    var entry by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val currentIsPin = AppLock.pinLength > 0

    // This dialog is its own window and would sit above the lock screen, so close it when the app locks.
    if (AppLock.locked) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    fun finish(pin: String?) {
        busy = true
        scope.launch {
            when (mode) {
                LockDialog.Enable -> withContext(Dispatchers.Default) { AppLock.setPassword(pin!!) }
                    .also { onResult("Đã bật khóa. AZ Tube sẽ hỏi mã PIN mỗi khi mở") }
                LockDialog.Change -> withContext(Dispatchers.Default) { AppLock.setPassword(pin!!) }
                    .also { onResult("Đã đổi mã PIN") }
                LockDialog.Disable -> AppLock.disable().also { onResult("Đã tắt khóa ứng dụng") }
            }
            busy = false
        }
    }

    fun submit() {
        if (busy) return
        when (step) {
            Step.Current -> {
                if (entry.isEmpty()) return
                val typed = entry
                busy = true
                scope.launch {
                    val ok = withContext(Dispatchers.Default) { AppLock.matches(typed) }
                    busy = false
                    entry = ""
                    if (!ok) {
                        error = if (currentIsPin) "Mã PIN hiện tại không đúng" else "Mật khẩu hiện tại không đúng"
                    } else if (mode == LockDialog.Disable) {
                        finish(null)
                    } else {
                        error = null
                        step = Step.New
                    }
                }
            }
            Step.New -> {
                if (entry.length < AppLock.MIN_LENGTH) {
                    error = "Mã PIN cần ít nhất ${AppLock.MIN_LENGTH} số"
                    return
                }
                firstPin = entry
                entry = ""
                error = null
                step = Step.Confirm
            }
            Step.Confirm -> {
                if (entry == firstPin) {
                    finish(entry)
                } else {
                    error = "Hai lần nhập không giống nhau. Nhập lại mã mới"
                    entry = ""
                    firstPin = ""
                    step = Step.New
                }
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    when (mode) {
                        LockDialog.Enable -> "Đặt mã PIN"
                        LockDialog.Change -> "Đổi mã PIN"
                        LockDialog.Disable -> "Tắt khóa ứng dụng"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    when (step) {
                        Step.Current -> if (currentIsPin) "Nhập mã PIN hiện tại" else "Nhập mật khẩu hiện tại"
                        Step.New -> "Nhập mã PIN mới (${AppLock.MIN_LENGTH} đến ${AppLock.MAX_PIN} số), rồi bấm ✓"
                        Step.Confirm -> "Nhập lại mã PIN mới"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (mode == LockDialog.Enable && step == Step.New) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "AZ Tube sẽ hỏi mã mỗi khi mở hoặc quay lại app. Nhạc đang phát nền vẫn tiếp tục.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(20.dp))
                val legacy = step == Step.Current && !currentIsPin
                if (legacy) {
                    LegacyPasswordField(entry, { entry = it; error = null }, !busy, ::submit)
                } else {
                    val target = when (step) {
                        Step.Current -> AppLock.pinLength
                        Step.Confirm -> firstPin.length
                        Step.New -> 0
                    }
                    PinDots(entry.length, target)
                }
                Text(
                    error.orEmpty(),
                    Modifier.padding(top = 12.dp).height(44.dp),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                if (legacy) {
                    Button(
                        onClick = ::submit,
                        enabled = !busy && entry.isNotEmpty(),
                        modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Brand, contentColor = Color.White),
                    ) { Text("Tiếp tục", fontWeight = FontWeight.Bold) }
                } else {
                    // Current PIN and the confirmation have a known length, so they check themselves when complete.
                    val autoLength = when (step) {
                        Step.Current -> AppLock.pinLength
                        Step.Confirm -> firstPin.length
                        Step.New -> 0
                    }
                    PinPad(
                        enabled = !busy,
                        onDigit = { d ->
                            val max = if (autoLength > 0) autoLength else AppLock.MAX_PIN
                            if (entry.length < max) {
                                entry += d
                                error = null
                                if (autoLength > 0 && entry.length == autoLength) submit()
                            }
                        },
                        onDelete = { entry = entry.dropLast(1) },
                        okEnabled = step == Step.New && entry.length >= AppLock.MIN_LENGTH,
                        onOk = ::submit,
                    )
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, enabled = !busy) { Text("Hủy", fontSize = 16.sp) }
            }
        }
    }
}

/** One dot per digit typed; [total] empty dots show how many are expected (0 = unknown length). */
@Composable
private fun PinDots(typed: Int, total: Int) {
    val count = maxOf(typed, total, AppLock.MIN_LENGTH)
    Row(
        Modifier.height(24.dp).semantics { contentDescription = "Đã nhập $typed số" },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val filled = i < typed
            Box(
                Modifier.size(16.dp).clip(CircleShape)
                    .background(if (filled) Brand else Color.Transparent)
                    .border(2.dp, if (filled) Brand else MaterialTheme.colorScheme.outline, CircleShape),
            )
        }
    }
}

/** Big phone-style number pad: 1–9, then delete, 0, OK. */
@Composable
private fun PinPad(
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    okEnabled: Boolean,
    onOk: () -> Unit,
) {
    val rows = listOf("123", "456", "789")
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { d -> PadKey(d.toString(), enabled, "Số $d") { onDigit(d) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            PadKey("⌫", enabled, "Xóa", plain = true, onClick = onDelete)
            PadKey("0", enabled, "Số 0") { onDigit('0') }
            if (okEnabled) PadKey("✓", true, "Xong", accent = true, onClick = onOk)
            else Spacer(Modifier.size(76.dp))
        }
    }
}

@Composable
private fun PadKey(
    label: String,
    enabled: Boolean,
    description: String,
    plain: Boolean = false,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    val bg = when {
        accent -> Brand
        plain -> Color.Transparent
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Box(
        Modifier.size(76.dp).clip(CircleShape).background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = if (plain) 26.sp else 32.sp,
            fontWeight = FontWeight.Medium,
            color = when {
                accent -> Color.White
                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/** Text password field, only for a password set in version 1.2 before PINs. */
@Composable
private fun LegacyPasswordField(value: String, onChange: (String) -> Unit, enabled: Boolean, onDone: () -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(64)) },
        label = { Text("Mật khẩu") },
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
    )
}
