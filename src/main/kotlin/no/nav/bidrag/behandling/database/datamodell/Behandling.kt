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
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.Where
import java.util.Date

@Entity(name = "behandling")
@SQLDelete(sql = "UPDATE behandling SET deleted = true WHERE id=?")
@Where(clause = "deleted=false")
class Behandling(
    @Enumerated(EnumType.STRING)
    val behandlingType: Behandlingstype,
    // TODO Endre til Vedtakstype
    @Enumerated(EnumType.STRING)
    val soknadType: SoknadType,
    val datoFom: Date,
    val datoTom: Date,
    val mottatDato: Date,
    val saksnummer: String,
    val soknadId: Long,
    val soknadRefId: Long? = null,
    val behandlerEnhet: String,
    @Enumerated(EnumType.STRING)
    val soknadFra: SøktAvType,
    @Enumerated(EnumType.STRING)
    var stonadType: Stønadstype?,
    @Enumerated(EnumType.STRING)
    var engangsbelopType: Engangsbeløptype?,
    var vedtakId: Long? = null,
    var virkningsDato: Date? = null,
    @Enumerated(EnumType.STRING)
    var aarsak: ForskuddAarsakType? = null,
    @Column(name = "VIRKNINGS_TIDSPUNKT_BEGRUNNELSE_MED_I_VEDTAK_NOTAT")
    var virkningsTidspunktBegrunnelseMedIVedtakNotat: String? = null,
    @Column(name = "VIRKNINGS_TIDSPUNKT_BEGRUNNELSE_KUN_I_NOTAT")
    var virkningsTidspunktBegrunnelseKunINotat: String? = null,
    @Column(name = "BOFORHOLD_BEGRUNNELSE_MED_I_VEDTAK_NOTAT")
    var boforholdBegrunnelseMedIVedtakNotat: String? = null,
    @Column(name = "BOFORHOLD_BEGRUNNELSE_KUN_I_NOTAT")
    var boforholdBegrunnelseKunINotat: String? = null,
    @Column(name = "INNTEKT_BEGRUNNELSE_MED_I_VEDTAK_NOTAT")
    var inntektBegrunnelseMedIVedtakNotat: String? = null,
    @Column(name = "INNTEKT_BEGRUNNELSE_KUN_I_NOTAT")
    var inntektBegrunnelseKunINotat: String? = null,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    var grunnlagspakkeId: Long? = null,
    @OneToMany(
        fetch = FetchType.EAGER,
        mappedBy = "behandling",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    var roller: MutableSet<Rolle> = mutableSetOf(),
    @OneToMany(
        fetch = FetchType.EAGER,
        mappedBy = "behandling",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    var husstandsBarn: MutableSet<HusstandsBarn> = mutableSetOf(),
    @OneToMany(
        fetch = FetchType.EAGER,
        mappedBy = "behandling",
        cascade = [CascadeType.PERSIST, CascadeType.MERGE],
        orphanRemoval = true,
    )
    var inntekter: MutableSet<Inntekt> = mutableSetOf(),
    @OneToMany(
        fetch = FetchType.EAGER,
        mappedBy = "behandling",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    var sivilstand: MutableSet<Sivilstand> = mutableSetOf(),
    @OneToMany(
        fetch = FetchType.EAGER,
        mappedBy = "behandling",
        cascade = [CascadeType.PERSIST, CascadeType.MERGE],
        orphanRemoval = true,
    )
    var barnetillegg: MutableSet<Barnetillegg> = mutableSetOf(),
    @OneToMany(
        fetch = FetchType.EAGER,
        mappedBy = "behandling",
        cascade = [CascadeType.PERSIST, CascadeType.MERGE],
        orphanRemoval = true,
    )
    var utvidetbarnetrygd: MutableSet<Utvidetbarnetrygd> = mutableSetOf(),
    var deleted: Boolean = false,
) {
    fun getSøknadsBarn() = roller.filter { it.rolleType == Rolletype.BARN }
}
