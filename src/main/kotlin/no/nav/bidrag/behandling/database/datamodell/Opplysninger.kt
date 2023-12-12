package no.nav.bidrag.behandling.database.datamodell

import com.fasterxml.jackson.module.kotlin.readValue
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
import no.nav.bidrag.transport.behandling.grunnlag.response.ArbeidsforholdDto
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandDto
import java.util.Date

@Entity(name = "opplysninger")
class Opplysninger(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "behandling_id", nullable = false)
    val behandling: Behandling,
    @Enumerated(EnumType.STRING)
    val opplysningerType: OpplysningerType,
    val data: String,
    val hentetDato: Date,
    @Column(insertable = false, updatable = false)
    val ts: Date? = null,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
)

inline fun <reified T> Opplysninger?.hentData(): T? = when (this?.opplysningerType) {
    OpplysningerType.INNTEKTSOPPLYSNINGER, OpplysningerType.INNTEKT_BEARBEIDET -> konverterData<InntektsopplysningerBearbeidet>() as T
    OpplysningerType.BOFORHOLD, OpplysningerType.BOFORHOLD_BEARBEIDET -> konverterData<BoforholdBearbeidet>() as T
    OpplysningerType.HUSSTANDSMEDLEMMER -> konverterData<List<RelatertPersonDto>>() as T
    OpplysningerType.SIVILSTAND -> konverterData<List<SivilstandDto>>() as T
    OpplysningerType.ARBEIDSFORHOLD -> konverterData<List<ArbeidsforholdDto>>() as T
    OpplysningerType.INNTEKT -> konverterData<List<InntektGrunnlag>>() as T
    else -> null
}

inline fun <reified T> Opplysninger?.konverterData(): T? =
    this?.data?.let { objectmapper.readValue(it) }