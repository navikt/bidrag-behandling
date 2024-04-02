package no.nav.bidrag.behandling.database.datamodell

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
import no.nav.bidrag.behandling.dto.v2.validering.BeregningValideringsfeil
import no.nav.bidrag.behandling.dto.v2.validering.BeregningValideringsfeilType
import no.nav.bidrag.behandling.transformers.eksplisitteYtelser
import no.nav.bidrag.behandling.transformers.toDDMMYYYY
import no.nav.bidrag.behandling.transformers.validerBoforholdForBeregning
import no.nav.bidrag.behandling.transformers.validerInntekterForBeregning
import no.nav.bidrag.behandling.transformers.validerSivilstandForBeregning
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype
import no.nav.bidrag.transport.felles.commonObjectmapper
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import java.nio.charset.Charset
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

fun Behandling.validerForBeregning(): List<BeregningValideringsfeil> {
    val feil = mutableListOf<BeregningValideringsfeil>()
    valider(id != null) { feil.leggTil(BeregningValideringsfeilType.ANDRE, "Behandlingsid mangler") }
    valider(datoTom == null || datoTom!!.isAfter(søktFomDato)) {
        feil.leggTil(BeregningValideringsfeilType.ANDRE, "Til dato må være etter fra dato")
    }
    valider(saksnummer.isNotBlank()) { feil.leggTil(BeregningValideringsfeilType.ANDRE, "Saksnummer mangler for behandling") }
    valider(avslag != null || årsak != null) {
        feil.leggTil(BeregningValideringsfeilType.VIRKNINGSTIDSPUNKT, "Årsak eller avslag må velges")
    }

    if (avslag == null) {
        valider(virkningstidspunkt != null) { feil.leggTil(BeregningValideringsfeilType.VIRKNINGSTIDSPUNKT, "Mangler virkningstidspunkt") }
        husstandsbarn.validerBoforholdForBeregning(virkningstidspunktEllerSøktFomDato).forEach {
            feil.leggTil(BeregningValideringsfeilType.BOFORHOLD, it)
        }
        roller.filter { it.rolletype == Rolletype.BARN }.forEach { barn ->
            valider(husstandsbarn.any { it.ident == barn.ident }) {
                feil.leggTil(
                    BeregningValideringsfeilType.BOFORHOLD,
                    "Søknadsbarn ${barn.hentNavn()}/${barn.foedselsdato.toDDMMYYYY()} mangler informasjon om boforhold",
                )
            }
        }
        sivilstand.validerSivilstandForBeregning(virkningstidspunktEllerSøktFomDato).forEach {
            feil.leggTil(BeregningValideringsfeilType.SIVILSTAND, it)
        }

        validerInntekterForBeregning().forEach {
            feil.leggTil(BeregningValideringsfeilType.INNTEKT, it)
        }
        eksplisitteYtelser.forEach {
            validerInntekterForBeregning(it).forEach {
                feil.leggTil(BeregningValideringsfeilType.INNTEKT, it)
            }
        }

        if (stonadstype != Stønadstype.FORSKUDD) {
            valider(inntekter.any { it.taMed && it.ident == bidragspliktig?.ident }) {
                feil.leggTil(BeregningValideringsfeilType.INNTEKT, "Mangler innteker for bidragspliktig")
            }
        }
    }

    if (feil.isNotEmpty()) {
        secureLogger.warn { "Feil ved validering av behandling for beregning $feil" }
        throw HttpClientErrorException(
            HttpStatus.BAD_REQUEST,
            "Feil ved validering av behandling for beregning",
            commonObjectmapper.writeValueAsBytes(feil),
            Charset.defaultCharset(),
        )
    }
    return feil
}

fun MutableList<BeregningValideringsfeil>.leggTil(
    type: BeregningValideringsfeilType,
    feilmelding: String,
) {
    find { it.type == type }?.feilListe?.add(feilmelding)
        ?: add(BeregningValideringsfeil(type, mutableListOf(feilmelding)))
}

inline fun valider(
    condition: Boolean,
    onConditionNotTrue: () -> Unit,
) {
    if (!condition) {
        onConditionNotTrue()
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
