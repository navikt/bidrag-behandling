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
import no.nav.bidrag.behandling.database.grunnlag.SkattepliktigeInntekter
import no.nav.bidrag.behandling.database.grunnlag.SummerteInntekter
import no.nav.bidrag.behandling.dto.v1.behandling.SivilstandDto
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.objectmapper
import no.nav.bidrag.behandling.service.GrunnlagService.Companion.inntekterOgYtelser
import no.nav.bidrag.transport.behandling.grunnlag.response.ArbeidsforholdGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.BarnetilleggGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.KontantstøtteGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SmåbarnstilleggGrunnlagDto
import no.nav.bidrag.transport.behandling.inntekt.request.UtvidetBarnetrygd
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
    when (this?.erBearbeidet == true && inntekterOgYtelser.contains(this.type)) {
        true -> konverterData<SummerteInntekter<SummertÅrsinntekt>>() as T
        else -> {
            when (this?.type) {
                Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER -> konverterData<SkattepliktigeInntekter>() as T
                Grunnlagsdatatype.SUMMERTE_MÅNEDSINNTEKTER -> konverterData<SummerteInntekter<SummertÅrsinntekt>>() as T
                Grunnlagsdatatype.BARNETILLEGG -> konverterData<List<BarnetilleggGrunnlagDto>>() as T
                Grunnlagsdatatype.KONTANTSTØTTE -> konverterData<List<KontantstøtteGrunnlagDto>>() as T
                Grunnlagsdatatype.SMÅBARNSTILLEGG -> konverterData<List<SmåbarnstilleggGrunnlagDto>>() as T
                Grunnlagsdatatype.UTVIDET_BARNETRYGD -> konverterData<List<UtvidetBarnetrygd>>() as T
                Grunnlagsdatatype.BOFORHOLD_BEARBEIDET -> konverterData<BoforholdBearbeidet>() as T
                Grunnlagsdatatype.BOFORHOLD -> konverterData<List<RelatertPersonDto>>() as T
                Grunnlagsdatatype.SIVILSTAND -> konverterData<List<SivilstandDto>>() as T
                Grunnlagsdatatype.ARBEIDSFORHOLD -> konverterData<List<ArbeidsforholdGrunnlagDto>>() as T
                else -> null
            }
        }
    }

inline fun <reified T> Grunnlag?.konverterData(): T? {
    return this?.data?.let {
        objectmapper.readValue(it)
    }
}
