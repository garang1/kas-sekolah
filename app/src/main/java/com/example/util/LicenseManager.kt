package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LicenseInfo(
    val isLicensed: Boolean,
    val isTrial: Boolean,
    val isExpired: Boolean,
    val statusText: String,
    val expiryDateFormatted: String,
    val daysRemaining: Long,
    val npsn: String,
    val requestCode: String,
    val registeredSchoolName: String = "",
    val isOnlineVerified: Boolean = false,
    val deviceSlotText: String = "",
    val deviceModelName: String = ""
)

data class OnlineCheckResult(
    val success: Boolean,
    val isRegistered: Boolean,
    val isBlocked: Boolean,
    val isOverLimit: Boolean = false,
    val status: String,
    val expiryDateString: String,
    val message: String,
    val schoolName: String = "",
    val deviceSlot: String = "",
    val maxDevices: Int = 2
)

object LicenseManager {

    private const val PREFS_NAME = "bku_license_prefs"
    private const val KEY_FIRST_INSTALL_TIME = "first_install_time"
    private const val KEY_ACTIVATED_KEY = "activated_key"
    private const val KEY_EXPIRY_TIMESTAMP = "expiry_timestamp"
    private const val KEY_ACTIVATED_YEAR = "activated_year"
    private const val KEY_SERVER_URL = "server_license_url"
    private const val KEY_LAST_ONLINE_CHECK = "last_online_check"
    private const val KEY_REGISTERED_SCHOOL_NAME = "registered_school_name"
    private const val KEY_IS_BLOCKED = "is_license_blocked"
    private const val KEY_DEVICE_SLOT = "device_slot_assigned"
    private const val KEY_TRIAL_EXPIRED_SERVER = "trial_expired_server"
    private const val KEY_SERVER_TRIAL_EXPIRY = "server_trial_expiry_ts"

    private const val TRIAL_DURATION_DAYS = 180L // 6 Bulan (180 Hari)
    private const val SECRET_SALT = "BukuKasPintar_SDN33_OpperAntoni_2026"

    private const val DEFAULT_MASTER_SERVER_URL = "https://script.google.com/macros/s/AKfycbxFT4KT_RNXTLpa4bjzvGt9ekln5i3Vi8ZzvBlubYrRcG024B-lEJYKSrPfANacV7iv_A/exec"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getServerUrl(context: Context): String {
        val customUrl = getPrefs(context).getString(KEY_SERVER_URL, "") ?: ""
        return customUrl.ifBlank { DEFAULT_MASTER_SERVER_URL }
    }

    fun setServerUrl(context: Context, url: String) {
        getPrefs(context).edit().putString(KEY_SERVER_URL, url.trim()).apply()
    }

    /**
     * Mendapatkan Unique Android Device ID (Aman & Tidak Berubah)
     */
    @SuppressLint("HardwareIds")
    fun getDeviceId(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return if (!androidId.isNullOrBlank()) androidId else "DEV_${Build.SERIAL ?: "UNKNOWN"}"
    }

