package com.zamerplan.app.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class ZamerStatus(val label: String) {
    PLANNED("Запланирован"),
    DONE("Выполнено"),
    POSTPONED("Перенесён"),
    CANCELLED("Отменён")
}

data class Zamer(
    val id: Long,
    val date: LocalDate,
    val time: LocalTime,
    val timeEnd: String,
    val name: String,
    val phone: String,
    val contactFrom: String,
    val address: String,
    val area: String,
    val thickness: String,
    val price: String,
    val comment: String,
    val voiceFile: String,
    val status: ZamerStatus
) {
    fun timeText(): String =
        if (timeEnd.isNotBlank()) time.format(DateTimeFormatter.ofPattern("HH:mm")) + "–" + timeEnd
        else time.format(DateTimeFormatter.ofPattern("HH:mm"))

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("date", date.toString())
        put("time", time.format(DateTimeFormatter.ofPattern("HH:mm")))
        put("timeEnd", timeEnd)
        put("name", name)
        put("phone", phone)
        put("contactFrom", contactFrom)
        put("address", address)
        put("area", area)
        put("thickness", thickness)
        put("price", price)
        put("comment", comment)
        put("voiceFile", voiceFile)
        put("status", status.name)
    }

    companion object {
        fun fromJson(o: JSONObject): Zamer = Zamer(
            id = o.optLong("id", 0L),
            date = try { LocalDate.parse(o.optString("date", "")) } catch (e: Exception) { LocalDate.now() },
            time = try { LocalTime.parse(o.optString("time", "12:00")) } catch (e: Exception) { LocalTime.NOON },
            timeEnd = o.optString("timeEnd", ""),
            name = o.optString("name", ""),
            phone = o.optString("phone", ""),
            contactFrom = o.optString("contactFrom", ""),
            address = o.optString("address", ""),
            area = o.optString("area", ""),
            thickness = o.optString("thickness", ""),
            price = o.optString("price", ""),
            comment = o.optString("comment", ""),
            voiceFile = o.optString("voiceFile", ""),
            status = try { ZamerStatus.valueOf(o.optString("status", "PLANNED")) } catch (e: Exception) { ZamerStatus.PLANNED }
        )
    }
}

class Storage(context: Context) {
    private val prefs = context.getSharedPreferences("zamer_storage", Context.MODE_PRIVATE)
    private val ctx = context

    fun load(): List<Zamer> {
        val json = prefs.getString("zamers_json", null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { Zamer.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) { emptyList() }
    }

    fun save(list: List<Zamer>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("zamers_json", arr.toString()).apply()
    }

    fun deleteVoice(id: Long) {
        java.io.File(ctx.filesDir, "voice_$id.m4a").delete()
    }
}

object ZamerParser {

    data class Parsed(
        val phone: String, val address: String, val area: String,
        val thickness: String, val comment: String, val contactFrom: String,
        val name: String, val price: String,
        val dateOffset: Int?, val timeStr: String
    )

    private val PHONE = Regex("(?:\\+7|8)?[\\s(]*\\d{3}[)\\s\\-]*\\d{3}[\\s\\-]*\\d{2}[\\s\\-]*\\d{2}")
    private val AREA = Regex("(\\d{1,4}(?:[.,]\\d+)?)\\s*(?:м²|м\\s?2|кв\\.?\\s*м\\.?|квадрат\\w*|площад\\w*)", RegexOption.IGNORE_CASE)
    private val THICK = Regex("(?:толщина\\s+(?:стяжки?\\s+)?)?(?:примерно\\s+)?(\\d{1,3})\\s*(?:[-–]\\s*(\\d{1,3}))?\\s*(см|сантиметр\\w*|мм|миллиметр\\w*)", RegexOption.IGNORE_CASE)
    private val PRICE = Regex("(\\d{2,6})\\s*(?:₽|руб\\w*|р\\.|тыс\\.?)", RegexOption.IGNORE_CASE)
    private val FROM = Regex("от\\s*[:\\-]?\\s*([А-ЯЁA-Z][а-яёa-z\\-]+)", RegexOption.IGNORE_CASE)
    private val NAME_HINT = Regex("(?:имя|клиент|зовут|звонил\\w*|покупател\\w*)\\s*[:\\-]?\\s*([А-ЯЁA-Z][а-яёa-z\\-]+)", RegexOption.IGNORE_CASE)
    private val TIME_COLON = Regex("(?:в|на|к)\\s*(\\d{1,2})[:.](\\d{2})", RegexOption.IGNORE_CASE)
    private val TIME_HOUR = Regex("(?:в|на|к)\\s*(\\d{1,2})\\s*(?:час|ч\\b)", RegexOption.IGNORE_CASE)
    private val ADDR_WORDS = Regex("(ул\\.?|улиц\\w+|проспект|пр-?кт|просп\\.?|дом|д\\.?|корп\\.?|корпус|кв\\.?|квартир\\w+|мкр\\.?|микрорайон|жк|шоссе|бульвар|б-?р|переулок|пер\\.?|проезд|набережн\\w+|этаж|эт\\.?|стр\\.?|вл\\.?|владен\\w+|район|область|город|г\\.?|москв\\w+|башн\\w+|новостройк\\w+)", RegexOption.IGNORE_CASE)
    private val STOP = Regex("(лифт|паркинг|парковк|разгруз|домофон|въезд|мусоропровод|встретит\\w+|наберит\\w+|позвонит\\w+)", RegexOption.IGNORE_CASE)
    private val COMMENT_HINTS = Regex("(есть|нужно|будет|примерно|лифт|паркинг|разгруз|домофон|звонить|набрать|этаж|подвал|чердак|ключи|охрана)", RegexOption.IGNORE_CASE)
    private val DANGLE = Regex("(квартир\\w*|толщин\\w*|стяжк\\w*|примерно|площад\\w*)(?![0-9а-яёa-z])", RegexOption.IGNORE_CASE)

