package com.az.tube

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** "New version" dialog and the download progress, shown over the app (never over the lock screen). */
@Composable
fun UpdateDialogs() {
    val context = LocalContext.current
    val release = Updater.available
    val progress = Updater.progress

    if (progress != null || Updater.installing) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Đang cập nhật AZ Tube ${release?.version.orEmpty()}") },
            text = {
                Column {
                    if (progress != null) {
                        Text("Đang tải bản mới… ${(progress * 100).toInt()}%")
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth(), color = Brand)
                    } else {
                        Text("Bấm \"Cập nhật\" trên màn hình của Android để cài. Kênh đăng ký, lịch sử và cài đặt được giữ nguyên.")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { if (progress != null) Updater.cancel() else Updater.onInstallEnded(null) }) {
                    Text(if (progress != null) "Hủy" else "Đóng")
                }
            },
        )
        return
    }

    if (Updater.prompt && release != null) {
        AlertDialog(
            onDismissRequest = { Updater.prompt = false },
            title = { Text("Có bản mới: AZ Tube ${release.version}") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        "Bạn đang dùng bản ${Updater.currentVersion(context)}. Cập nhật mất khoảng " +
                            "${(release.size / 1_000_000L).coerceAtLeast(1)} MB, giữ nguyên kênh đăng ký và lịch sử.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (release.notes.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(release.notes, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    Updater.prompt = false
                    Updater.start(context, release)
                }) { Text("Cập nhật", fontWeight = FontWeight.Bold, color = Brand) }
            },
            dismissButton = {
                TextButton(onClick = {
                    Updater.prompt = false
                    Store.skippedUpdate = release.version
                }) { Text("Để sau") }
            },
        )
    }
}

/** The "Cập nhật" rows in the Bạn tab. */
@Composable
fun UpdateSettings(onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val current = Updater.currentVersion(context)
    val release = Updater.available
    SettingLine(
        if (release != null) "Cập nhật lên bản ${release.version}" else "Kiểm tra bản mới",
        when {
            Updater.checking -> "Đang kiểm tra…"
            Updater.progress != null -> "Đang tải…"
            else -> "Đang dùng $current"
        },
    ) {
        if (Updater.checking || Updater.progress != null) return@SettingLine
        if (release != null) {
            Updater.prompt = true
        } else {
            scope.launch {
                val found = runCatching { Updater.check(context) }
                found.onSuccess { r ->
                    if (r != null) Updater.prompt = true else onMessage("Bạn đang dùng bản mới nhất ($current)")
                }.onFailure { onMessage("Không kiểm tra được. Kiểm tra kết nối mạng rồi thử lại") }
            }
        }
    }
    var auto by remember { mutableStateOf(Store.autoUpdateCheck) }
    SwitchLine("Tự kiểm tra bản mới", "Khi mở app, hỏi GitHub xem có bản mới không (tối đa 2 lần mỗi ngày)", auto) {
        Store.autoUpdateCheck = it
        auto = it
    }
}
