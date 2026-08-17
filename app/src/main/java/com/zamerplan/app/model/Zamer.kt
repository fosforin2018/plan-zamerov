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
    val name: String,
    val phone: String,
    val contactFrom: String,
    val address: String,
    val area: String,
    val thickness: String,
    val price: String,
    val status: ZamerStatus
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("date", date.toString())
        put("time", time.format(DateTimeFormatter.ofPattern("HH:mm")))
        put("name", name)
        put("phone", phone)
        put("contactFrom", contactFrom)
        put("address", address)
        put("area", area)
        put("thickness", thickness)
        put("price", price)
        put("status", status.name)
    }

    companion object {
        fun fromJson(o: JSONObject): Zamer = Zamer(
            id = o.optLong("id", 0L),
            date = try { LocalDate.parse(o.optString("date", "")) } catch (e: Exception) { LocalDate.now() },
            time = try { LocalTime.parse(o.optString("time", "12:00")) } catch (e: Exception) { LocalTime.NOON },
            name = o.optString("name", ""),
            phone = o.optString("phone", ""),
            contactFrom = o.optString("contactFrom", ""),
            address = o.optString("address", ""),
            area = o.optString("area", ""),
            thickness = o.optString("thickness", ""),
            price = o.optString("price", ""),
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