    /**
     * Mendapatkan Nama Model Perangkat (Contoh: Samsung SM-A546B atau Xiaomi 2201117TY)
     */
    fun getDeviceModelName(): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        val model = Build.MODEL
        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model"
        }
    }

    /**
     * Inisialisasi waktu pemasangan pertama kali
     */
    fun initInstallTime(context: Context) {
        val prefs = getPrefs(context)
        if (!prefs.contains(KEY_FIRST_INSTALL_TIME)) {
            prefs.edit().putLong(KEY_FIRST_INSTALL_TIME, System.currentTimeMillis()).apply()
        }
    }

    /**
     * Menghasilkan Kode Permintaan Lisensi berdasarkan NPSN + Device ID
     */
    fun generateRequestCode(context: Context, npsn: String): String {
        val cleanNpsn = npsn.trim().ifBlank { "00000000" }
        val devId = getDeviceId(context).takeLast(6).uppercase(Locale.getDefault())
        val currentYear = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())
        val raw = "REQ-$cleanNpsn-$devId-$currentYear-$SECRET_SALT"
        val hash = sha256(raw).take(6).uppercase(Locale.getDefault())
        return "REQ-$cleanNpsn-$devId-$hash"
    }

    /**
     * Menghasilkan Kunci Lisensi Offline yang Valid
     */
    fun generateValidKey(npsn: String, targetYear: Int): String {
        val cleanNpsn = npsn.trim().ifBlank { "00000000" }
        val raw = "ACTIVE-$cleanNpsn-$targetYear-$SECRET_SALT"
        val signature = sha256(raw).take(8).uppercase(Locale.getDefault())
        return "LIC-$cleanNpsn-$targetYear-$signature"
    }

    /**
     * Memeriksa lisensi secara online ke Server Google Sheets Master
     * Hanya menggunakan NPSN sebagai kunci utama, Android Device ID, dan Tipe Model HP
     */
    suspend fun checkLicenseOnline(context: Context, npsn: String): OnlineCheckResult = withContext(Dispatchers.IO) {
        val serverUrl = getServerUrl(context)
        val cleanNpsn = npsn.trim()
        val deviceId = getDeviceId(context)
        val deviceModel = getDeviceModelName()

        if (serverUrl.isBlank()) {
            return@withContext OnlineCheckResult(
                success = false,
                isRegistered = false,
                isBlocked = false,
                isOverLimit = false,
                status = "OFFLINE_MODE",
                expiryDateString = "",
                message = "URL Server Lisensi belum disetel. Menggunakan verifikasi lokal/kunci."
            )
        }

        if (cleanNpsn.isBlank()) {
            return@withContext OnlineCheckResult(
                success = false,
                isRegistered = false,
                isBlocked = false,
                isOverLimit = false,
                status = "INVALID_NPSN",
                expiryDateString = "",
                message = "NPSN sekolah belum diisi di Profil Sekolah."
            )
        }

        try {
            val encodedNpsn = URLEncoder.encode(cleanNpsn, "UTF-8")
            val encodedDevId = URLEncoder.encode(deviceId, "UTF-8")
            val encodedModel = URLEncoder.encode(deviceModel, "UTF-8")

            var targetUrl = if (serverUrl.contains("?")) {
                "$serverUrl&action=checkLicense&npsn=$encodedNpsn&deviceId=$encodedDevId&deviceModel=$encodedModel"
            } else {
                "$serverUrl?action=checkLicense&npsn=$encodedNpsn&deviceId=$encodedDevId&deviceModel=$encodedModel"
            }

            var conn: HttpURLConnection
            var responseCode = -1
            var redirectCount = 0

            // Follow HTTP redirects (302/301/307) which Google Apps Script always produces
            while (redirectCount < 5) {
                conn = URL(targetUrl).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.instanceFollowRedirects = true

                responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
                    responseCode == 307 || responseCode == 308) {
                    val location = conn.getHeaderField("Location")
                    if (!location.isNullOrBlank()) {
                        targetUrl = location
                        redirectCount++
                        continue
                    }
                }

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val responseText = reader.use { it.readText() }
                    val json = JSONObject(responseText)

                    val status = json.optString("status", "NOT_FOUND")
                    val isRegistered = json.optBoolean("isRegistered", false)
                    val isBlocked = json.optBoolean("isBlocked", false)
                    val isOverLimit = json.optBoolean("isOverLimit", false)
                    val isTrialExpired = json.optBoolean("isTrialExpired", false)
                    val expiryStr = json.optString("expiryDate", "").trim()
                    val trialExpiryStr = json.optString("trialExpiryDate", "").trim()
                    val registeredName = json.optString("schoolName", "")
                    val deviceSlot = json.optString("deviceSlot", "")
                    val maxDevices = json.optInt("maxDevices", 2)
                    val message = json.optString("message", "")

                    val prefs = getPrefs(context)
                    if (isBlocked) {
                        prefs.edit()
                            .putBoolean(KEY_IS_BLOCKED, true)
                            .putString(KEY_ACTIVATED_KEY, "")
                            .putLong(KEY_EXPIRY_TIMESTAMP, 0L)
                            .putString(KEY_DEVICE_SLOT, "")
                            .apply()
                    } else if (isOverLimit) {
                        prefs.edit()
                            .putBoolean(KEY_IS_BLOCKED, true)
                            .putString(KEY_ACTIVATED_KEY, "")
                            .putLong(KEY_EXPIRY_TIMESTAMP, 0L)
                            .putString(KEY_DEVICE_SLOT, "OVERLIMIT")
                            .apply()
                    } else if (isTrialExpired) {
                        prefs.edit()
                            .putBoolean(KEY_TRIAL_EXPIRED_SERVER, true)
                            .apply()
                    } else if (isRegistered && expiryStr.isNotBlank()) {
                        // Multi-format date parser
                        val datePatterns = arrayOf(
                            "yyyy-MM-dd",
                            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                            "yyyy-MM-dd'T'HH:mm:ss",
                            "dd/MM/yyyy",
                            "yyyy/MM/dd"
                        )
                        var parsedDate: Date? = null
                        for (pattern in datePatterns) {
                            try {
                                val sdf = SimpleDateFormat(pattern, Locale.getDefault())
                                parsedDate = sdf.parse(expiryStr)
                                if (parsedDate != null) break
                            } catch (ignored: Exception) {}
                        }

                        val expiryTs = parsedDate?.time ?: (System.currentTimeMillis() + (365L * 24 * 60 * 60 * 1000))
                        prefs.edit()
                            .putBoolean(KEY_IS_BLOCKED, false)
                            .putBoolean(KEY_TRIAL_EXPIRED_SERVER, false)
                            .putLong(KEY_EXPIRY_TIMESTAMP, expiryTs)
                            .putString(KEY_REGISTERED_SCHOOL_NAME, registeredName)
                            .putString(KEY_DEVICE_SLOT, deviceSlot)
                            .putLong(KEY_LAST_ONLINE_CHECK, System.currentTimeMillis())
                            .apply()
                    } else if (trialExpiryStr.isNotBlank()) {
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        val parsedTrial = try { sdf.parse(trialExpiryStr) } catch (e: Exception) { null }
                        if (parsedTrial != null) {
                            prefs.edit().putLong(KEY_SERVER_TRIAL_EXPIRY, parsedTrial.time).apply()
                        }
                    }

                    return@withContext OnlineCheckResult(
                        success = true,
                        isRegistered = isRegistered,
                        isBlocked = isBlocked,
                        isOverLimit = isOverLimit,
                        status = status,
                        expiryDateString = expiryStr,
                        message = message,
                        schoolName = registeredName,
                        deviceSlot = deviceSlot,
                        maxDevices = maxDevices
                    )
                }
                break
            }

            return@withContext OnlineCheckResult(
                success = false,
                isRegistered = false,
                isBlocked = false,
                isOverLimit = false,
                status = "HTTP_ERROR",
                expiryDateString = "",
                message = "Gagal terhubung ke Server Lisensi (HTTP $responseCode)"
            )
        } catch (e: Exception) {
            return@withContext OnlineCheckResult(
                success = false,
                isRegistered = false,
                isBlocked = false,
                isOverLimit = false,
                status = "NETWORK_ERROR",
                expiryDateString = "",
                message = "Koneksi internet bermasalah atau URL server tidak valid: ${e.localizedMessage}"
            )
        }
    }

    /**
     * Aktivasi lisensi melalui Kunci Serial (Offline / Manual)
     */
    fun activateLicense(context: Context, npsn: String, inputKey: String): Pair<Boolean, String> {
        val cleanKey = inputKey.trim().uppercase(Locale.getDefault())
        val cleanNpsn = npsn.trim()

        if (cleanNpsn.isBlank()) {
            return Pair(false, "Harap isi NPSN Sekolah terlebih dahulu di tab Profil Sekolah.")
        }

        val parts = cleanKey.split("-")
        if (parts.size != 4 || parts[0] != "LIC") {
            return Pair(false, "Format Kunci Lisensi tidak valid. Contoh: LIC-12345678-2026-XXXXXXXX")
        }

        val keyNpsn = parts[1]
        val keyYearStr = parts[2]
        val keyYear = keyYearStr.toIntOrNull()

        if (keyNpsn != cleanNpsn) {
            return Pair(false, "Kunci Lisensi ini dibuat untuk NPSN $keyNpsn, bukan untuk NPSN sekolah Anda ($cleanNpsn).")
        }

        if (keyYear == null || keyYear < 2024) {
            return Pair(false, "Tahun pada Kunci Lisensi tidak valid.")
        }

        val expectedKey = generateValidKey(cleanNpsn, keyYear)
        if (cleanKey != expectedKey) {
            return Pair(false, "Kunci Lisensi salah atau tidak terdaftar.")
        }

        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.YEAR, keyYear)
        calendar.set(java.util.Calendar.MONTH, java.util.Calendar.DECEMBER)
        calendar.set(java.util.Calendar.DAY_OF_MONTH, 31)
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 23)
        calendar.set(java.util.Calendar.MINUTE, 59)
        calendar.set(java.util.Calendar.SECOND, 59)

        var expiryTs = calendar.timeInMillis
        val oneYearFromNow = System.currentTimeMillis() + (365L * 24 * 60 * 60 * 1000)
        if (expiryTs < oneYearFromNow) {
            expiryTs = oneYearFromNow
        }

        val prefs = getPrefs(context)
        prefs.edit()
            .putBoolean(KEY_IS_BLOCKED, false)
            .putString(KEY_ACTIVATED_KEY, cleanKey)
            .putLong(KEY_EXPIRY_TIMESTAMP, expiryTs)
            .putInt(KEY_ACTIVATED_YEAR, keyYear)
            .apply()

        val dateFmt = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID")).format(Date(expiryTs))
        return Pair(true, "Lisensi Tahunan Berhasil Diaktifkan! Masa aktif hingga $dateFmt.")
    }

    /**
     * Memeriksa status lisensi sekolah saat ini (Online/Offline, Trial, Active, Blocked, Expired)
     */
    fun checkLicenseStatus(context: Context, npsn: String): LicenseInfo {
        initInstallTime(context)
        val prefs = getPrefs(context)
        val cleanNpsn = npsn.trim().ifBlank { "00000000" }
        val now = System.currentTimeMillis()

        val isBlocked = prefs.getBoolean(KEY_IS_BLOCKED, false)
        val isTrialExpiredServer = prefs.getBoolean(KEY_TRIAL_EXPIRED_SERVER, false)
        val serverTrialExpiryTs = prefs.getLong(KEY_SERVER_TRIAL_EXPIRY, 0L)
        val activatedKey = prefs.getString(KEY_ACTIVATED_KEY, "") ?: ""
        val expiryTs = prefs.getLong(KEY_EXPIRY_TIMESTAMP, 0L)
        val installTs = prefs.getLong(KEY_FIRST_INSTALL_TIME, now)
        val regSchool = prefs.getString(KEY_REGISTERED_SCHOOL_NAME, "") ?: ""
        val lastOnlineCheck = prefs.getLong(KEY_LAST_ONLINE_CHECK, 0L)
        val deviceSlot = prefs.getString(KEY_DEVICE_SLOT, "") ?: ""
        val deviceModel = getDeviceModelName()

        val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID"))
        val requestCode = generateRequestCode(context, cleanNpsn)

        // 1. Jika Diblokir atau Melebihi Kuota (Overlimit)
        if (isBlocked) {
            val blockReason = if (deviceSlot == "OVERLIMIT") {
                "⛔ Kuota Perangkat Penuh (Maksimal 2 HP/Sekolah)"
            } else {
                "⛔ Lisensi Dinonaktifkan / Diblokir oleh Pusat"
            }
            return LicenseInfo(
                isLicensed = false,
                isTrial = false,
                isExpired = true,
                statusText = blockReason,
                expiryDateFormatted = "-",
                daysRemaining = 0,
                npsn = cleanNpsn,
                requestCode = requestCode,
                registeredSchoolName = regSchool,
                isOnlineVerified = true,
                deviceSlotText = if (deviceSlot == "OVERLIMIT") "Ditolak (Slot 3+)" else deviceSlot,
                deviceModelName = deviceModel
            )
        }

        // 2. Jika ada masa aktif langganan resmi (dari Kunci atau dari Server Master)
        if (expiryTs > 0) {
            if (now <= expiryTs) {
                val daysLeft = Math.max(0, (expiryTs - now) / (1000 * 60 * 60 * 24))
                return LicenseInfo(
                    isLicensed = true,
                    isTrial = false,
                    isExpired = false,
                    statusText = if (lastOnlineCheck > 0) "Aktif (Terverifikasi Server Master)" else "Aktif (Lisensi Tahunan)",
                    expiryDateFormatted = dateFormat.format(Date(expiryTs)),
                    daysRemaining = daysLeft,
                    npsn = cleanNpsn,
                    requestCode = requestCode,
                    registeredSchoolName = regSchool,
                    isOnlineVerified = lastOnlineCheck > 0,
                    deviceSlotText = if (deviceSlot.isNotBlank()) "Slot $deviceSlot" else "HP Terdaftar",
                    deviceModelName = deviceModel
                )
            } else {
                return LicenseInfo(
                    isLicensed = false,
                    isTrial = false,
                    isExpired = true,
                    statusText = "Masa Langganan Telah Habis",
                    expiryDateFormatted = dateFormat.format(Date(expiryTs)),
                    daysRemaining = 0,
                    npsn = cleanNpsn,
                    requestCode = requestCode,
                    registeredSchoolName = regSchool,
                    isOnlineVerified = lastOnlineCheck > 0,
                    deviceSlotText = deviceSlot,
                    deviceModelName = deviceModel
                )
            }
        }

        // 3. Jika Server Master menyatakan Trial Device ID ini sudah kedaluwarsa (Anti-Reset / Clear Data)
        if (isTrialExpiredServer) {
            return LicenseInfo(
                isLicensed = false,
                isTrial = true,
                isExpired = true,
                statusText = "Masa Percobaan 6 Bulan Telah Habis",
                expiryDateFormatted = if (serverTrialExpiryTs > 0) dateFormat.format(Date(serverTrialExpiryTs)) else "-",
                daysRemaining = 0,
                npsn = cleanNpsn,
                requestCode = requestCode,
                registeredSchoolName = regSchool,
                deviceSlotText = "Trial Expired (Server Locked)",
                deviceModelName = deviceModel
            )
        }

        // 4. Masa Percobaan (Trial 6 Bulan / 180 Hari)
        val effectiveTrialExpiryTs = if (serverTrialExpiryTs > 0) serverTrialExpiryTs else installTs + (TRIAL_DURATION_DAYS * 24 * 60 * 60 * 1000)
        if (now <= effectiveTrialExpiryTs) {
            val daysLeft = Math.max(0, (effectiveTrialExpiryTs - now) / (1000 * 60 * 60 * 24))
            return LicenseInfo(
                isLicensed = true,
                isTrial = true,
                isExpired = false,
                statusText = "Masa Percobaan Gratis (Trial 6 Bulan)",
                expiryDateFormatted = dateFormat.format(Date(effectiveTrialExpiryTs)),
                daysRemaining = daysLeft,
                npsn = cleanNpsn,
                requestCode = requestCode,
                registeredSchoolName = regSchool,
                deviceSlotText = "Trial Mode",
                deviceModelName = deviceModel
            )
        } else {
            return LicenseInfo(
                isLicensed = false,
                isTrial = true,
                isExpired = true,
                statusText = "Masa Percobaan 6 Bulan Telah Habis",
                expiryDateFormatted = dateFormat.format(Date(effectiveTrialExpiryTs)),
                daysRemaining = 0,
                npsn = cleanNpsn,
                requestCode = requestCode,
                registeredSchoolName = regSchool,
                deviceSlotText = "Trial Expired",
                deviceModelName = deviceModel
            )
        }
    }

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray())
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
}
