package no.nav.bidrag.behandling.database.datamodell

import com.fasterxml.jackson.core.type.TypeReference
import io.hypersistence.utils.hibernate.type.ImmutableType
import io.hypersistence.utils.hibernate.type.json.internal.JacksonUtil
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
import jakarta.persistence.OneToOne
import no.nav.bidrag.behandling.database.datamodell.json.ForsendelseBestillinger
import no.nav.bidrag.behandling.database.datamodell.json.ForsendelseBestillingerConverter
import no.nav.bidrag.behandling.database.datamodell.json.KlageDetaljerConverter
import no.nav.bidrag.behandling.database.datamodell.json.Klagedetaljer
import no.nav.bidrag.behandling.database.datamodell.json.VedtakDetaljer
import no.nav.bidrag.behandling.database.datamodell.json.VedtakDetaljerConverter
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.LesemodusVedtak
import no.nav.bidrag.behandling.dto.v2.validering.GrunnlagFeilDto
import no.nav.bidrag.behandling.objectmapper
import no.nav.bidrag.behandling.transformers.vedtak.ifFalse
import no.nav.bidrag.beregn.core.util.justerPeriodeTomOpphørsdato
import no.nav.bidrag.domene.enums.behandling.BisysSøknadstype
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.særbidrag.Særbidragskategori
import no.nav.bidrag.domene.enums.vedtak.BeregnTil
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.sak.Saksnummer
import no.nav.bidrag.domene.sak.Stønadsid
import no.nav.bidrag.transport.behandling.belopshistorikk.response.StønadDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.felles.toCompactString
import org.hibernate.annotations.ColumnTransformer
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import org.hibernate.annotations.Type
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.engine.spi.SharedSessionContractImplementor
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

