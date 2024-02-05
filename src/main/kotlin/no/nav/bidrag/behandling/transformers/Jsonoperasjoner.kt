package no.nav.bidrag.behandling.transformers

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import no.nav.bidrag.behandling.database.grunnlag.GrunnlagInntekt
import no.nav.bidrag.transport.behandling.grunnlag.response.ArbeidsforholdGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.BarnetilleggGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.BarnetilsynGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.KontantstøtteGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.UtvidetBarnetrygdOgSmaabarnstilleggGrunnlagDto
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

        fun jsonTilBarnetilleggGrunnlagDto(json: String): Set<BarnetilleggGrunnlagDto> {
            val targetClassType: Type = object : TypeToken<ArrayList<BarnetilleggGrunnlagDto?>?>() {}.type

            return GsonBuilder()
                .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter())
                .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter())
                .registerTypeAdapter(YearMonth::class.java, YearMonthTypeAdapter()).create()
                .fromJson<Set<BarnetilleggGrunnlagDto>?>(
                    json,
                    targetClassType,
                ).toSet()
        }

        fun jsonTilBarnetilsynGrunnlagDto(json: String): Set<BarnetilsynGrunnlagDto> {
            val targetClassType: Type = object : TypeToken<ArrayList<BarnetilsynGrunnlagDto?>?>() {}.type

            return GsonBuilder()
                .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter())
                .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter())
                .registerTypeAdapter(YearMonth::class.java, YearMonthTypeAdapter()).create()
                .fromJson<Set<BarnetilsynGrunnlagDto>?>(
                    json,
                    targetClassType,
                ).toSet()
        }

        fun jsonTilKontantstøtteGrunnlagDto(json: String): Set<KontantstøtteGrunnlagDto> {
            val targetClassType: Type = object : TypeToken<ArrayList<KontantstøtteGrunnlagDto?>?>() {}.type

            return GsonBuilder()
                .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter())
                .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter())
                .registerTypeAdapter(YearMonth::class.java, YearMonthTypeAdapter()).create()
                .fromJson<Set<KontantstøtteGrunnlagDto>?>(
                    json,
                    targetClassType,
                ).toSet()
        }

        fun jsonTilUtvidetBarnetrygdOgSmaabarnstilleggGrunnlagDto(json: String): Set<UtvidetBarnetrygdOgSmaabarnstilleggGrunnlagDto> {
            val targetClassType: Type = object : TypeToken<ArrayList<UtvidetBarnetrygdOgSmaabarnstilleggGrunnlagDto?>?>() {}.type

            return GsonBuilder()
                .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter())
                .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter())
                .registerTypeAdapter(YearMonth::class.java, YearMonthTypeAdapter()).create()
                .fromJson<Set<UtvidetBarnetrygdOgSmaabarnstilleggGrunnlagDto>?>(
                    json,
                    targetClassType,
                ).toSet()
        }
    }
}
