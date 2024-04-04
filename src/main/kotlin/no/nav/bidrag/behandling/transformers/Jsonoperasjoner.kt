package no.nav.bidrag.behandling.transformers

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class Jsonoperasjoner {
    companion object {
        fun <T> tilJson(objekt: T): String =
            GsonBuilder()
                .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter())
                .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter())
                .registerTypeAdapter(YearMonth::class.java, YearMonthTypeAdapter()).create()
                .toJson(
                    objekt,
                )

        fun <T> tilJson(sett: Set<T>): String =
            GsonBuilder()
                .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter())
                .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter())
                .registerTypeAdapter(YearMonth::class.java, YearMonthTypeAdapter()).create()
                .toJson(
                    sett,
                )

        inline fun <reified T> jsonListeTilObjekt(json: String) =
            GsonBuilder()
                .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter())
                .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter())
                .registerTypeAdapter(YearMonth::class.java, YearMonthTypeAdapter()).create()
                .fromJsonList<T>(json).toSet()

        inline fun <reified T> jsonTilObjekt(json: String) =
            GsonBuilder()
                .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter())
                .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter())
                .registerTypeAdapter(YearMonth::class.java, YearMonthTypeAdapter()).create()
                .fromJson<T>(json, genericType<T>())
    }
}

inline fun <reified T> Gson.fromJsonList(json: String) = fromJson<List<T>>(json, object : TypeToken<List<T>>() {}.type)

inline fun <reified T> genericType(): Type = object : TypeToken<T?>() {}.type
