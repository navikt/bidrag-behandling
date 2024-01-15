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
import no.nav.bidrag.behandling.objectmapper
import no.nav.bidrag.transport.behandling.grunnlag.response.ArbeidsforholdGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandDto
import org.hibernate.annotations.ColumnTransformer
import java.time.LocalDateTime

@Entity(name = "grunnlag")
@Schema(name = "GrunnlagEntity")
class Grunnlag(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "behandling_id", nullable = false)
    val behandling: Behandling,
    @Enumerated(EnumType.STRING)
    val type: Grunnlagsdatatype,
    @Column(name = "data", columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    val data: String,
    val innhentet: LocalDateTime,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
)

inline fun <reified T> Grunnlag?.hentData(): T? =
    when (this?.type) {
        Grunnlagsdatatype.INNTEKTSOPPLYSNINGER, Grunnlagsdatatype.INNTEKT_BEARBEIDET -> konverterData<InntektsopplysningerBearbeidet>() as T
        Grunnlagsdatatype.BOFORHOLD, Grunnlagsdatatype.BOFORHOLD_BEARBEIDET -> konverterData<BoforholdBearbeidet>() as T
        Grunnlagsdatatype.HUSSTANDSMEDLEMMER -> konverterData<List<RelatertPersonDto>>() as T
        Grunnlagsdatatype.SIVILSTAND -> konverterData<List<SivilstandDto>>() as T
        Grunnlagsdatatype.ARBEIDSFORHOLD -> konverterData<List<ArbeidsforholdGrunnlagDto>>() as T
        Grunnlagsdatatype.INNTEKT -> konverterData<List<InntektGrunnlag>>() as T
        else -> null
    }

inline fun <reified T> Grunnlag?.konverterData(): T? = this?.data?.let { objectmapper.readValue(it) }
