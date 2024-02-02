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
import no.nav.bidrag.behandling.database.opplysninger.BoforholdBearbeidet
import no.nav.bidrag.behandling.database.opplysninger.InntektGrunnlag
import no.nav.bidrag.behandling.database.opplysninger.InntektsopplysningerBearbeidet
import no.nav.bidrag.transport.behandling.grunnlag.response.ArbeidsforholdGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import no.nav.bidrag.transport.felles.commonObjectmapper
import org.hibernate.annotations.ColumnTransformer
import java.time.LocalDateTime

@Entity(name = "grunnlag")
@Schema(name = "GrunnlagEntity")
open class BehandlingGrunnlag(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "behandling_id", nullable = false)
    open val behandling: Behandling,
    @Enumerated(EnumType.STRING)
    open val type: Grunnlagsdatatype,
    @Column(name = "data", columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    open val data: String,
    open val innhentet: LocalDateTime,
    open val aktiv: LocalDateTime? = null,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open val id: Long? = null,
)

inline fun <reified T> BehandlingGrunnlag?.hentData(): T? =
    when (this?.type) {
        Grunnlagsdatatype.INNTEKTSOPPLYSNINGER, Grunnlagsdatatype.INNTEKT_BEARBEIDET -> konverterData<InntektsopplysningerBearbeidet>() as T
        Grunnlagsdatatype.BOFORHOLD, Grunnlagsdatatype.BOFORHOLD_BEARBEIDET -> konverterData<BoforholdBearbeidet>() as T
        Grunnlagsdatatype.HUSSTANDSMEDLEMMER -> konverterData<List<RelatertPersonGrunnlagDto>>() as T
        Grunnlagsdatatype.SIVILSTAND -> konverterData<List<SivilstandGrunnlagDto>>() as T
        Grunnlagsdatatype.ARBEIDSFORHOLD -> konverterData<List<ArbeidsforholdGrunnlagDto>>() as T
        Grunnlagsdatatype.INNTEKT -> konverterData<InntektGrunnlag>() as T
        else -> null
    }

val List<BehandlingGrunnlag>.inntekt: BehandlingGrunnlag?
    get() = find { it.type == Grunnlagsdatatype.INNTEKT }
val List<BehandlingGrunnlag>.sivilstand: BehandlingGrunnlag?
    get() = find { it.type == Grunnlagsdatatype.SIVILSTAND }
val List<BehandlingGrunnlag>.husstandmedlemmer: BehandlingGrunnlag?
    get() = find { it.type == Grunnlagsdatatype.HUSSTANDSMEDLEMMER }
val List<BehandlingGrunnlag>.arbeidsforhold: BehandlingGrunnlag?
    get() = find { it.type == Grunnlagsdatatype.ARBEIDSFORHOLD }

inline fun <reified T> BehandlingGrunnlag?.konverterData(): T? = this?.data?.let { commonObjectmapper.readValue(it) }
