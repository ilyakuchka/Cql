package me.senkuro.unofficial.data

import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import me.senkuro.unofficial.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val tag: String,
    val versionName: String,
    val versionCode: Int,
    val notes: String,
    val apkUrl: String,
    val size: Long,
    val sha256: String?,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val done: Long, val total: Long) : UpdateState
    data class NeedPermission(val info: UpdateInfo) : UpdateState
    data class Installing(val info: UpdateInfo) : UpdateState
    data class Failed(val message: String, val info: UpdateInfo? = null) : UpdateState
}

/**
 * Обновление «по воздуху» из GitHub Releases репозитория ilyakuchka/Cql: проверка последнего релиза,
 * скачивание APK и установка через PackageInstaller. Тег релиза: v<версия>-c<versionCode>-build<N>.
 */
object Updater {
    private const val REPO = "ilyakuchka/Cql"
    private const val CHECK_EVERY_MS = 6L * 60 * 60 * 1000
    private const val ACTION = "me.senkuro.unofficial.UPDATE_STATUS"

    private lateinit var app: Application
    private val prefs get() = app.getSharedPreferences("updates", Context.MODE_PRIVATE)
    // Отдельный клиент: к GitHub не должны уходить заголовки/токен сайта.
    private val http by lazy { OkHttpClient.Builder().callTimeout(0, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build() }

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Диалог скрыт пользователем («Позже») до следующей проверки. */
    private val _dismissed = MutableStateFlow(false)
    val dismissed: StateFlow<Boolean> = _dismissed.asStateFlow()

    fun init(application: Application) { app = application }

    fun dismiss() { _dismissed.value = true }

    suspend fun checkIfDue() {
        if (!prefs.getBoolean("auto", true)) return
        if (System.currentTimeMillis() - prefs.getLong("last", 0L) < CHECK_EVERY_MS) return
        check(silent = true)
    }

    var autoCheck: Boolean
        get() = prefs.getBoolean("auto", true)
        set(v) { prefs.edit().putBoolean("auto", v).apply() }

    /** silent: при автопроверке ошибки и «актуальная версия» не показываем. */
    suspend fun check(silent: Boolean = false) {
        val cur = _state.value
        if (cur is UpdateState.Downloading || cur is UpdateState.Installing) return
        _state.value = UpdateState.Checking
        _dismissed.value = false
        try {
            val info = withContext(Dispatchers.IO) { fetchLatest() }
            prefs.edit().putLong("last", System.currentTimeMillis()).apply()
            _state.value = if (info != null && info.versionCode > BuildConfig.VERSION_CODE) UpdateState.Available(info)
            else if (silent) UpdateState.Idle else UpdateState.UpToDate
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = if (silent) UpdateState.Idle else UpdateState.Failed("Не удалось проверить обновления: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun fetchLatest(): UpdateInfo? {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        http.newCall(req).execute().use { r ->
            if (r.code == 404) return null
            if (!r.isSuccessful) error("GitHub ответил ${r.code}")
            val j = JSONObject(r.body!!.string())
            val tag = j.optString("tag_name")
            val code = Regex("-c(\\d+)").find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: return null
            val assets = j.optJSONArray("assets") ?: return null
            var apk: JSONObject? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) { apk = a; break }
            }
            apk ?: return null
            val body = j.optString("body")
            val sha = Regex("sha256:\\s*([0-9a-fA-F]{64})").find(body)?.groupValues?.get(1)
            val notes = body.lineSequence().filterNot { it.trim().startsWith("sha256:", ignoreCase = true) }.joinToString("\n").trim()
            val name = Regex("^v(.+?)-c\\d+").find(tag)?.groupValues?.get(1) ?: tag.removePrefix("v")
            return UpdateInfo(tag, name, code, notes, apk.getString("browser_download_url"), apk.optLong("size"), sha)
        }
    }

    fun canInstall(): Boolean = Build.VERSION.SDK_INT < 26 || app.packageManager.canRequestPackageInstalls()

    /** Открывает системный экран «Установка из неизвестных источников» для этого приложения. */
    fun openInstallPermission() {
        val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app.startActivity(i)
    }

    suspend fun downloadAndInstall(info: UpdateInfo) {
        if (!canInstall()) { _state.value = UpdateState.NeedPermission(info); openInstallPermission(); return }
        try {
            _state.value = UpdateState.Downloading(info, 0, info.size)
            val file = withContext(Dispatchers.IO) { download(info) }
            _state.value = UpdateState.Installing(info)
            withContext(Dispatchers.IO) { install(file) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = UpdateState.Failed("Не удалось обновить: ${e.message ?: e.javaClass.simpleName}", info)
        }
    }

    private fun download(info: UpdateInfo): File {
        val dir = File(app.cacheDir, "updates").also { it.mkdirs(); it.listFiles()?.forEach { f -> f.delete() } }
        val out = File(dir, "update.apk")
        val md = MessageDigest.getInstance("SHA-256")
        http.newCall(Request.Builder().url(info.apkUrl).build()).execute().use { r ->
            if (!r.isSuccessful) error("загрузка: HTTP ${r.code}")
            val body = r.body!!
            val total = body.contentLength().takeIf { it > 0 } ?: info.size
            var done = 0L
            var lastEmit = 0L
            body.byteStream().use { input ->
                out.outputStream().use { os ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        os.write(buf, 0, n); md.update(buf, 0, n); done += n
                        val now = System.currentTimeMillis()
                        if (now - lastEmit > 150) { lastEmit = now; _state.value = UpdateState.Downloading(info, done, total) }
                    }
                }
            }
        }
        val hex = md.digest().joinToString("") { "%02x".format(it) }
        if (info.sha256 != null && !hex.equals(info.sha256, ignoreCase = true)) {
            out.delete(); error("контрольная сумма не совпала")
        }
        return out
    }

    private fun install(file: File) {
        val installer = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val id = installer.createSession(params)
        installer.openSession(id).use { s ->
            file.inputStream().use { input -> s.openWrite("update.apk", 0, file.length()).use { os -> input.copyTo(os); s.fsync(os) } }
            val intent = Intent(app, UpdateReceiver::class.java).setAction(ACTION)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            s.commit(PendingIntent.getBroadcast(app, id, intent, flags).intentSender)
        }
    }

    internal fun onStatus(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (confirm != null) context.startActivity(confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> _state.value = UpdateState.Idle
            PackageInstaller.STATUS_FAILURE_ABORTED -> _state.value = UpdateState.Idle
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                val hint = if (status == PackageInstaller.STATUS_FAILURE_CONFLICT || status == PackageInstaller.STATUS_FAILURE_INCOMPATIBLE)
                    " (подпись не совпадает с установленной версией — переустановите приложение)" else ""
                _state.value = UpdateState.Failed("Установка не удалась: $msg$hint")
            }
        }
    }
}

class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Updater.onStatus(context, intent)
}
