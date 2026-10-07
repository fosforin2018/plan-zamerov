package com.zamerplan.app.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.zamerplan.app.alarm.SettingsStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupManager {

    const val FORMAT_VERSION = 1
    const val FILE_NAME = "ZamerPlan_backup.zip"

    data class Destination(
        val uri: String,
        val name: String,
        val lastOk: Long,
        val lastError: String
    )

    data class BackupMeta(
        val formatVersion: Int,
        val appVersion: String,
        val createdAt: String,
        val zamerCount: Int,
        val voiceCount: Int
    )

    data class BackupResult(
        val destination: Destination,
        val ok: Boolean,
        val message: String
    )

    data class RestoreResult(val ok: Boolean, val message: String)

    // Вариант облака для простого списка выбора
    data class CloudOption(
        val title: String,
        val rootUri: Uri?,
        val packageName: String
    )

    // ============================================================
    // ПОДКЛЮЧЁННЫЕ ОБЛАКА
    // ============================================================

    fun destinations(ctx: Context): List<Destination> {
        val prefs = ctx.getSharedPreferences("backup_destinations", Context.MODE_PRIVATE)
        val count = prefs.getInt("count", 0)
        return (0 until count).mapNotNull { i ->
            val uri = prefs.getString("uri_$i", null) ?: return@mapNotNull null
            Destination(
                uri = uri,
                name = prefs.getString("name_$i", "Облако") ?: "Облако",
                lastOk = prefs.getLong("ok_$i", 0L),
                lastError = prefs.getString("err_$i", "") ?: ""
            )
        }
    }

    private fun saveDestinations(ctx: Context, list: List<Destination>) {
        val prefs = ctx.getSharedPreferences("backup_destinations", Context.MODE_PRIVATE)
        val ed = prefs.edit()
        ed.putInt("count", list.size)
        for (i in 0 until 20) {
            if (i >= list.size) {
                ed.remove("uri_$i").remove("name_$i").remove("ok_$i").remove("err_$i")
            }
        }
        list.forEachIndexed { i, d ->
            ed.putString("uri_$i", d.uri)
            ed.putString("name_$i", d.name)
            ed.putLong("ok_$i", d.lastOk)
            ed.putString("err_$i", d.lastError)
        }
        ed.apply()
    }

    private fun updateDest(ctx: Context, uri: String, transform: (Destination) -> Destination) {
        saveDestinations(ctx, destinations(ctx).map { if (it.uri == uri) transform(it) else it })
    }

    fun addDestination(ctx: Context, uri: Uri, name: String) {
        try {
            ctx.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) { }
        val list = destinations(ctx).toMutableList()
        val uriStr = uri.toString()
        if (list.none { it.uri == uriStr }) {
            list.add(Destination(uriStr, name, 0L, ""))
            saveDestinations(ctx, list)
        }
    }

    fun removeDestination(ctx: Context, uri: String) {
        saveDestinations(ctx, destinations(ctx).filter { it.uri != uri })
        try {
            ctx.contentResolver.releasePersistableUriPermission(
                Uri.parse(uri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) { }
    }

    fun displayName(ctx: Context, uri: Uri): String {
        val cloud = cloudNameByAuthority(uri)
        val file = try {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        } catch (e: Exception) { null }
        return if (file.isNullOrBlank()) cloud else "$cloud · $file"
    }

    private fun cloudNameByAuthority(uri: Uri): String = when {
        uri.authority?.contains("yandex") == true -> "Яндекс Диск"
        uri.authority?.contains("google") == true -> "Google Диск"
        uri.authority?.contains("docs") == true -> "Google Диск"
        uri.authority?.contains("mail") == true -> "Облако Mail.ru"
        uri.authority?.contains("dropbox") == true -> "Dropbox"
        uri.authority?.contains("onedrive") == true -> "OneDrive"
        else -> uri.authority ?: "Облако"
    }

    // ============================================================
    // ПОИСК ОБЛАК НА ТЕЛЕФОНЕ (работает на любом Android 11+)
    // ============================================================

    private val knownClouds = listOf(
        "ru.yandex.disk" to "Яндекс Диск",
        "com.google.android.apps.docs" to "Google Диск",
        "ru.mail.cloud" to "Облако Mail.ru",
        "com.dropbox.android" to "Dropbox",
        "com.microsoft.skydrive" to "OneDrive"
    )

    private val systemProviders = setOf(
        "com.android.externalstorage",
        "com.android.providers.media"
    )

    fun listCloudOptions(ctx: Context): List<CloudOption> {
        val result = mutableListOf<CloudOption>()
        val pm = ctx.packageManager
        val providers = mutableListOf<Triple<String, String, String>>()

        // 1) Все провайдеры документов, видимые системе
        try {
            val intent = Intent("android.content.action.DOCUMENTS_PROVIDER")
            for (ri in pm.queryIntentContentProviders(intent, 0)) {
                val info = ri.providerInfo ?: continue
                val authority = info.authority ?: continue
                if (info.packageName in systemProviders) continue
                val label = ri.loadLabel(pm)?.toString() ?: info.packageName
                providers.add(Triple(info.packageName, authority, label))
            }
        } catch (e: Exception) { }

        // 2) Добавляем известные облака, установленные на телефоне
        for ((pkg, title) in knownClouds) {
            val installed = try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (e: Exception) { false }
            if (installed && providers.none { it.first == pkg }) {
                providers.add(Triple(pkg, "", title))
            }
        }

        // 3) Для каждого пробуем получить корень для прямого входа
        for ((pkg, authority, label) in providers) {
            val titleBase = knownClouds.firstOrNull { it.first == pkg }?.second ?: label
            var added = false
            if (authority.isNotBlank()) {
                try {
                    val rootsUri = DocumentsContract.buildRootsUri(authority)
                    ctx.contentResolver.query(rootsUri, null, null, null, null)?.use { c ->
                        val docIdx = c.getColumnIndex(DocumentsContract.Root.COLUMN_DOCUMENT_ID)
                        val titleIdx = c.getColumnIndex(DocumentsContract.Root.COLUMN_TITLE)
                        while (c.moveToNext()) {
                            val docId = if (docIdx >= 0) c.getString(docIdx) else null
                            if (docId.isNullOrBlank()) continue
                            val t = if (titleIdx >= 0) {
                                c.getString(titleIdx)?.takeIf { it.isNotBlank() } ?: titleBase
                            } else {
                                titleBase
                            }
                            val uri = DocumentsContract.buildDocumentUri(authority, docId)
                            if (result.none { it.rootUri == uri }) {
                                result.add(CloudOption(t, uri, pkg))
                                added = true
                            }
                        }
                    }
                } catch (e: Exception) { }
            }
            if (!added && result.none { it.packageName == pkg && it.rootUri == null }) {
                result.add(CloudOption(titleBase, null, pkg))
            }
        }
        return result
    }

    fun createDocumentIntent(initialUri: Uri?): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = "application/zip"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_TITLE, FILE_NAME)
            if (initialUri != null) {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
            }
        }

    // ============================================================
    // СБОРКА КОПИИ
    // ============================================================

    private fun appVersion(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    } catch (e: Exception) { "?" }

    private fun voiceFiles(ctx: Context): List<File> =
        ctx.filesDir.listFiles()
            ?.filter { it.name.startsWith("voice_") && it.name.endsWith(".m4a") && it.length() > 0L }
            ?: emptyList()

    private fun buildZip(ctx: Context, target: File): BackupMeta {
        val prefs = ctx.getSharedPreferences("zamer_storage", Context.MODE_PRIVATE)
        val zamersJson = prefs.getString("zamers_json", "[]") ?: "[]"
        val zamerCount = try { JSONArray(zamersJson).length() } catch (e: Exception) { 0 }
        val voices = voiceFiles(ctx)

        val meta = BackupMeta(
            formatVersion = FORMAT_VERSION,
            appVersion = appVersion(ctx),
            createdAt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date()),
            zamerCount = zamerCount,
            voiceCount = voices.size
        )

        ZipOutputStream(target.outputStream().buffered()).use { zos ->
            zos.putNextEntry(ZipEntry("zamers.json"))
            zos.write(zamersJson.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("meta.json"))
            zos.write(
                JSONObject().apply {
                    put("format_version", meta.formatVersion)
                    put("app_version", meta.appVersion)
                    put("created_at", meta.createdAt)
                    put("zamer_count", meta.zamerCount)
                    put("voice_count", meta.voiceCount)
                }.toString().toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()

            voices.forEach { f ->
                zos.putNextEntry(ZipEntry("voices/" + f.name))
                f.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        return meta
    }

    private fun verifyZip(file: File): Boolean = try {
        ZipFile(file).use { zf ->
            val entry = zf.getEntry("zamers.json") ?: return false
            val text = zf.getInputStream(entry).bufferedReader().use { it.readText() }
            JSONArray(text)
            zf.getEntry("meta.json") != null
        }
    } catch (e: Exception) { false }

    // ============================================================
    // СОЗДАНИЕ КОПИЙ ВО ВСЕ ОБЛАКА
    // ============================================================

    fun backupAll(ctx: Context): List<BackupResult> {
        val dests = destinations(ctx)
        if (dests.isEmpty()) return emptyList()
        val tmp = File(ctx.cacheDir, "backup_tmp.zip")
        val results = mutableListOf<BackupResult>()
        try {
            buildZip(ctx, tmp)
            if (!verifyZip(tmp)) {
                return dests.map { BackupResult(it, false, "ошибка сборки копии") }
            }
            dests.forEach { d ->
                try {
                    writeToFile(ctx, Uri.parse(d.uri), tmp)
                    updateDest(ctx, d.uri) { it.copy(lastOk = System.currentTimeMillis(), lastError = "") }
                    results.add(BackupResult(d, true, ""))
                } catch (e: Exception) {
                    val msg = if (e is SecurityException || e is FileNotFoundException) {
                        "файл недоступен — переподключите облако"
                    } else {
                        "ошибка записи"
                    }
                    updateDest(ctx, d.uri) { it.copy(lastError = msg) }
                    results.add(BackupResult(d, false, msg))
                }
            }
            if (results.any { it.ok }) clearDirty(ctx)
        } catch (e: Exception) {
            dests.forEach { results.add(BackupResult(it, false, "ошибка сборки: ${e.message}")) }
        } finally {
            tmp.delete()
        }
        return results
    }

    private fun writeToFile(ctx: Context, uri: Uri, src: File) {
        ctx.contentResolver.openOutputStream(uri, "wt")?.use { os ->
            src.inputStream().use { it.copyTo(os) }
        } ?: throw java.io.IOException("не удалось открыть поток записи")
    }

    // ============================================================
    // ЧТЕНИЕ META ИЗ КОПИИ
    // ============================================================

    fun readMeta(ctx: Context, uri: Uri): BackupMeta? = try {
        ctx.contentResolver.openInputStream(uri)?.use { ins -> readMetaFromStream(ins) }
    } catch (e: Exception) { null }

    private fun readMetaFromStream(ins: InputStream): BackupMeta? {
        ZipInputStream(ins.buffered()).use { zis ->
            var meta: BackupMeta? = null
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "meta.json") {
                    val o = JSONObject(zis.bufferedReader().use { it.readText() })
                    meta = BackupMeta(
                        formatVersion = o.optInt("format_version", 1),
                        appVersion = o.optString("app_version", ""),
                        createdAt = o.optString("created_at", ""),
                        zamerCount = o.optInt("zamer_count", 0),
                        voiceCount = o.optInt("voice_count", 0)
                    )
                }
                entry = zis.nextEntry
            }
            return meta
        }
    }

    // ============================================================
    // ВОССТАНОВЛЕНИЕ
    // ============================================================

    fun restore(ctx: Context, uri: Uri): RestoreResult {
        val tmp = File(ctx.cacheDir, "restore_tmp.zip")
        return try {
            ctx.contentResolver.openInputStream(uri)
                ?.use { ins -> tmp.outputStream().use { ins.copyTo(it) } }
                ?: return RestoreResult(false, "не удалось открыть файл")

            if (!verifyZip(tmp)) {
                tmp.delete()
                return RestoreResult(false, "файл повреждён или не является копией")
            }

            val metaObj = ZipFile(tmp).use { zf ->
                val e = zf.getEntry("meta.json") ?: return RestoreResult(false, "в копии нет meta.json")
                JSONObject(zf.getInputStream(e).bufferedReader().use { it.readText() })
            }
            val fv = metaObj.optInt("format_version", 1)
            if (fv > FORMAT_VERSION) {
                tmp.delete()
                return RestoreResult(false, "копия создана более новой версией приложения — сначала обновите приложение")
            }

            try {
                buildZip(ctx, File(ctx.filesDir, "local_safety.zip"))
            } catch (e: Exception) { }

            var restoredZamers = 0
            var restoredVoices = 0
            ZipFile(tmp).use { zf ->
                val entries = zf.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    when {
                        entry.name == "zamers.json" -> {
                            val text = zf.getInputStream(entry).bufferedReader().use { it.readText() }
                            val arr = JSONArray(text)
                            ctx.getSharedPreferences("zamer_storage", Context.MODE_PRIVATE)
                                .edit().putString("zamers_json", text).apply()
                            restoredZamers = arr.length()
                        }
                        entry.name.startsWith("voices/") -> {
                            val name = entry.name.substringAfter("voices/")
                            if (name.isNotBlank() && !name.contains("..") && !name.contains("/")) {
                                val out = File(ctx.filesDir, name)
                                zf.getInputStream(entry).use { ins -> out.outputStream().use { ins.copyTo(it) } }
                                restoredVoices++
                            }
                        }
                    }
                }
            }
            tmp.delete()
            RestoreResult(true, "Готово! Восстановлено замеров: $restoredZamers, голосовых: $restoredVoices")
        } catch (e: Exception) {
            tmp.delete()
            RestoreResult(false, "ошибка восстановления: ${e.message}")
        }
    }

    // ============================================================
    // АВТОКОПИЯ
    // ============================================================

    private const val DEBOUNCE_MS = 20_000L
    private const val PERIODIC_MS = 15 * 60_000L
    private const val THROTTLE_MS = 5 * 60_000L

    private val handler = Handler(Looper.getMainLooper())
    private var debounceRunnable: Runnable? = null
    private var periodicRunnable: Runnable? = null

    private fun statePrefs(ctx: Context) =
        ctx.getSharedPreferences("backup_state", Context.MODE_PRIVATE)

    private fun isDirty(ctx: Context): Boolean =
        statePrefs(ctx).getBoolean("dirty", false)

    private fun clearDirty(ctx: Context) {
        statePrefs(ctx).edit()
            .putBoolean("dirty", false)
            .putLong("last_wall_at", System.currentTimeMillis())
            .apply()
    }

    fun onDataChanged(ctx: Context) {
        val app = ctx.applicationContext
        statePrefs(app).edit().putBoolean("dirty", true).apply()
        debounceRunnable?.let { handler.removeCallbacks(it) }
        val r = Runnable { flush(app, force = false) }
        debounceRunnable = r
        handler.postDelayed(r, DEBOUNCE_MS)
    }

    fun startPeriodic(ctx: Context) {
        stopPeriodic()
        val app = ctx.applicationContext
        val r = object : Runnable {
            override fun run() {
                flush(app, force = false)
                handler.postDelayed(this, PERIODIC_MS)
            }
        }
        periodicRunnable = r
        handler.postDelayed(r, PERIODIC_MS)
    }

    fun stopPeriodic() {
        periodicRunnable?.let { handler.removeCallbacks(it) }
        periodicRunnable = null
    }

    fun onAppForeground(ctx: Context) {
        flush(ctx.applicationContext, force = false)
    }

    fun onAppBackground(ctx: Context) {
        debounceRunnable?.let { handler.removeCallbacks(it) }
        debounceRunnable = null
        flush(ctx.applicationContext, force = true)
    }

    fun flush(ctx: Context, force: Boolean) {
        Thread {
            try {
                val store = SettingsStore(ctx)
                if (!store.autoBackup) return@Thread
                if (!isDirty(ctx)) return@Thread
                if (destinations(ctx).isEmpty()) return@Thread
                if (!force) {
                    val last = statePrefs(ctx).getLong("last_wall_at", 0L)
                    if (System.currentTimeMillis() - last < THROTTLE_MS) return@Thread
                }
                backupAll(ctx)
            } catch (e: Exception) { }
        }.start()
    }
}
