package no.nav.bidrag.behandling.database.datamodell

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.zipOrAccumulate
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
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(name = "behandling")
@SQLDelete(sql = "UPDATE behandling SET deleted = true WHERE id=?")
@Where(clause = "deleted=false")
class Behandling(
    @Enumerated(EnumType.STRING)
    val behandlingstype: Behandlingstype,
    // TODO Endre til Vedtakstype
    @Enumerated(EnumType.STRING)
    val soknadstype: Soknadstype,
    val datoFom: LocalDate,
    val datoTom: LocalDate,
    val mottattdato: LocalDate,
    val saksnummer: String,
    val soknadsid: Long,
    val soknadRefId: Long? = null,
    val behandlerEnhet: String,
    val opprettetAv: String,
    val opprettetAvNavn: String? = null,
    val kildeapplikasjon: String,
    @Enumerated(EnumType.STRING)
    val soknadFra: SøktAvType,
    @Enumerated(EnumType.STRING)
    var stonadstype: Stønadstype?,
    @Enumerated(EnumType.STRING)
    var engangsbeloptype: Engangsbeløptype?,
    var vedtaksid: Long? = null,
    var virkningsdato: LocalDate? = null,
    @Enumerated(EnumType.STRING)
    var aarsak: ForskuddAarsakType? = null,
    @Column(name = "VIRKNINGSTIDSPUNKTBEGRUNNELSE_VEDTAK_OG_NOTAT")
    var virkningstidspunktsbegrunnelseIVedtakOgNotat: String? = null,
    @Column(name = "VIRKNINGSTIDSPUNKTBEGRUNNELSE_KUN_NOTAT")
    var virkningstidspunktbegrunnelseKunINotat: String? = null,
    @Column(name = "BOFORHOLDSBEGRUNNELSE_VEDTAK_OG_NOTAT")
    var boforholdsbegrunnelseIVedtakOgNotat: String? = null,
    @Column(name = "BOFORHOLDSBEGRUNNELSE_KUN_NOTAT")
    var boforholdsbegrunnelseKunINotat: String? = null,
    @Column(name = "INNTEKTSBEGRUNNELSE_VEDTAK_OG_NOTAT")
    var inntektsbegrunnelseIVedtakOgNotat: String? = null,
    @Column(name = "INNTEKTSBEGRUNNELSE_KUN_NOTAT")
    var inntektsbegrunnelseKunINotat: String? = null,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    var grunnlagspakkeid: Long? = null,
    var grunnlagSistInnhentet: LocalDateTime? = null,
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
        cascade = [CascadeType.MERGE],
        orphanRemoval = true,
    )
    var husstandsbarn: MutableSet<Husstandsbarn> = mutableSetOf(),
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
        cascade = [CascadeType.MERGE],
        orphanRemoval = true,
    )
    var sivilstand: MutableSet<Sivilstand> = mutableSetOf(),
    @OneToMany(
        fetch = FetchType.EAGER,
        mappedBy = "behandling",
        cascade = [CascadeType.MERGE],
        orphanRemoval = true,
    )
    var barnetillegg: MutableSet<Barnetillegg> = mutableSetOf(),
    @OneToMany(
        fetch = FetchType.EAGER,
        mappedBy = "behandling",
        cascade = [CascadeType.PERSIST, CascadeType.MERGE],
        orphanRemoval = true,
    )
    var utvidetBarnetrygd: MutableSet<UtvidetBarnetrygd> = mutableSetOf(),
    var deleted: Boolean = false,
) {
    fun getSøknadsbarn() = roller.filter { it.rolletype == Rolletype.BARN }
}

fun Behandling.validere(): Either<NonEmptyList<String>, Behandling> =
    either {
        zipOrAccumulate(
            { ensure(this@validere.id != null) { raise("Behandlingsid mangler") } },
            { ensure(this@validere.datoTom != null) { raise("Til-dato mangler for behandling") } },
            { ensure(this@validere.virkningsdato != null) { raise("Virkningsdato mangler for behandling") } },
            { ensure(this@validere.saksnummer.isNotBlank()) { raise("Saksnummer mangler for behandling") } },
            {
                mapOrAccumulate(sivilstand) {
                    ensure(it.datoFom != null) { raise("Til-dato mangler for sivilstand i behandling") }
                }
            },
            {
                mapOrAccumulate(inntekter.filter { it.taMed }) {
                    ensure(it.datoFom != null) { raise("Til-dato mangler for sivilstand i behandling") }
                    ensure(it.inntektstype != null) { raise("Inntektstype mangler for behandling") }
                    it
                }
            },
            {
                mapOrAccumulate(utvidetBarnetrygd) {
                    ensure(it.datoFom != null) { raise("Fra-dato mangler for utvidet barnetrygd i behandling") }
                }
            },
            {
                mapOrAccumulate(barnetillegg) {
                    ensure(it.datoFom != null) { raise("Fra-dato mangler for barnetillegg i behandling") }
                }
            },
            {
                ensure(this@validere.husstandsbarn.size > 0) { raise("Husstandsbarn mangler i behandling") }
                mapOrAccumulate(husstandsbarn.filter { it.medISaken }.flatMap { it.perioder }) {
                    ensure(it.datoFom != null) { raise("Fra-dato mangler for husstandsbarnpreiode i behandling") }
                    it
                }
            },
        ) { _, _, _, _, _, _, _, _, _ ->
            var behandling = this@validere
            behandling.inntekter = inntekter
            behandling.husstandsbarn = husstandsbarn
            behandling
        }
    }
