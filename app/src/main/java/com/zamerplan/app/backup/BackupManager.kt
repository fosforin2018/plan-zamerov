package com.zamerplan.app.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Base64
import com.zamerplan.app.alarm.SettingsStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.net.ssl.HttpsURLConnection

object BackupManager {

    const val FORMAT_VERSION = 1
    const val FILE_NAME = "ZamerPlan_backup.zip"
    private const val REMOTE_PATH = "/ZamerPlan_backup.zip"

    // ============================================================
    // ВСТАВЬ СЮДА значения со страницы oauth.yandex.ru
    // ============================================================
    private const val YANDEX_CLIENT_ID = "ВСТАВЬ_CLIENT_ID_СЮДА"
    private const val YANDEX_CLIENT_SECRET = "ВСТАВЬ_CLIENT_SECRET_СЮДА"
    private const val YANDEX_REDIRECT = "zamerplan://backup-auth"

    // ============================================================
    // ВСТАВЬ СЮДА токен бота от @BotFather и его username
    // ============================================================
    private const val TELEGRAM_BOT_TOKEN = "ВСТАВЬ_ТОКЕН_БОТА_СЮДА"
    private const val TELEGRAM_BOT_USERNAME = "@твой_бот"

    data class Destination(
        val type: String, // "saf" | "yandex" | "mailru" | "telegram"
        val uri: String,
        val login: String,
        val password: String,
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

    fun cloudTitle(type: String): String = when (type) {
        "yandex" -> "Яндекс Диск"
        "mailru" -> "Облако Mail.ru"
        "telegram" -> "Telegram"
        else -> "Файл на телефоне"
    }

    // ============================================================
    // СПИСОК ПОДКЛЮЧЕНИЙ
    // ============================================================

    fun destinations(ctx: Context): List<Destination> {
        val prefs = ctx.getSharedPreferences("backup_destinations", Context.MODE_PRIVATE)
        val count = prefs.getInt("count", 0)
        return (0 until count).mapNotNull { i ->
            val type = prefs.getString("type_$i", "saf") ?: "saf"
            val uri = prefs.getString("uri_$i", "") ?: ""
            if (type == "saf" && uri.isBlank()) return@mapNotNull null
            Destination(
                type = type,
                uri = uri,
                login = prefs.getString("login_$i", "") ?: "",
                password = prefs.getString("pass_$i", "") ?: "",
                name = prefs.getString("name_$i", cloudTitle(type)) ?: cloudTitle(type),
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
                ed.remove("type_$i").remove("uri_$i").remove("login_$i")
                ed.remove("pass_$i").remove("name_$i").remove("ok_$i").remove("err_$i")
            }
        }
        list.forEachIndexed { i, d ->
            ed.putString("type_$i", d.type)
            ed.putString("uri_$i", d.uri)
            ed.putString("login_$i", d.login)
            ed.putString("pass_$i", d.password)
            ed.putString("name_$i", d.name)
            ed.putLong("ok_$i", d.lastOk)
            ed.putString("err_$i", d.lastError)
        }
        ed.apply()
    }

    private fun updateDest(ctx: Context, index: Int, transform: (Destination) -> Destination) {
        val list = destinations(ctx).toMutableList()
        if (index in list.indices) {
            list[index] = transform(list[index])
            saveDestinations(ctx, list)
        }
    }

    fun addSafDestination(ctx: Context, uri: Uri, name: String) {
        try {
            ctx.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) { }
        val list = destinations(ctx).toMutableList()
        val uriStr = uri.toString()
        if (list.none { it.type == "saf" && it.uri == uriStr }) {
            list.add(Destination("saf", uriStr, "", "", name, 0L, ""))
            saveDestinations(ctx, list)
        }
    }

    fun addCloudDestination(ctx: Context, type: String, login: String, password: String) {
        val list = destinations(ctx).toMutableList()
        list.removeAll { it.type == type }
        list.add(Destination(type, "", login.trim(), password.trim(), cloudTitle(type), 0L, ""))
        saveDestinations(ctx, list)
    }

    fun addTelegramDestination(ctx: Context, chatId: String, chatName: String) {
        val list = destinations(ctx).toMutableList()
        list.removeAll { it.type == "telegram" }
        val name = if (chatName.isBlank()) "Telegram" else "Telegram · $chatName"
        list.add(Destination("telegram", "", chatId, "", name, 0L, ""))
        saveDestinations(ctx, list)
    }

    fun removeDestination(ctx: Context, index: Int) {
        val list = destinations(ctx).toMutableList()
        val d = list.getOrNull(index)
        if (d != null && d.type == "saf") {
            try {
                ctx.contentResolver.releasePersistableUriPermission(
                    Uri.parse(d.uri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) { }
        }
        if (index in list.indices) {
            list.removeAt(index)
            saveDestinations(ctx, list)
        }
    }

    fun displayName(ctx: Context, uri: Uri): String {
        val file = try {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        } catch (e: Exception) { null }
        return if (file.isNullOrBlank()) "Файл на телефоне" else "Файл: $file"
    }

    fun createDocumentIntent(): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = "application/zip"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_TITLE, FILE_NAME)
        }

    // ============================================================
    // TELEGRAM: подключение и загрузка через бота
    // ============================================================

    fun telegramConfigured(): Boolean =
        TELEGRAM_BOT_TOKEN.isNotBlank() && !TELEGRAM_BOT_TOKEN.startsWith("ВСТАВЬ")

    fun telegramBotName(): String = TELEGRAM_BOT_USERNAME

    private fun httpGetJson(url: String): JSONObject? = try {
        val conn = URL(url).openConnection() as HttpsURLConnection
        try {
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            if (conn.responseCode == 200) {
                JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            } else {
                null
            }
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        null
    }

    fun telegramConnect(ctx: Context): String? {
        if (!telegramConfigured()) return "Telegram не настроен в приложении (токен бота)"
        val json = httpGetJson("https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/getUpdates")
            ?: return "Нет ответа от Telegram (проверь интернет или токен бота)"
        if (!json.optBoolean("ok")) return "Ошибка Telegram: " + json.optString("description")
        val arr = json.optJSONArray("result") ?: return "Открой бота в Telegram и нажми START"
        for (i in arr.length() - 1 downTo 0) {
            val u = arr.optJSONObject(i) ?: continue
            val msg = u.optJSONObject("message") ?: continue
            val chat = msg.optJSONObject("chat") ?: continue
            val chatId = chat.optLong("id", 0L)
            if (chatId == 0L) continue
            val who = (chat.optString("first_name", "") + " " + chat.optString("last_name", "")).trim()
            addTelegramDestination(ctx, chatId.toString(), who)
            return null
        }
        return "Бот не получил сообщение. Открой бота в Telegram, нажми START и повтори подключение"
    }

    private fun telegramUpload(d: Destination, src: File): String {
        if (src.length() > 49L * 1024 * 1024) {
            throw IOException("файл больше 50 МБ — Telegram не примет")
        }
        val boundary = "ZPboundary" + System.currentTimeMillis()
        val conn = URL("https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/sendDocument")
            .openConnection() as HttpsURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 20000
            conn.readTimeout = 180000
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            DataOutputStream(conn.outputStream).use { dos ->
                dos.write(
                    ("--$boundary\r\nContent-Disposition: form-data; name=\"chat_id\"\r\n\r\n" + d.login + "\r\n")
                        .toByteArray(Charsets.UTF_8)
                )
                dos.write(
                    ("--$boundary\r\nContent-Disposition: form-data; name=\"document\"; filename=\"$FILE_NAME\"\r\nContent-Type: application/zip\r\n\r\n")
                        .toByteArray(Charsets.UTF_8)
                )
                src.inputStream().use { it.copyTo(dos) }
                dos.write(("\r\n--$boundary--\r\n").toByteArray(Charsets.UTF_8))
                dos.flush()
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                throw IOException(
                    if (code == 400 || code == 403) "чат недоступен — подключите Telegram заново"
                    else "Telegram ответил ошибкой $code"
                )
            }
            val obj = JSONObject(text)
            if (!obj.optBoolean("ok")) throw IOException("Telegram: " + obj.optString("description"))
            val fileId = obj.optJSONObject("result")?.optJSONObject("document")?.optString("file_id") ?: ""
            if (fileId.isBlank()) throw IOException("Telegram не вернул файл")
            return fileId
        } finally {
            conn.disconnect()
        }
    }

    private fun telegramDownload(d: Destination, dst: File): Boolean {
        val fileId = d.uri
        if (fileId.isBlank()) return false
        val gj = httpGetJson(
            "https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/getFile?file_id=" + Uri.encode(fileId)
        ) ?: return false
        if (!gj.optBoolean("ok")) return false
        val path = gj.optJSONObject("result")?.optString("file_path") ?: return false
        if (path.isBlank()) return false
        val conn = URL("https://api.telegram.org/file/bot$TELEGRAM_BOT_TOKEN/$path")
            .openConnection() as HttpsURLConnection
        return try {
            conn.connectTimeout = 20000
            conn.readTimeout = 180000
            if (conn.responseCode == 200) {
                conn.inputStream.use { ins -> dst.outputStream().use { ins.copyTo(it) } }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        } finally {
            conn.disconnect()
        }
    }

    // ============================================================
    // ЯНДЕКС: OAuth + прямая загрузка
    // ============================================================

    fun yandexConfigured(): Boolean =
        !YANDEX_CLIENT_ID.startsWith("ВСТАВЬ") && !YANDEX_CLIENT_SECRET.startsWith("ВСТАВЬ")

    fun yandexAuthIntent(): Intent =
        Intent(
            Intent.ACTION_VIEW,
            Uri.parse(
                "https://oauth.yandex.ru/authorize?response_type=code" +
                    "&client_id=" + YANDEX_CLIENT_ID +
                    "&redirect_uri=" + Uri.encode(YANDEX_REDIRECT)
            )
        )

    fun onAuthRedirect(ctx: Context, uri: Uri) {
        val code = uri.getQueryParameter("code") ?: ""
        val err = uri.getQueryParameter("error") ?: ""
        ctx.getSharedPreferences("backup_state", Context.MODE_PRIVATE)
            .edit()
            .putString("pending_code", code)
            .putString("pending_error", err)
            .apply()
    }

    fun takePendingAuth(ctx: Context): Pair<String?, String?> {
        val prefs = ctx.getSharedPreferences("backup_state", Context.MODE_PRIVATE)
        val code = prefs.getString("pending_code", "") ?: ""
        val err = prefs.getString("pending_error", "") ?: ""
        prefs.edit().remove("pending_code").remove("pending_error").apply()
        return Pair(code.takeIf { it.isNotBlank() }, err.takeIf { it.isNotBlank() })
    }

    fun finishYandexConnect(ctx: Context, code: String): String? {
        return try {
            val body = "grant_type=authorization_code" +
                "&code=" + Uri.encode(code) +
                "&client_id=" + Uri.encode(YANDEX_CLIENT_ID) +
                "&client_secret=" + Uri.encode(YANDEX_CLIENT_SECRET) +
                "&redirect_uri=" + Uri.encode(YANDEX_REDIRECT)
            val conn = URL("https://oauth.yandex.ru/token").openConnection() as HttpsURLConnection
            try {
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val rc = conn.responseCode
                val stream = if (rc in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
                if (rc !in 200..299) return "Яндекс отклонил подключение (код $rc)"
                val token = JSONObject(text).optString("access_token")
                if (token.isBlank()) return "Не удалось получить ключ доступа"
                addCloudDestination(ctx, "yandex", "", token)
                null
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            "Ошибка сети при подключении: ${e.message}"
        }
    }

    private fun yandexUpload(d: Destination, src: File) {
        val token = d.password
        val meta = URL(
            "https://cloud-api.yandex.net/v1/disk/resources/upload?path=" +
                Uri.encode(REMOTE_PATH) + "&overwrite=true"
        ).openConnection() as HttpsURLConnection
        val href: String
        try {
            meta.requestMethod = "GET"
            meta.connectTimeout = 20000
            meta.readTimeout = 30000
            meta.setRequestProperty("Authorization", "OAuth $token")
            val rc = meta.responseCode
            if (rc == 401 || rc == 403) throw IOException("связь потеряна — подключите Яндекс заново")
            if (rc != 200) throw IOException("Яндекс Диск ответил ошибкой $rc")
            href = JSONObject(meta.inputStream.bufferedReader().use { it.readText() }).optString("href")
        } finally {
            meta.disconnect()
        }
        if (href.isBlank()) throw IOException("Яндекс Диск не дал ссылку для загрузки")
        val put = URL(href).openConnection() as HttpsURLConnection
        try {
            put.requestMethod = "PUT"
            put.doOutput = true
            put.connectTimeout = 20000
            put.readTimeout = 120000
            put.setRequestProperty("Authorization", "OAuth $token")
            put.outputStream.use { os -> src.inputStream().use { it.copyTo(os) } }
            val pc = put.responseCode
            if (pc !in 200..299) throw IOException("не удалось загрузить файл (код $pc)")
        } finally {
            put.disconnect()
        }
    }

    private fun yandexDownload(d: Destination, dst: File): Boolean {
        val token = d.password
        return try {
            val meta = URL(
                "https://cloud-api.yandex.net/v1/disk/resources/download?path=" + Uri.encode(REMOTE_PATH)
            ).openConnection() as HttpsURLConnection
            val href: String
            try {
                meta.requestMethod = "GET"
                meta.connectTimeout = 20000
                meta.readTimeout = 30000
                meta.setRequestProperty("Authorization", "OAuth $token")
                if (meta.responseCode != 200) return false
                href = JSONObject(meta.inputStream.bufferedReader().use { it.readText() }).optString("href")
            } finally {
                meta.disconnect()
            }
            if (href.isBlank()) return false
            val get = URL(href).openConnection() as HttpURLConnection
            try {
                get.connectTimeout = 20000
                get.readTimeout = 120000
                if (get.responseCode != 200) return false
                get.inputStream.use { ins -> dst.outputStream().use { ins.copyTo(it) } }
                true
            } finally {
                get.disconnect()
            }
        } catch (e: Exception) {
            false
        }
    }

    // ============================================================
    // MAIL.RU: WebDAV
    // ============================================================

    private fun basicAuth(d: Destination): String =
        "Basic " + Base64.encodeToString(
            (d.login + ":" + d.password).toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )

    private fun webdavPut(d: Destination, src: File) {
        val conn = URL("https://webdav.cloud.mail.ru" + REMOTE_PATH).openConnection() as HttpsURLConnection
        try {
            conn.requestMethod = "PUT"
            conn.doOutput = true
            conn.connectTimeout = 20000
            conn.readTimeout = 120000
            conn.setRequestProperty("Authorization", basicAuth(d))
            conn.setRequestProperty("Content-Type", "application/zip")
            conn.outputStream.use { os -> src.inputStream().use { it.copyTo(os) } }
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IOException(
                    if (code == 401 || code == 403) "неверный логин или пароль Mail.ru"
                    else "облако ответило ошибкой $code"
                )
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun webdavGet(d: Destination, dst: File): Boolean {
        val conn = URL("https://webdav.cloud.mail.ru" + REMOTE_PATH).openConnection() as HttpsURLConnection
        return try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 20000
            conn.readTimeout = 120000
            conn.setRequestProperty("Authorization", basicAuth(d))
            if (conn.responseCode == 200) {
                conn.inputStream.use { ins -> dst.outputStream().use { ins.copyTo(it) } }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        } finally {
            conn.disconnect()
        }
    }

    fun downloadTo(ctx: Context, d: Destination, dst: File): Boolean = when (d.type) {
        "yandex" -> yandexDownload(d, dst)
        "telegram" -> telegramDownload(d, dst)
        else -> webdavGet(d, dst)
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
    // СОЗДАНИЕ КОПИЙ ВО ВСЕ ПОДКЛЮЧЕНИЯ
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
            dests.forEachIndexed { i, d ->
                try {
                    var fileId: String? = null
                    when (d.type) {
                        "saf" -> writeToFile(ctx, Uri.parse(d.uri), tmp)
                        "yandex" -> yandexUpload(d, tmp)
                        "telegram" -> fileId = telegramUpload(d, tmp)
                        else -> webdavPut(d, tmp)
                    }
                    updateDest(ctx, i) {
                        it.copy(
                            lastOk = System.currentTimeMillis(),
                            lastError = "",
                            uri = fileId ?: it.uri
                        )
                    }
                    results.add(BackupResult(d, true, ""))
                } catch (e: Exception) {
                    val msg = when {
                        e is SecurityException || e is FileNotFoundException ->
                            "файл недоступен — переподключите"
                        e is IOException && e.message != null -> e.message!!
                        else -> "ошибка записи"
                    }
                    updateDest(ctx, i) { it.copy(lastError = msg) }
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
        } ?: throw IOException("не удалось открыть поток записи")
    }

    // ============================================================
    // META И ВОССТАНОВЛЕНИЕ
    // ============================================================

    fun readMeta(ctx: Context, uri: Uri): BackupMeta? = try {
        ctx.contentResolver.openInputStream(uri)?.use { ins -> readMetaFromStream(ins) }
    } catch (e: Exception) { null }

    fun readMetaFile(file: File): BackupMeta? = try {
        ZipFile(file).use { zf ->
            val e = zf.getEntry("meta.json") ?: return null
            val o = JSONObject(zf.getInputStream(e).bufferedReader().use { it.readText() })
            BackupMeta(
                formatVersion = o.optInt("format_version", 1),
                appVersion = o.optString("app_version", ""),
                createdAt = o.optString("created_at", ""),
                zamerCount = o.optInt("zamer_count", 0),
                voiceCount = o.optInt("voice_count", 0)
            )
        }
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

    fun restoreFromFile(ctx: Context, tmp: File): RestoreResult {
        return try {
            if (!verifyZip(tmp)) return RestoreResult(false, "файл повреждён или не является копией")
            val metaObj = ZipFile(tmp).use { zf ->
                val e = zf.getEntry("meta.json") ?: return RestoreResult(false, "в копии нет meta.json")
                JSONObject(zf.getInputStream(e).bufferedReader().use { it.readText() })
            }
            val fv = metaObj.optInt("format_version", 1)
            if (fv > FORMAT_VERSION) {
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
            RestoreResult(true, "Готово! Восстановлено замеров: $restoredZamers, голосовых: $restoredVoices")
        } catch (e: Exception) {
            RestoreResult(false, "ошибка восстановления: ${e.message}")
        }
    }

    fun restoreSaf(ctx: Context, uri: Uri): RestoreResult {
        val tmp = File(ctx.cacheDir, "restore_tmp.zip")
        return try {
            ctx.contentResolver.openInputStream(uri)
                ?.use { ins -> tmp.outputStream().use { ins.copyTo(it) } }
                ?: return RestoreResult(false, "не удалось открыть файл")
            restoreFromFile(ctx, tmp)
        } finally {
            tmp.delete()
        }
    }

    fun restoreCloud(ctx: Context, d: Destination): RestoreResult {
        val tmp = File(ctx.cacheDir, "restore_tmp.zip")
        return try {
            if (!downloadTo(ctx, d, tmp)) {
                return RestoreResult(false, "не удалось скачать копию (если копия больше 20 МБ — Telegram не отдаёт её боту, восстановите из другого источника)")
            }
            restoreFromFile(ctx, tmp)
        } finally {
            tmp.delete()
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
