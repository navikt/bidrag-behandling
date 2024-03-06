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
import no.nav.bidrag.behandling.database.grunnlag.BoforholdBearbeidet
import no.nav.bidrag.behandling.database.grunnlag.InntektsopplysningerBearbeidet
import no.nav.bidrag.behandling.database.grunnlag.SkattepliktigInntekter
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.objectmapper
import no.nav.bidrag.transport.behandling.grunnlag.response.ArbeidsforholdGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandDto
import no.nav.bidrag.transport.behandling.inntekt.response.SummertMånedsinntekt
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
)

inline fun <reified T> Grunnlag?.hentData(): T? =
    when (this?.type) {
        Grunnlagsdatatype.INNTEKTSOPPLYSNINGER, Grunnlagsdatatype.INNTEKT_BEARBEIDET -> konverterData<InntektsopplysningerBearbeidet>() as T
        Grunnlagsdatatype.BOFORHOLD_BEARBEIDET -> konverterData<BoforholdBearbeidet>() as T
        Grunnlagsdatatype.BOFORHOLD -> konverterData<List<RelatertPersonDto>>() as T
        Grunnlagsdatatype.SIVILSTAND -> konverterData<List<SivilstandDto>>() as T
        Grunnlagsdatatype.ARBEIDSFORHOLD -> konverterData<List<ArbeidsforholdGrunnlagDto>>() as T
        Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER -> konverterData<SummertMånedsinntekt>() as T
        Grunnlagsdatatype.SKATTEPLIKTIG -> konverterData<SkattepliktigInntekter<SummertÅrsinntekt>>() as T
        else -> null
    }

inline fun <reified T> Grunnlag?.konverterData(): T? {
    return this?.data?.let {
        objectmapper.readValue(it)
    }
}
