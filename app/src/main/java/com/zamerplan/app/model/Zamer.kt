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
            status = try { ZamerStatus.valueOf(o.optString("status", "PLANNED")) } catch (e: Exception) { ZamerStatus.PLANNED }
        )
    }
}

class Storage(context: Context) {
    private val prefs = context.getSharedPreferences("zamer_storage", Context.MODE_PRIVATE)

    fun load(): List<Zamer> {
        val json = prefs.getString("zamers_json", null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { Zamer.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(list: List<Zamer>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("zamers_json", arr.toString()).apply()
    }
}

object ZamerParser {
    private val PHONE = Regex("(\\+?\\d[\\d\\s\\-()]{8,}\\d)")
    private val AREA = Regex("(\\d{1,4})\\s*(м²|м2|кв\\.?\\s*м\\.?|квадратных?\\s*метров?|кв\\.?\\s*метров?)", RegexOption.IGNORE_CASE)
    private val THICK = Regex("(\\d{1,2})\\s*(?:[-–]\\s*(\\d{1,2}))?\\s*(см|сантиметр[а-я]*)", RegexOption.IGNORE_CASE)
    private val FROM = Regex("от\\s*[:\\-]?\\s*([А-ЯA-Zа-яa-zЁё]+)", RegexOption.IGNORE_CASE)
    private val ADDR_KEY = Regex("(ул\\.?|улиц[а-я]+|проспект|пр-?кт|просп\\.?|дом|д\\.?|корп\\.?|корпус|кв\\.?|квартира|мкр\\.?|микрорайон|жк|шоссе|бульвар|переулок|пер\\.?|проезд|набережная|эт\\.|этаж)", RegexOption.IGNORE_CASE)
    private val STOP = Regex("(лифт|паркинг|парковк|разгруз|домофон|въезд)", RegexOption.IGNORE_CASE)

    data class Parsed(
        val phone: String, val address: String, val area: String,
        val thickness: String, val comment: String, val contactFrom: String
    )

    private fun String.clean(): String =
        this.replace(Regex("\\s+"), " ").trim().trimEnd('.', ',', ' ', ';')

    fun parse(raw: String): Parsed {
        var text = raw.replace("\r", "").trim()
        var phone = ""; var area = ""; var thick = ""; var contactFrom = ""

        FROM.find(text)?.let { m -> contactFrom = m.groupValues[1]; text = text.removeRange(m.range) }
        PHONE.find(text)?.let { m -> phone = m.value.trim(); text = text.removeRange(m.range) }
        AREA.find(text)?.let { m -> area = m.groupValues[1] + " м²"; text = text.removeRange(m.range) }
        THICK.find(text)?.let { m ->
            thick = if (m.groupValues[2].isNotEmpty()) m.groupValues[1] + "-" + m.groupValues[2] + " см"
                    else m.groupValues[1] + " см"
            text = text.removeRange(m.range)
        }

        var address = ""; var comment = ""
        val aStart = ADDR_KEY.find(text)?.range?.first
        if (aStart != null) {
            val before = text.substring(0, aStart)
            val rest = text.substring(aStart)
            val stop = STOP.find(rest)
            address = (stop?.let { rest.substring(0, it.range.first) } ?: rest).clean()
            comment = (before + " " + (stop?.let { rest.substring(it.range.first) } ?: "")).clean()
        } else {
            comment = text.clean()
        }
        return Parsed(phone, address, area, thick, comment, contactFrom)
    }
}
