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
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagstype
import no.nav.bidrag.behandling.objectmapper
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.jsonListeTilObjekt
import no.nav.bidrag.boforhold.dto.BoforholdResponse
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.sivilstand.dto.Sivilstand
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto
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
    override fun toString(): String =
        try {
            "Grunnlag($type, erBearbeidet=$erBearbeidet, rolle=${rolle.rolletype}, ident=${rolle.ident}, aktiv=$aktiv, " +
                "id=$id, behandling=${behandling.id}, innhentet=$innhentet, gjelder=$gjelder)"
        } catch (e: Exception) {
            "Grunnlag($type, erBearbeidet=$erBearbeidet, aktiv=$aktiv, id=$id, innhentet=$innhentet, gjelder=$gjelder)"
        }

    val identifikator get() = type.name + rolle.ident + erBearbeidet + gjelder
}

fun Set<Grunnlag>.hentAlleIkkeAktiv() = sortedByDescending { it.innhentet }.filter { g -> g.aktiv == null }

fun Set<Grunnlag>.hentAlleAktiv() = sortedByDescending { it.innhentet }.filter { g -> g.aktiv != null }

fun Set<Grunnlag>.henteNyesteGrunnlag(
    grunnlagstype: Grunnlagstype,
    rolle: Rolle,
    gjelder: Personident?,
) = filter {
    it.type == grunnlagstype.type &&
        it.rolle.id == rolle.id &&
        grunnlagstype.erBearbeidet == it.erBearbeidet &&
        it.gjelder == gjelder?.verdi
}.toSet().maxByOrNull { it.innhentet }

fun Set<Grunnlag>.henteNyesteGrunnlag(
    grunnlagstype: Grunnlagstype,
    rolleInnhentetFor: Rolle,
): Grunnlag? =
    filter {
        it.type == grunnlagstype.type && it.rolle.id == rolleInnhentetFor.id && grunnlagstype.erBearbeidet == it.erBearbeidet
    }.toSet().maxByOrNull { it.innhentet }

fun Set<Grunnlag>.hentSisteIkkeAktiv() =
    hentAlleIkkeAktiv()
        .groupBy { it.identifikator }
        .mapValues { (_, grunnlagList) -> grunnlagList.maxByOrNull { it.innhentet } }
        .values
        .filterNotNull()

fun Set<Grunnlag>.hentSisteAktiv() =
    hentAlleAktiv()
        .groupBy { it.identifikator }
        .mapValues { (_, grunnlagList) -> grunnlagList.maxByOrNull { it.innhentet } }
        .values
        .filterNotNull()

fun Set<Grunnlag>.hentIdenterForEgneBarnIHusstandFraGrunnlagForRolle(rolleInnhentetFor: Rolle) =
    henteNyesteGrunnlag(
        Grunnlagstype(Grunnlagsdatatype.BOFORHOLD, false),
        rolleInnhentetFor,
    )?.data
        ?.let { jsonListeTilObjekt<RelatertPersonGrunnlagDto>(it) }
        ?.filter { it.erBarn && it.gjelderPersonId != null }
        ?.groupBy { it.gjelderPersonId!! }
        ?.map { Personident(it.key) }
        ?.toSet()

fun Set<Grunnlag>.henteSisteSivilstand(erBearbeidet: Boolean) =
    hentSisteAktiv()
        .find { it.erBearbeidet == erBearbeidet && Grunnlagsdatatype.SIVILSTAND == it.type }
        .konvertereData<Set<Sivilstand>>()

fun Husstandsmedlem.hentSisteBearbeidetBoforhold() =
    behandling.grunnlag
        .hentSisteAktiv()
        .find { it.erBearbeidet && it.type == Grunnlagsdatatype.BOFORHOLD && it.gjelder == this.ident }
        .konvertereData<List<BoforholdResponse>>()

fun List<Grunnlag>.hentGrunnlagForType(
    type: Grunnlagsdatatype,
    ident: String,
) = filter {
    it.type == type && it.rolle.ident == ident
}

fun List<Grunnlag>.henteBearbeidaInntekterForType(
    type: Grunnlagsdatatype,
    ident: String,
) = find {
    it.type == type && it.erBearbeidet && it.rolle.ident == ident
}.konvertereData<SummerteInntekter<SummertÅrsinntekt>>()

inline fun <reified T> Grunnlag?.konvertereData(): T? = this?.data?.let { objectmapper.readValue(it) }
