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
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.Where
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(name = "behandling")
@SQLDelete(sql = "UPDATE behandling SET deleted = true WHERE id=?")
@Where(clause = "deleted=false")
class Behandling(
    @Enumerated(EnumType.STRING)
    @Column(name = "soknadstype")
    val vedtakstype: Vedtakstype,
    val datoFom: LocalDate,
    val datoTom: LocalDate? = null,
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
        cascade = [CascadeType.MERGE, CascadeType.PERSIST],
        orphanRemoval = true,
    )
    var husstandsbarn: MutableSet<Husstandsbarn> = mutableSetOf(),
    @OneToMany(
        fetch = FetchType.EAGER,
        mappedBy = "behandling",
        cascade = [CascadeType.MERGE, CascadeType.PERSIST],
        orphanRemoval = true,
    )
    var inntekter: MutableSet<Inntekt> = mutableSetOf(),
    @OneToMany(
        fetch = FetchType.EAGER,
        mappedBy = "behandling",
        cascade = [CascadeType.MERGE, CascadeType.PERSIST],
        orphanRemoval = true,
    )
    var sivilstand: MutableSet<Sivilstand> = mutableSetOf(),
    @OneToMany(
        fetch = FetchType.EAGER,
        mappedBy = "behandling",
        cascade = [CascadeType.MERGE, CascadeType.PERSIST],
        orphanRemoval = true,
    )
    var barnetillegg: MutableSet<Barnetillegg> = mutableSetOf(),
    @OneToMany(
        fetch = FetchType.EAGER,
        mappedBy = "behandling",
        cascade = [CascadeType.MERGE, CascadeType.PERSIST],
        orphanRemoval = true,
    )
    var utvidetBarnetrygd: MutableSet<UtvidetBarnetrygd> = mutableSetOf(),
    var deleted: Boolean = false,
) {
    fun getSøknadsbarn() = roller.filter { it.rolletype == Rolletype.BARN }

    fun getBidragsmottaker() = roller.find { it.rolletype == Rolletype.BIDRAGSMOTTAKER }

    fun getBidragspliktig() = roller.find { it.rolletype == Rolletype.BIDRAGSPLIKTIG }
}

fun Behandling.tilBehandlingstype() = (stonadstype?.name ?: engangsbeloptype?.name)

fun Behandling.validere(): Either<NonEmptyList<String>, Behandling> =
    either {
        zipOrAccumulate(
            { ensure(this@validere.id != null) { raise("Behandlingsid mangler") } },
            {
                ensure(this@validere.datoTom == null || this@validere.datoTom.isAfter(this@validere.datoFom)) {
                    raise(
                        "Til dato må være etter fra dato"
                    )
                }
            },
            { ensure(this@validere.virkningsdato != null) { raise("Mangler virkningsdato") } },
            { ensure(this@validere.saksnummer.isNotBlank()) { raise("Saksnummer mangler for behandling") } },
            {
                ensure(this@validere.sivilstand.size > 0) { raise("Sivilstand mangler i behandling") }
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
                val bm = getBidragsmottaker()
                val bp = getBidragspliktig()
                ensure(this@validere.inntekter.any { it.taMed && it.ident == bm?.ident }) { raise("Mangler inntekter for bidragsmottaker") }
                if (stonadstype != Stønadstype.FORSKUDD) {
                    ensure(this@validere.inntekter.any { it.taMed && it.ident == bp?.ident }) {
                        raise(
                            "Mangler innteker for bidragspliktig",
                        )
                    }
                }
            },
            {
                mapOrAccumulate(utvidetBarnetrygd) {
                    ensure(it.datoFom != null) { raise("Fra-dato mangler for utvidet barnetrygd") }
                }
            },
            {
                mapOrAccumulate(barnetillegg) {
                    ensure(it.datoFom != null) { raise("Fra-dato mangler for barnetillegg") }
                }
            },
            {
                ensure(this@validere.husstandsbarn.size > 0) { raise("Mangler informasjon om husstandsbarn") }
                this@validere.husstandsbarn.filter { it.medISaken }.forEach {
                    ensure(it.perioder.isNotEmpty()) {
                        raise(
                            "Mangler perioder for husstandsbarn ${it.hentNavn()}/${it.ident}",
                        )
                    }
                }

                roller.filter { it.rolletype == Rolletype.BARN }.forEach { barn ->
                    ensure(this@validere.husstandsbarn.any { it.ident == barn.ident }) {
                        raise(
                            "Søknadsbarn ${barn.hentNavn()}/${barn.ident} mangler informasjon om boforhold",
                        )
                    }
                }

                mapOrAccumulate(husstandsbarn.filter { it.medISaken }.flatMap { it.perioder }) {
                    ensure(it.datoFom != null) { raise("Fra-dato mangler for husstandsbarnperiode i behandling") }
                    it
                }
            },
        ) { _, _, _, _, _, _, _, _, _ ->
            val behandling = this@validere
            behandling.inntekter = inntekter
            behandling.husstandsbarn = husstandsbarn
            behandling
        }
    }
