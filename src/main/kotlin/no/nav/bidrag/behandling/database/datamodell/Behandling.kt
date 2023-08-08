package no.nav.bidrag.behandling.database.datamodell

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import no.nav.bidrag.domain.enums.EngangsbelopType
import no.nav.bidrag.domain.enums.StonadType
import java.util.Date

@Entity(name = "behandling")
data class Behandling(
    @Enumerated(EnumType.STRING)
    val behandlingType: BehandlingType,

    @Enumerated(EnumType.STRING)
    val soknadType: SoknadType,

    val datoFom: Date,

    val datoTom: Date,

    val mottatDato: Date,

    val saksnummer: String,

    val behandlerEnhet: String,

    @Enumerated(EnumType.STRING)
    val soknadFra: SoknadFraType,

    @Enumerated(EnumType.STRING)
    var stonadType: StonadType?,

    @Enumerated(EnumType.STRING)
    var engangsbelopType: EngangsbelopType?,

    var vedtakId: Long? = null,

    val virkningsDato: Date? = null,

    @Enumerated(EnumType.STRING)
    val aarsak: ForskuddAarsakType? = null,

    @Column(name = "VIRKNINGS_TIDSPUNKT_BEGRUNNELSE_MED_I_VEDTAK_NOTAT")
    val virkningsTidspunktBegrunnelseMedIVedtakNotat: String? = null,

    @Column(name = "VIRKNINGS_TIDSPUNKT_BEGRUNNELSE_KUN_I_NOTAT")
    val virkningsTidspunktBegrunnelseKunINotat: String? = null,

    @Column(name = "BOFORHOLD_BEGRUNNELSE_MED_I_VEDTAK_NOTAT")
    val boforholdBegrunnelseMedIVedtakNotat: String? = null,

    @Column(name = "BOFORHOLD_BEGRUNNELSE_KUN_I_NOTAT")
    val boforholdBegrunnelseKunINotat: String? = null,

    @Column(name = "INNTEKT_BEGRUNNELSE_MED_I_VEDTAK_NOTAT")
    val inntektBegrunnelseMedIVedtakNotat: String? = null,

    @Column(name = "INNTEKT_BEGRUNNELSE_KUN_I_NOTAT")
    val inntektBegrunnelseKunINotat: String? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
) {
    @OneToMany(fetch = FetchType.EAGER, mappedBy = "behandling", cascade = [CascadeType.ALL], orphanRemoval = true)
    var roller: MutableSet<Rolle> = mutableSetOf()

    @OneToMany(fetch = FetchType.EAGER, mappedBy = "behandling", cascade = [CascadeType.ALL], orphanRemoval = true)
    var husstandsBarn: MutableSet<HusstandsBarn> = mutableSetOf()

    @OneToMany(fetch = FetchType.EAGER, mappedBy = "behandling", cascade = [CascadeType.ALL], orphanRemoval = true)
    var inntekter: MutableSet<Inntekt> = mutableSetOf()

    @OneToMany(fetch = FetchType.EAGER, mappedBy = "behandling", cascade = [CascadeType.ALL], orphanRemoval = true)
    var sivilstand: MutableSet<Sivilstand> = mutableSetOf()

    @OneToMany(fetch = FetchType.EAGER, mappedBy = "behandling", cascade = [CascadeType.ALL], orphanRemoval = true)
    var barnetillegg: MutableSet<Barnetillegg> = mutableSetOf()

    @OneToMany(fetch = FetchType.EAGER, mappedBy = "behandling", cascade = [CascadeType.ALL], orphanRemoval = true)
    var utvidetbarnetrygd: MutableSet<Utvidetbarnetrygd> = mutableSetOf()
}
