package com.az.tube

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * In-app updates from GitHub Releases (the same files as the website's download button).
 *
 * Checks the latest release, downloads the APK, checks its SHA-256 against the .sha256 file published
 * with it and that it is a newer AZ Tube, then hands it to Android's installer. Android itself refuses
 * an update signed with a different key, and always asks the user to confirm.
 */
object Updater {
    private const val LATEST = "https://api.github.com/repos/diennguyendhv/aztube-releases/releases/latest"

    data class Release(val version: String, val notes: String, val apkUrl: String, val shaUrl: String?, val size: Long)

    /** A newer release than the installed one, once a check found it. */
    var available by mutableStateOf<Release?>(null)
        private set

    /** Show the "new version" dialog. */
    var prompt by mutableStateOf(false)

    var checking by mutableStateOf(false)
        private set

    /** Download progress 0..1 while downloading, null otherwise. */
    var progress by mutableStateOf<Float?>(null)
        private set

    /** Waiting for Android's installer. */
    var installing by mutableStateOf(false)
        private set

    fun currentVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    } catch (_: Exception) {
        ""
    }

    /** "1.10" > "1.9". */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.trimStart('v', 'V').split('.').map { it.toIntOrNull() ?: 0 }
        val b = current.trimStart('v', 'V').split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Checks once at startup when allowed and not done in the last 12 hours. */
    suspend fun autoCheck(context: Context) {
        if (!Store.autoUpdateCheck) return
        val now = System.currentTimeMillis()
        if (now - Store.lastUpdateCheck < 12 * 60 * 60_000L) {
            return
        }
        val r = runCatching { check(context) }.getOrNull() ?: return
        if (r.version != Store.skippedUpdate) prompt = true
    }

    /** Asks GitHub for the latest release. Returns it when it is newer than this app, else null. */
    suspend fun check(context: Context): Release? = withContext(Dispatchers.IO) {
        checking = true
        try {
            val req = Request.Builder().url(LATEST)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "AZTube/${currentVersion(context)}")
                .build()
            val json = Net.client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) throw IllegalStateException("GitHub trả lỗi ${res.code}")
                JSONObject(res.body.string())
            }
            Store.lastUpdateCheck = System.currentTimeMillis()
            val version = json.optString("tag_name").trimStart('v', 'V')
            val assets = json.optJSONArray("assets")
            var apk: JSONObject? = null
            var sha: String? = null
            for (i in 0 until (assets?.length() ?: 0)) {
                val a = assets!!.getJSONObject(i)
                val name = a.optString("name")
                when {
                    name.endsWith(".apk", true) -> apk = a
                    name.endsWith(".apk.sha256", true) -> sha = a.optString("browser_download_url")
                }
            }
            val release = if (apk != null && version.isNotEmpty() && isNewer(version, currentVersion(context))) {
                Release(version, cleanNotes(json.optString("body")), apk.optString("browser_download_url"), sha, apk.optLong("size"))
            } else {
                null
            }
            available = release
            release
        } finally {
            checking = false
        }
    }

    /** Release notes are Markdown on GitHub: keep the "what's new" part as plain text. */
    private fun cleanNotes(body: String): String = body.lines()
        .takeWhile { !it.trim().startsWith("**Thông tin") }
        .filterNot { it.trim().startsWith("**AZ Tube") }
        .joinToString("\n") { it.replace("**", "").replace("`", "").trimEnd() }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Main)
    private var job: kotlinx.coroutines.Job? = null

    /** Starts the download in the background (keeps going if the dialog closes). */
    fun start(context: Context, release: Release) {
        if (job?.isActive == true) return
        val app = context.applicationContext
        job = scope.launch {
            try {
                downloadAndInstall(app, release)
            } catch (_: kotlinx.coroutines.CancellationException) {
                progress = null
            } catch (e: Exception) {
                AppLock.notice = e.message ?: "Cập nhật không thành công. Kiểm tra mạng rồi thử lại"
            }
        }
    }

    fun cancel() {
        job?.cancel()
        progress = null
    }

    /** Downloads, verifies and opens Android's installer. Throws with a message the user can read. */
    suspend fun downloadAndInstall(context: Context, release: Release) = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "AZTube-${release.version}.apk")
        progress = 0f
        try {
            val expected = release.shaUrl?.let { url ->
                Net.client.newCall(Request.Builder().url(url).build()).execute().use { res ->
                    if (!res.isSuccessful) throw IllegalStateException("Không tải được mã kiểm tra (lỗi ${res.code})")
                    res.body.string().trim().split(Regex("\\s+")).first().lowercase()
                }
            } ?: throw IllegalStateException("Bản phát hành thiếu file mã kiểm tra")

            val digest = MessageDigest.getInstance("SHA-256")
            Net.client.newCall(Request.Builder().url(release.apkUrl).build()).execute().use { res ->
                if (!res.isSuccessful) throw IllegalStateException("Không tải được bản cập nhật (lỗi ${res.code})")
                val total = res.body.contentLength().takeIf { it > 0 } ?: release.size
                res.body.byteStream().use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            done += n
                            if (total > 0) progress = (done.toFloat() / total).coerceIn(0f, 1f)
                        }
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (actual != expected) throw IllegalStateException("File tải về bị lỗi (mã kiểm tra không khớp). Thử lại sau")

            val info = context.packageManager.getPackageArchiveInfo(file.path, 0)
                ?: throw IllegalStateException("File tải về không phải ứng dụng Android hợp lệ")
            if (info.packageName != context.packageName) throw IllegalStateException("File tải về không phải AZ Tube")
            if (!isNewer(info.versionName.orEmpty(), currentVersion(context))) throw IllegalStateException("Bạn đang dùng bản mới nhất")

            progress = null
            installing = true
            install(context, file)
        } catch (e: Throwable) {
            file.delete()
            progress = null
            installing = false
            throw e
        }
    }

    private fun install(context: Context, file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            file.inputStream().use { input ->
                session.openWrite("AZTube.apk", 0, file.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val callback = PendingIntent.getBroadcast(context, id, Intent(context, UpdateReceiver::class.java), flags)
            session.commit(callback.intentSender)
        }
    }

    /** Called by [UpdateReceiver] when the installer finished without installing. */
    fun onInstallEnded(message: String?) {
        installing = false
        if (message != null) AppLock.notice = message
    }
}

/** Receives the installer's answer: shows Android's confirmation, or reports a failure. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // Android's confirmation screen is part of updating, not leaving the app.
                AppLock.skipNextLock = true
                runCatching { context.startActivity(confirm) }
                    .onFailure { Updater.onInstallEnded("Không mở được trình cài đặt. Tải bản mới trên trang web") }
            }
            PackageInstaller.STATUS_SUCCESS -> Updater.onInstallEnded(null)
            PackageInstaller.STATUS_FAILURE_ABORTED -> Updater.onInstallEnded(null)
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            -> Updater.onInstallEnded("Không cập nhật được: bản tải về không khớp với bản đang cài. Gỡ app rồi cài lại từ trang web")
            PackageInstaller.STATUS_FAILURE_STORAGE -> Updater.onInstallEnded("Không đủ bộ nhớ để cập nhật")
            else -> Updater.onInstallEnded("Cập nhật không thành công. Thử lại hoặc tải trên trang web")
        }
    }
}
