package com.az.tube

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Optional password lock for the whole app.
 *
 * Only a salted PBKDF2-SHA256 hash of the password is stored. The app locks when it starts and
 * whenever the screen is left (Home, screen off). Background playback keeps going while locked.
 */
object AppLock {
    const val MIN_LENGTH = 4
    private const val ITERATIONS = 120_000
    private const val FREE_TRIES = 5

    private lateinit var sp: SharedPreferences

    /** A password is set. */
    var enabled by mutableStateOf(false)
        private set

    /** The lock screen is showing. */
    var locked by mutableStateOf(false)
        private set

    /** Wrong passwords in a row, and when the next try is allowed (elapsedRealtime). */
    var failures by mutableIntStateOf(0)
        private set
    var waitUntil by mutableLongStateOf(0L)
        private set

    /** A short message for the app to show once (e.g. after unlocking with the phone's screen lock). */
    var notice by mutableStateOf<String?>(null)

    /** Set right before opening something of our own (share sheet, device credential) so leaving for it doesn't lock. */
    var skipNextLock = false

    fun init(context: Context) {
        sp = context.getSharedPreferences("app_lock", Context.MODE_PRIVATE)
        enabled = sp.contains("hash")
        locked = enabled
        failures = sp.getInt("failures", 0)
        // A wait that was running when the app was closed keeps running (at most 15 minutes).
        val left = (sp.getLong("wait_until", 0L) - System.currentTimeMillis()).coerceIn(0L, 15 * 60_000L)
        if (left > 0) waitUntil = SystemClock.elapsedRealtime() + left
    }

    /** Called when the activity stops. Rotation and our own screens don't count. */
    fun onLeave(changingConfigurations: Boolean) {
        if (changingConfigurations) return
        if (skipNextLock) {
            skipNextLock = false
            return
        }
        if (enabled) locked = true
    }

    /**
     * Leaving the floating video window (PiP) back to full screen. Pressing Home while a video plays
     * opens PiP instead of leaving the app, so without this the app would open again unlocked.
     */
    fun onPipClosed() {
        if (enabled) locked = true
    }

    /** Seconds to wait before the next try, 0 when trying is allowed. */
    fun secondsToWait(): Long {
        val ms = waitUntil - SystemClock.elapsedRealtime()
        return if (ms > 0) (ms + 999) / 1000 else 0
    }

    /** Slow (key stretching): call off the main thread. */
    fun matches(password: String): Boolean {
        val salt = decode(sp.getString("salt", null)) ?: return false
        val hash = decode(sp.getString("hash", null)) ?: return false
        val iterations = sp.getInt("iterations", ITERATIONS)
        return MessageDigest.isEqual(derive(password, salt, iterations), hash)
    }

    /** Checks the password on the lock screen. Slow: call off the main thread. */
    fun tryUnlock(password: String): Boolean {
        if (secondsToWait() > 0) return false
        val ok = matches(password)
        if (ok) {
            failures = 0
            waitUntil = 0
            locked = false
        } else {
            failures++
            // 5 free tries, then 30 s, 60 s, 120 s… (at most 15 minutes) between tries.
            if (failures >= FREE_TRIES) {
                val step = (failures - FREE_TRIES).coerceAtMost(5)
                waitUntil = SystemClock.elapsedRealtime() + (30_000L shl step).coerceAtMost(15 * 60_000L)
            }
        }
        val wall = if (waitUntil > 0) System.currentTimeMillis() + (waitUntil - SystemClock.elapsedRealtime()) else 0L
        sp.edit { putInt("failures", failures); putLong("wait_until", wall) }
        return ok
    }

    /** Sets or changes the password. Slow: call off the main thread. */
    fun setPassword(password: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = derive(password, salt, ITERATIONS)
        sp.edit {
            putString("salt", encode(salt))
            putString("hash", encode(hash))
            putInt("iterations", ITERATIONS)
            putInt("failures", 0)
        }
        failures = 0
        waitUntil = 0
        enabled = true
        locked = false
    }

    /** Turns the lock off (after the password or the phone's own screen lock was confirmed). */
    fun disable() {
        sp.edit { clear() }
        enabled = false
        locked = false
        failures = 0
        waitUntil = 0
    }

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encode(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun decode(s: String?) = s?.let { runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull() }
}
