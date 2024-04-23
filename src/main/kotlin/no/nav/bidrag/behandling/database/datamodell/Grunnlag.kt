package no.nav.bidrag.behandling.database.datamodell

import com.fasterxml.jackson.module.kotlin.readValue
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import no.nav.bidrag.behandling.database.grunnlag.SummerteInntekter
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.objectmapper
import no.nav.bidrag.transport.behandling.inntekt.response.SummertÅrsinntekt
import org.hibernate.annotations.ColumnTransformer
import java.time.LocalDateTime

@Entity(name = "grunnlag")
@Schema(name = "GrunnlagEntity")
open class Grunnlag(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "behandling_id", nullable = false)
    open val behandling: Behandling,
    @Enumerated(EnumType.STRING)
    open val type: Grunnlagsdatatype,
    open val erBearbeidet: Boolean = false,
    @Column(name = "data", columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    open val data: String,
    open val innhentet: LocalDateTime,
    open var aktiv: LocalDateTime? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rolle_id", nullable = false)
    open val rolle: Rolle,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open val id: Long? = null,
) {
    override fun toString(): String {
        return try {
            "Grunnlag($type, erBearbeidet=$erBearbeidet, rolle=${rolle.rolletype}, ident=${rolle.ident}, aktiv=$aktiv, " +
                "id=$id, behandling=${behandling.id}, innhentet=$innhentet)"
        } catch (e: Exception) {
            "Grunnlag($type, erBearbeidet=$erBearbeidet, aktiv=$aktiv, id=$id, innhentet=$innhentet)"
        }
    }
}

fun List<Grunnlag>.hentAlleIkkeAktiv() = filter { it.innhentet != null }.sortedByDescending { it.innhentet }.filter { g -> g.aktiv == null }

fun List<Grunnlag>.hentAlleAktiv() = filter { it.innhentet != null }.sortedByDescending { it.innhentet }.filter { g -> g.aktiv != null }

fun List<Grunnlag>.hentSisteIkkeAktiv() =
    hentAlleIkkeAktiv().groupBy { it.type.name + it.erBearbeidet.toString() }
        .mapValues { (_, grunnlagList) -> grunnlagList.maxByOrNull { it.innhentet } }
        .values
        .filterNotNull()

fun List<Grunnlag>.hentSisteAktiv() =
    hentAlleAktiv().groupBy { it.type.name + it.erBearbeidet.toString() }
        .mapValues { (_, grunnlagList) -> grunnlagList.maxByOrNull { it.innhentet } }
        .values
        .filterNotNull()

fun List<Grunnlag>.harInntekterForTypeSomIkkeErBearbeidet(
    type: Grunnlagsdatatype,
    ident: String,
) = any {
    it.type == type && !it.erBearbeidet && it.rolle.ident == ident
}

fun List<Grunnlag>.hentGrunnlagForType(
    type: Grunnlagsdatatype,
    ident: String,
) = filter {
    it.type == type && it.rolle.ident == ident
}

fun List<Grunnlag>.hentBearbeidetInntekterForType(
    type: Grunnlagsdatatype,
    ident: String,
) = find {
    it.type == type && it.erBearbeidet && it.rolle.ident == ident
}.konverterData<SummerteInntekter<SummertÅrsinntekt>>()

inline fun <reified T> Grunnlag?.konverterData(): T? {
    return this?.data?.let {
        objectmapper.readValue(it)
    }
}

inline fun <reified T> Grunnlag?.konverterData2(): Set<T>? {
    return this?.data?.let {
        objectmapper.readValue(it)
    }
}
