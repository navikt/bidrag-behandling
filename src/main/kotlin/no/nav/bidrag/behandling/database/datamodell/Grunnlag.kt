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
import no.nav.bidrag.boforhold.dto.BoforholdResponse
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
    open var data: String,
    open var innhentet: LocalDateTime,
    open var aktiv: LocalDateTime? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rolle_id", nullable = false)
    open val rolle: Rolle,
    open val gjelder: String? = null,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open val id: Long? = null,
) {
    override fun toString(): String {
        return try {
            "Grunnlag($type, erBearbeidet=$erBearbeidet, rolle=${rolle.rolletype}, ident=${rolle.ident}, aktiv=$aktiv, " +
                "id=$id, behandling=${behandling.id}, innhentet=$innhentet, gjelder=$gjelder)"
        } catch (e: Exception) {
            "Grunnlag($type, erBearbeidet=$erBearbeidet, aktiv=$aktiv, id=$id, innhentet=$innhentet, gjelder=$gjelder)"
        }
    }

    val identifikator get() = type.name + rolle.ident + erBearbeidet + gjelder
}

fun Set<Grunnlag>.hentAlleIkkeAktiv() = sortedByDescending { it.innhentet }.filter { g -> g.aktiv == null }

fun Set<Grunnlag>.hentAlleAktiv() = sortedByDescending { it.innhentet }.filter { g -> g.aktiv != null }

fun Set<Grunnlag>.hentSisteIkkeAktiv() =
    hentAlleIkkeAktiv().groupBy { it.identifikator }
        .mapValues { (_, grunnlagList) -> grunnlagList.maxByOrNull { it.innhentet } }
        .values
        .filterNotNull()

fun Set<Grunnlag>.hentSisteAktiv() =
    hentAlleAktiv().groupBy { it.identifikator }
        .mapValues { (_, grunnlagList) -> grunnlagList.maxByOrNull { it.innhentet } }
        .values
        .filterNotNull()

fun Husstandsbarn.hentSisteBearbeidetBoforhold() =
    behandling.grunnlag.hentSisteAktiv()
        .find { it.erBearbeidet && it.type == Grunnlagsdatatype.BOFORHOLD && it.gjelder == this.ident }
        .konverterData<List<BoforholdResponse>>()

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