    private fun cleanAddr(s: String): String = s
        .replace(Regex("\\s+"), " ")
        .replace(DANGLE, " ")
        .replace(Regex("(дом|д|корп\\.?|корпус)\\s*(?=[.,;:]|$)", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("\\s+\\.\\s+"), ". ")
        .replace(Regex("\\.\\s*\\."), ".")
        .replace(Regex("\\s+([.,;:])"), "$1")
        .replace(Regex("\\s+"), " ")
        .trim().trimEnd('.', ',', ' ', ';')
        .trimStart('.', ',', ' ', ';')
        .trim()

    fun parse(raw: String): Parsed {
        return try {
            var text = raw.replace("\r", "").trim()
            var phone = ""; var area = ""; var thick = ""; var contact = ""
            var name = ""; var price = ""; var dateOffset: Int? = null; var timeStr = ""

            when {
                Regex("послезавтра", RegexOption.IGNORE_CASE).containsMatchIn(text) -> dateOffset = 2
                Regex("завтра", RegexOption.IGNORE_CASE).containsMatchIn(text) -> dateOffset = 1
                Regex("сегодня", RegexOption.IGNORE_CASE).containsMatchIn(text) -> dateOffset = 0
            }

            TIME_COLON.find(text)?.let { m ->
                timeStr = m.groupValues[1].padStart(2, '0') + ":" + m.groupValues[2]
                text = text.removeRange(m.range)
            } ?: TIME_HOUR.find(text)?.let { m ->
                timeStr = m.groupValues[1].padStart(2, '0') + ":00"
                text = text.removeRange(m.range)
            }

            PHONE.find(text)?.let { m -> phone = m.value.trim(); text = text.removeRange(m.range) }
            FROM.find(text)?.let { m -> contact = m.groupValues[1]; text = text.removeRange(m.range) }
            NAME_HINT.find(text)?.let { m -> name = m.groupValues[1]; text = text.removeRange(m.range) }
            AREA.find(text)?.let { m -> area = m.groupValues[1].replace(",", ".") + " м²"; text = text.removeRange(m.range) }
            THICK.find(text)?.let { m ->
                val a = m.groupValues[1]; val b = m.groupValues[2]
                val unit = m.groupValues[3].lowercase()
                val isMm = unit.startsWith("мм") || unit.startsWith("милл")
                fun conv(v: String) = if (isMm) (((v.toIntOrNull() ?: 0) / 10).toString()) else v
                thick = if (b.isNotEmpty()) conv(a) + "-" + conv(b) + " см" else conv(a) + " см"
                text = text.removeRange(m.range)
            }
            PRICE.find(text)?.let { m -> price = m.groupValues[1]; text = text.removeRange(m.range) }

            val addressParts = mutableListOf<String>()
            val commentParts = mutableListOf<String>()

            text.split("\n").forEach { rawLine ->
                var line = rawLine.trim()
                if (line.isBlank()) return@forEach
                val stopMatch = STOP.find(line)
                var tail = ""
                if (stopMatch != null) {
                    tail = line.substring(stopMatch.range.first)
                    line = line.substring(0, stopMatch.range.first).trim()
                }
                if (line.isBlank()) {
                    if (tail.isNotBlank()) commentParts.add(tail)
                    return@forEach
                }
                val words = line.split(Regex("\\s+"))
                when {
                    ADDR_WORDS.containsMatchIn(line) -> addressParts.add(line)
                    words.size == 1 && line.length <= 15 &&
                        words[0].first().isUpperCase() && !line.any { it.isDigit() } ->
                        if (name.isBlank()) name = line else commentParts.add(line)
                    words.size in 2..4 && !line.any { it.isDigit() } &&
                        !COMMENT_HINTS.containsMatchIn(line) -> addressParts.add(line)
                    else -> commentParts.add(line)
                }
                if (tail.isNotBlank()) commentParts.add(tail)
            }

            var address = cleanAddr(addressParts.joinToString(", "))
            var comment = commentParts.joinToString(" · ").trim()
            if (address.isBlank()) {
                if (ADDR_WORDS.containsMatchIn(text)) address = cleanAddr(text)
            }

            Parsed(phone, address, area, thick, comment, contact, name, price, dateOffset, timeStr)
        } catch (e: Exception) {
            Parsed("", "", "", "", raw.trim(), "", "", "", null, "")
        }
    }
}
