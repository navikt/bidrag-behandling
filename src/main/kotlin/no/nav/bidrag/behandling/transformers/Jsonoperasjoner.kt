package no.nav.bidrag.behandling.transformers

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import no.nav.bidrag.behandling.database.grunnlag.GrunnlagInntekt
import no.nav.bidrag.transport.behandling.grunnlag.response.ArbeidsforholdGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import java.lang.reflect.Type
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class Jsonoperasjoner {
    companion object {
        fun <T> objektTilJson(objekt: T): String =
            GsonBuilder()
                .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter())
                .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter())
                .registerTypeAdapter(YearMonth::class.java, YearMonthTypeAdapter()).create()
                .toJson(
                    objekt,
                )

        fun jsonTilGrunnlagInntekt(json: String): GrunnlagInntekt =
            GsonBuilder()
                .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter())
                .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter())
                .registerTypeAdapter(YearMonth::class.java, YearMonthTypeAdapter()).create()
                .fromJson(
                    json,
                    GrunnlagInntekt::class.java,
                )

        fun jsonTilRelatertPersonGrunnlagDto(json: String): Set<RelatertPersonGrunnlagDto> {
            val targetClassType: Type = object : TypeToken<ArrayList<RelatertPersonGrunnlagDto?>?>() {}.type

            return GsonBuilder()
                .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter())
                .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter())
                .registerTypeAdapter(YearMonth::class.java, YearMonthTypeAdapter()).create()
                .fromJson<Set<RelatertPersonGrunnlagDto>?>(
                    json,
                    targetClassType,
                ).toSet()
        }

        fun jsonTilSivilstandGrunnlagDto(json: String): Set<SivilstandGrunnlagDto> {
            val targetClassType: Type = object : TypeToken<ArrayList<SivilstandGrunnlagDto?>?>() {}.type

            return GsonBuilder()
                .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter())
                .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter())
                .registerTypeAdapter(YearMonth::class.java, YearMonthTypeAdapter()).create()
                .fromJson<Set<SivilstandGrunnlagDto>?>(
                    json,
                    targetClassType,
                ).toSet()
        }

        fun jsonTilArbeidsforholdGrunnlagDto(json: String): Set<ArbeidsforholdGrunnlagDto> {
            val targetClassType: Type = object : TypeToken<ArrayList<ArbeidsforholdGrunnlagDto?>?>() {}.type

            return GsonBuilder()
                .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter())
                .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter())
                .registerTypeAdapter(YearMonth::class.java, YearMonthTypeAdapter()).create()
                .fromJson<Set<ArbeidsforholdGrunnlagDto>?>(
                    json,
                    targetClassType,
                ).toSet()
        }
    }
}
