package no.nav.bidrag.behandling.database.datamodell

import java.util.Date
import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.OneToMany

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

    val virkningsDato: Date? = null,

    @Enumerated(EnumType.STRING)
    val aarsak: ForskuddBeregningKodeAarsakType? = null,

    @Enumerated(EnumType.STRING)
    val avslag: AvslagType? = null,

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
}
