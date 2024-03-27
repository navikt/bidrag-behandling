package no.nav.bidrag.behandling.database.datamodell

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.zipOrAccumulate
import jakarta.persistence.AttributeConverter
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Converter
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import no.nav.bidrag.behandling.transformers.eksplisitteYtelser
import no.nav.bidrag.behandling.transformers.validerInntekterForBeregning
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDate
import java.time.LocalDateTime

@Suppress("Unused")
@Entity(name = "behandling")
@SQLDelete(sql = "UPDATE behandling SET deleted = true, slettet_tidspunkt = now() WHERE id=?")
@SQLRestriction(value = "deleted=false")
open class Behandling(
    @Enumerated(EnumType.STRING)
    open val vedtakstype: Vedtakstype,
    @Column(name = "dato_fom")
    open val søktFomDato: LocalDate,
    open val datoTom: LocalDate? = null,
    open val mottattdato: LocalDate,
    open val saksnummer: String,
    open var soknadsid: Long,
    open val soknadRefId: Long? = null,
    open val behandlerEnhet: String,
    open val opprettetAv: String,
    open val opprettetAvNavn: String? = null,
    open val kildeapplikasjon: String,
    @Enumerated(EnumType.STRING)
    open val soknadFra: SøktAvType,
    @Enumerated(EnumType.STRING)
    open var stonadstype: Stønadstype?,
    @Enumerated(EnumType.STRING)
    open var engangsbeloptype: Engangsbeløptype?,
    open var vedtaksid: Long? = null,
    open var refVedtaksid: Long? = null,
    @Column(name = "virkningsdato")
    open var virkningstidspunkt: LocalDate? = null,
    open var opprinneligVirkningstidspunkt: LocalDate? = null,
    open var vedtakstidspunkt: LocalDateTime? = null,
    open var slettetTidspunkt: LocalDateTime? = null,
    open var vedtakFattetAv: String? = null,
    @Column(name = "aarsak")
    @Convert(converter = ÅrsakConverter::class)
    open var årsak: VirkningstidspunktÅrsakstype? = null,
    @Column(name = "avslag")
    @Enumerated(EnumType.STRING)
    open var avslag: Resultatkode? = null,
    @Column(name = "VIRKNINGSTIDSPUNKTBEGRUNNELSE_VEDTAK_OG_NOTAT")
    open var virkningstidspunktsbegrunnelseIVedtakOgNotat: String? = null,
    @Column(name = "VIRKNINGSTIDSPUNKTBEGRUNNELSE_KUN_NOTAT")
    open var virkningstidspunktbegrunnelseKunINotat: String? = null,
    @Column(name = "BOFORHOLDSBEGRUNNELSE_VEDTAK_OG_NOTAT")
    open var boforholdsbegrunnelseIVedtakOgNotat: String? = null,
    @Column(name = "BOFORHOLDSBEGRUNNELSE_KUN_NOTAT")
    open var boforholdsbegrunnelseKunINotat: String? = null,
    @Column(name = "INNTEKTSBEGRUNNELSE_VEDTAK_OG_NOTAT")
    open var inntektsbegrunnelseIVedtakOgNotat: String? = null,
    @Column(name = "INNTEKTSBEGRUNNELSE_KUN_NOTAT")
    open var inntektsbegrunnelseKunINotat: String? = null,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open val id: Long? = null,
    open var grunnlagspakkeid: Long? = null,
    open var grunnlagSistInnhentet: LocalDateTime? = null,
    @OneToMany(
        fetch = FetchType.EAGER,
        mappedBy = "behandling",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    open var grunnlag: MutableSet<Grunnlag> = mutableSetOf(),
    @OneToMany(
        fetch = FetchType.EAGER,
        mappedBy = "behandling",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    open var roller: MutableSet<Rolle> = mutableSetOf(),
    @OneToMany(
        fetch = FetchType.EAGER,
        mappedBy = "behandling",
        cascade = [CascadeType.MERGE, CascadeType.PERSIST],
        orphanRemoval = true,
    )
    open var husstandsbarn: MutableSet<Husstandsbarn> = mutableSetOf(),
    @OneToMany(
        fetch = FetchType.EAGER,
        mappedBy = "behandling",
        cascade = [CascadeType.MERGE, CascadeType.PERSIST],
        orphanRemoval = true,
    )
    open var inntekter: MutableSet<Inntekt> = mutableSetOf(),
    @OneToMany(
        fetch = FetchType.EAGER,
        mappedBy = "behandling",
        cascade = [CascadeType.MERGE, CascadeType.PERSIST],
        orphanRemoval = true,
    )
    open var sivilstand: MutableSet<Sivilstand> = mutableSetOf(),
    open var deleted: Boolean = false,
) {
    val grunnlagListe: List<Grunnlag> get() = grunnlag.toList()
    val søknadsbarn get() = roller.filter { it.rolletype == Rolletype.BARN }
    val bidragsmottaker get() = roller.find { it.rolletype == Rolletype.BIDRAGSMOTTAKER }
    val bidragspliktig get() = roller.find { it.rolletype == Rolletype.BIDRAGSPLIKTIG }

    val erVedtakFattet get() = vedtaksid != null
    val virkningstidspunktEllerSøktFomDato get() = virkningstidspunkt ?: søktFomDato
    val erKlageEllerOmgjøring get() = refVedtaksid != null
}

fun Behandling.tilBehandlingstype() = (stonadstype?.name ?: engangsbeloptype?.name)

fun Behandling.validere(): Either<NonEmptyList<String>, Behandling> =
    either {
        zipOrAccumulate(
            { ensure(this@validere.id != null) { raise("Behandlingsid mangler") } },
            {
                ensure(this@validere.datoTom == null || this@validere.datoTom!!.isAfter(this@validere.søktFomDato)) {
                    raise(
                        "Til dato må være etter fra dato",
                    )
                }
            },
            { ensure(this@validere.virkningstidspunkt != null) { raise("Mangler virkningstidspunkt") } },
            { ensure(this@validere.saksnummer.isNotBlank()) { raise("Saksnummer mangler for behandling") } },
            {
                ensure(this@validere.sivilstand.size > 0) { raise("Mangler perioder for sivilstand") }
                mapOrAccumulate(sivilstand) {
                    ensure(it.datoFom != null) { raise("Til-dato mangler for sivilstand") }
                }
            },
            {
                val inntekter =
                    mapOrAccumulate(inntekter.filter { it.taMed }) {
                        it
                    }
                validerInntekterForBeregning().forEach {
                    raise(it)
                }
                eksplisitteYtelser.forEach {
                    validerInntekterForBeregning(it).forEach {
                        raise(it)
                    }
                }
                ensure(inntekter.any { it.taMed && it.ident == bidragsmottaker?.ident }) {
                    raise(
                        "Mangler inntekter for bidragsmottaker",
                    )
                }
                if (stonadstype != Stønadstype.FORSKUDD) {
                    ensure(inntekter.any { it.taMed && it.ident == bidragspliktig?.ident }) {
                        raise(
                            "Mangler innteker for bidragspliktig",
                        )
                    }
                }
            },
            {
                ensure(this@validere.husstandsbarn.size > 0) { raise("Mangler informasjon om husstandsbarn") }
                this@validere.husstandsbarn.filter { Kilde.OFFENTLIG == it.kilde }.forEach {
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

                mapOrAccumulate(husstandsbarn.filter { Kilde.OFFENTLIG == it.kilde }.flatMap { it.perioder }) {
                    ensure(it.datoFom != null) { raise("Fra-dato mangler for husstandsbarnperiode i behandling") }
                    it
                }
            },
        ) { _, _, _, _, _, _, _ ->
            val behandling = this@validere
            behandling.inntekter = inntekter
            behandling.husstandsbarn = husstandsbarn
            behandling
        }
    }

@Converter
open class ÅrsakConverter : AttributeConverter<VirkningstidspunktÅrsakstype?, String?> {
    override fun convertToDatabaseColumn(attribute: VirkningstidspunktÅrsakstype?): String? {
        return attribute?.name
    }

    override fun convertToEntityAttribute(dbData: String?): VirkningstidspunktÅrsakstype? {
        return dbData?.tilÅrsakstype()
    }
}