@Suppress("Unused")
@Entity(name = "behandling")
@SQLDelete(sql = "UPDATE behandling SET deleted = true, slettet_tidspunkt = now() WHERE id=?")
@SQLRestriction(value = "deleted=false")
open class Behandling(
    @Enumerated(EnumType.STRING)
    open var vedtakstype: Vedtakstype,
    @Column(name = "dato_fom")
    open var søktFomDato: LocalDate,
    open var mottattdato: LocalDate,
    open val saksnummer: String,
    open var soknadsid: Long?,
    open var behandlerEnhet: String,
    open var opprettetAv: String,
    open val opprettetAvNavn: String? = null,
    open val kildeapplikasjon: String,
    @Enumerated(EnumType.STRING)
    open val soknadFra: SøktAvType,
    @Enumerated(EnumType.STRING)
    open var stonadstype: Stønadstype?,
    @Enumerated(EnumType.STRING)
    open var engangsbeloptype: Engangsbeløptype?,
    open var vedtaksid: Int? = null,
    open var notatJournalpostId: String? = null,
    @Column(name = "virkningsdato")
    open var virkningstidspunkt: LocalDate? = null,
    open var vedtakstidspunkt: LocalDateTime? = null,
    open var slettetTidspunkt: LocalDateTime? = null,
    open var opprettetTidspunkt: LocalDateTime = LocalDateTime.now(),
    open var vedtakFattetAv: String? = null,
    open var kategori: String? = null,
    open var kategoriBeskrivelse: String? = null,
    @Enumerated(EnumType.STRING)
    open var innkrevingstype: Innkrevingstype? = null,
    @Column(name = "aarsak")
    @Convert(converter = ÅrsakConverter::class)
    open var årsak: VirkningstidspunktÅrsakstype? = null,
    @Column(name = "avslag")
    @Enumerated(EnumType.STRING)
    open var avslag: Resultatkode? = null,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null,
    @Column(name = "grunnlagsinnhenting_feilet", columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    open var grunnlagsinnhentingFeilet: String? = null,
    @Column(name = "forsendelse_bestillinger", columnDefinition = "jsonb")
    @Convert(converter = ForsendelseBestillingerConverter::class)
    @ColumnTransformer(write = "?::jsonb")
    open var forsendelseBestillinger: ForsendelseBestillinger = ForsendelseBestillinger(),
    @Column(name = "klagedetaljer", columnDefinition = "jsonb")
    @Convert(converter = KlageDetaljerConverter::class)
    @ColumnTransformer(write = "?::jsonb")
    open var klagedetaljer: Klagedetaljer? = null,
    @Column(name = "vedtak_detaljer", columnDefinition = "jsonb")
    @Convert(converter = VedtakDetaljerConverter::class)
    @ColumnTransformer(write = "?::jsonb")
    open var vedtakDetaljer: VedtakDetaljer? = null,
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
    open var notater: MutableSet<Notat> = mutableSetOf(),
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
    open var husstandsmedlem: MutableSet<Husstandsmedlem> = mutableSetOf(),
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
    @OneToOne(
        fetch = FetchType.EAGER,
        mappedBy = "behandling",
        cascade = [CascadeType.MERGE, CascadeType.PERSIST],
        orphanRemoval = true,
    )
    open var utgift: Utgift? = null,
    @OneToMany(
        fetch = FetchType.EAGER,
        mappedBy = "behandling",
        cascade = [CascadeType.MERGE, CascadeType.PERSIST],
        orphanRemoval = true,
    )
    open var samvær: MutableSet<Samvær> = mutableSetOf(),
    @OneToMany(
        fetch = FetchType.EAGER,
        mappedBy = "behandling",
        cascade = [CascadeType.MERGE, CascadeType.PERSIST],
        orphanRemoval = true,
    )
    open var privatAvtale: MutableSet<PrivatAvtale> = mutableSetOf(),
    @OneToMany(
        fetch = FetchType.EAGER,
        mappedBy = "behandling",
        cascade = [CascadeType.MERGE, CascadeType.PERSIST, CascadeType.REMOVE],
        orphanRemoval = true,
    )
    open var underholdskostnader: MutableSet<Underholdskostnad> = mutableSetOf(),
    open var deleted: Boolean = false,
    open var vedtakFattetAvEnhet: String? = null,
    @Type(BehandlingMetadataDoConverter::class)
    @Column(columnDefinition = "hstore", name = "metadata")
    open var metadata: BehandlingMetadataDo? = null,
    @Enumerated(EnumType.STRING)
    open var søknadstype: BisysSøknadstype? = null,
    @Transient
    var erBisysVedtak: Boolean = false,
    @Transient
    var lesemodusVedtak: LesemodusVedtak? = null,
    @Transient
    var erVedtakUtenBeregning: Boolean = false,
    @Transient
    var grunnlagslisteFraVedtak: List<GrunnlagDto>? = emptyList(),
    @Transient
    var historiskeStønader: MutableSet<StønadDto> = mutableSetOf(),
) {
    val grunnlagListe: List<Grunnlag> get() = grunnlag.toList()
    val søknadsbarn get() = roller.filter { it.rolletype == Rolletype.BARN }
    val bidragsmottaker get() = roller.find { it.rolletype == Rolletype.BIDRAGSMOTTAKER }
    val bidragspliktig get() = roller.find { it.rolletype == Rolletype.BIDRAGSPLIKTIG }

    val erVedtakFattet get() = vedtaksid != null
    val virkningstidspunktEllerSøktFomDato get() = virkningstidspunkt ?: søktFomDato
    val erKlageEllerOmgjøring get() = klagedetaljer?.påklagetVedtak != null
    val minstEnRolleHarBegrensetBeregnTilDato get() =
        søknadsbarn.any {
            it.opphørsdato != null ||
                it.beregnTil != null && it.beregnTil != BeregnTil.INNEVÆRENDE_MÅNED
        }
    val globalOpphørsdatoYearMonth get() = globalOpphørsdato?.let { YearMonth.from(it) }
    val globalVirkningstidspunkt get() =
        søknadsbarn.mapNotNull { it.virkningstidspunkt }.minByOrNull { it } ?: virkningstidspunkt
    val globalOpphørsdato get() =
        if (søknadsbarn.any { it.opphørsdato == null }) {
            null
        } else {
            søknadsbarn.maxByOrNull { it.opphørsdato!! }?.opphørsdato
        }

    val opphørTilDato get() = justerPeriodeTomOpphørsdato(globalOpphørsdato)
    val opphørSistePeriode get() = opphørTilDato != null

    fun tilStønadsid(søknadsbarn: Rolle) =
        Stønadsid(
            stonadstype!!,
            Personident(søknadsbarn.ident!!),
            Personident(bidragspliktig!!.ident!!),
            Saksnummer(saksnummer),
        )
}

val Behandling.særbidragKategori
    get() =
        kategori.isNullOrEmpty().ifFalse {
            try {
                Særbidragskategori.valueOf(kategori!!)
            } catch (e: Exception) {
                Særbidragskategori.ANNET
            }
        } ?: Særbidragskategori.ANNET

fun Behandling.henteEllerOpprettBpHusstandsmedlem(): Husstandsmedlem {
    val eksisterendeHusstandsmedlem = husstandsmedlem.find { Rolletype.BIDRAGSPLIKTIG == it.rolle?.rolletype }
    return eksisterendeHusstandsmedlem ?: leggeTilBPSomHusstandsmedlem()
}

private fun Behandling.leggeTilBPSomHusstandsmedlem(): Husstandsmedlem {
    val bpSomHusstandsmedlem = Husstandsmedlem(this, kilde = Kilde.OFFENTLIG, rolle = this.bidragspliktig!!)
    this.husstandsmedlem.add(bpSomHusstandsmedlem)
    return bpSomHusstandsmedlem
}

fun Behandling.grunnlagsinnhentingFeiletMap(): Map<Grunnlagsdatatype, GrunnlagFeilDto> {
    val typeRef: TypeReference<Map<Grunnlagsdatatype, GrunnlagFeilDto>> =
        object : TypeReference<Map<Grunnlagsdatatype, GrunnlagFeilDto>>() {}

    return grunnlagsinnhentingFeilet?.let { objectmapper.readValue(grunnlagsinnhentingFeilet, typeRef) } ?: emptyMap()
}

fun Behandling.henteAlleBostatusperioder() = husstandsmedlem.flatMap { it.perioder }

fun Behandling.finnBostatusperiode(id: Long?) = henteAlleBostatusperioder().find { it.id == id }

fun Behandling.tilBehandlingstype() = (stonadstype?.name ?: engangsbeloptype?.name)

val Set<Husstandsmedlem>.barn get() = filter { it.rolle?.rolletype != Rolletype.BIDRAGSPLIKTIG }

val Set<Husstandsmedlem>.voksneIHusstanden get() = find { it.rolle?.rolletype == Rolletype.BIDRAGSPLIKTIG }

fun Behandling.hentMaksTilOgMedDato() = if (globalOpphørsdato != null) globalOpphørsdato!!.withDayOfMonth(1).minusDays(1) else null

fun Behandling.hentBeløpshistorikkForStønadstype(
    stønadstype: Stønadstype,
    søknadsbarn: Rolle,
) = historiskeStønader.find { it.type == stønadstype && it.kravhaver.verdi == søknadsbarn.ident }

fun Behandling.opprettUnikReferanse(postfix: String? = null) =
    "behandling_${id}_${opprettetTidspunkt.toCompactString()}${postfix?.let { "_$it" } ?: ""}"

@Converter
open class ÅrsakConverter : AttributeConverter<VirkningstidspunktÅrsakstype?, String?> {
    override fun convertToDatabaseColumn(attribute: VirkningstidspunktÅrsakstype?): String? = attribute?.name

    override fun convertToEntityAttribute(dbData: String?): VirkningstidspunktÅrsakstype? = dbData?.tilÅrsakstype()
}

class BehandlingMetadataDoConverter : ImmutableType<BehandlingMetadataDo>(BehandlingMetadataDo::class.java) {
    override fun get(
        rs: ResultSet,
        p1: Int,
        session: SharedSessionContractImplementor?,
        owner: Any?,
    ): BehandlingMetadataDo? {
        val map = rs.getObject(p1) as Map<String, String>?
        return map?.let { BehandlingMetadataDo.from(it) }
    }

    override fun set(
        st: PreparedStatement,
        value: BehandlingMetadataDo?,
        index: Int,
        session: SharedSessionContractImplementor,
    ) {
        st.setObject(index, value?.toMap())
    }

    override fun getSqlType(): Int = Types.OTHER

    override fun compare(
        p0: Any?,
        p1: Any?,
        p2: SessionFactoryImplementor?,
    ): Int = 0

    override fun fromStringValue(sequence: CharSequence?): BehandlingMetadataDo? =
        try {
            sequence?.let { JacksonUtil.fromString(sequence as String, BehandlingMetadataDo::class.java) }
        } catch (e: Exception) {
            throw IllegalArgumentException(
                String.format(
                    "Could not transform the [%s] value to a Map!",
                    sequence,
                ),
            )
        }
}

class BehandlingMetadataDo : MutableMap<String, String> by hashMapOf() {
    companion object {
        fun from(initValue: Map<String, String> = hashMapOf()): BehandlingMetadataDo {
            val dokmap = BehandlingMetadataDo()
            dokmap.putAll(initValue)
            return dokmap
        }
    }

    private val følgerAutomatiskVedtak = "følger_automatisk_vedtak"
    private val klagePåBisysVedtak = "klage_på_bisys_vedtak"

    fun setKlagePåBisysVedtak() {
        update(klagePåBisysVedtak, "true")
    }

    fun erKlagePåBisysVedtak() = get(klagePåBisysVedtak)?.toBooleanStrictOrNull() == true

    fun setFølgerAutomatiskVedtak(vedtaksid: Int?) {
        vedtaksid?.let { update(følgerAutomatiskVedtak, it.toString()) }
    }

    fun getFølgerAutomatiskVedtak(): Int? = get(følgerAutomatiskVedtak)?.toIntOrNull()

    private fun update(
        key: String,
        value: String?,
    ) {
        remove(key)
        value?.let { put(key, value) }
    }

    fun copy(): BehandlingMetadataDo = from(this)
}
