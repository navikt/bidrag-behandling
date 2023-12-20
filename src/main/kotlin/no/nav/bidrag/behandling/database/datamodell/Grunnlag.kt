package no.nav.bidrag.behandling.database.datamodell

import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
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
import no.nav.bidrag.transport.behandling.grunnlag.response.ArbeidsforholdDto
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandDto
import java.io.Serializable
import java.time.LocalDateTime

@Entity(name = "grunnlag")
class Grunnlag(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "behandling_id", nullable = false)
    val behandling: Behandling,
    @Enumerated(EnumType.STRING)
    val type: Grunnlagstype,
    @Column(columnDefinition = "jsonb")
    val data: Jsonb,
    val innhentet: LocalDateTime,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
)

@Embeddable
class Jsonb(var innhold: String) : Serializable

inline fun <reified T> Grunnlag?.hentData(): T? =
    when (this?.type) {
        Grunnlagstype.INNTEKTSOPPLYSNINGER, Grunnlagstype.INNTEKT_BEARBEIDET -> konverterData<InntektsopplysningerBearbeidet>() as T
        Grunnlagstype.BOFORHOLD, Grunnlagstype.BOFORHOLD_BEARBEIDET -> konverterData<BoforholdBearbeidet>() as T
        Grunnlagstype.HUSSTANDSMEDLEMMER -> konverterData<List<RelatertPersonDto>>() as T
        Grunnlagstype.SIVILSTAND -> konverterData<List<SivilstandDto>>() as T
        Grunnlagstype.ARBEIDSFORHOLD -> konverterData<List<ArbeidsforholdDto>>() as T
        Grunnlagstype.INNTEKT -> konverterData<List<InntektGrunnlag>>() as T
        else -> null
    }

inline fun <reified T> Grunnlag?.konverterData(): T? = this?.data?.let { objectmapper.readValue(it.innhold) }
