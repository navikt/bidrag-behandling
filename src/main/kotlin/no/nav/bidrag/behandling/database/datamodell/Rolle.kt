package no.nav.bidrag.behandling.database.datamodell

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import no.nav.bidrag.behandling.database.datamodell.extensions.ÅrsakConverter
import no.nav.bidrag.behandling.database.datamodell.json.ForholdsmessigFordelingRolle
import no.nav.bidrag.behandling.oppdateringAvBoforholdFeilet
import no.nav.bidrag.behandling.service.hentNyesteIdent
import no.nav.bidrag.behandling.service.hentPersonVisningsnavn
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.jsonListeTilObjekt
import no.nav.bidrag.beregn.core.util.justerPeriodeTomOpphørsdato
import no.nav.bidrag.domene.enums.behandling.Behandlingstatus
import no.nav.bidrag.domene.enums.behandling.Behandlingstema
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.BeregnTil
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.VirkningstidspunktÅrsakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.vedtak.response.VedtakPeriodeDto
import no.nav.bidrag.transport.felles.commonObjectmapper
import org.hibernate.annotations.ColumnTransformer
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

@Entity(name = "rolle")
@SQLDelete(sql = "UPDATE rolle SET deleted = true WHERE id=?")
@SQLRestriction(value = "deleted=false")
open class Rolle(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "behandling_id", nullable = false)
    open val behandling: Behandling,
    @Enumerated(EnumType.STRING)
    open val rolletype: Rolletype,
    open var ident: String?,
    open var fødselsdato: LocalDate,
    open val opprettet: LocalDateTime = LocalDateTime.now(),
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null,
    open val navn: String? = null,
    open val deleted: Boolean = false,
    open var harGebyrsøknad: Boolean = false,
    @Column(columnDefinition = "jsonb", name = "manuelt_overstyrt_gebyr")
    @ColumnTransformer(write = "?::jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    open var gebyr: GebyrRolle? = null,
    open var innbetaltBeløp: BigDecimal? = null,
    @Column(name = "forrige_sivilstandshistorikk", columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    open var forrigeSivilstandshistorikk: String? = null,
    @OneToMany(
        fetch = FetchType.EAGER,
        mappedBy = "rolle",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    open var grunnlag: MutableSet<Grunnlag> = mutableSetOf(),
    @OneToMany(
        fetch = FetchType.EAGER,
        mappedBy = "rolle",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    open var notat: MutableSet<Notat> = mutableSetOf(),
    @OneToOne(
        fetch = FetchType.LAZY,
        mappedBy = "rolle",
        cascade = [CascadeType.MERGE, CascadeType.PERSIST],
        orphanRemoval = true,
    )
    open val husstandsmedlem: Husstandsmedlem? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "person_id",
        nullable = true,
    )
    open var person: Person? = null,
    open var opphørsdato: LocalDate? = null,
    @Enumerated(EnumType.STRING)
    open var behandlingstema: Behandlingstema? = null,
    @Enumerated(EnumType.STRING)
    open var behandlingstatus: Behandlingstatus? = null,
    @Enumerated(EnumType.STRING)
    open var beregnTil: BeregnTil? = null,
    open var virkningstidspunkt: LocalDate? = null,
    open var opprinneligVirkningstidspunkt: LocalDate? = null,
    @Convert(converter = ÅrsakConverter::class)
    open var årsak: VirkningstidspunktÅrsakstype? = null,
    @Enumerated(EnumType.STRING)
    open var avslag: Resultatkode? = null,
    // Vedtaksid beregning av aldersjustering skal basere seg på. Dette velges manuelt av saksbehandler
    open var grunnlagFraVedtak: Int? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", name = "grunnlag_fra_vedtak_json")
    open var grunnlagFraVedtakListe: List<GrunnlagFraVedtak> = emptyList(),
    @Enumerated(EnumType.STRING)
    open var innkrevingstype: Innkrevingstype? = null,
    open var innkrevesFraDato: LocalDate? = null,
    @Enumerated(EnumType.STRING)
    open var stønadstype: Stønadstype? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", name = "forholdsmessig_fordeling")
    open var forholdsmessigFordeling: ForholdsmessigFordelingRolle? = null,
) {
    val virkningstidspunktRolle get() = virkningstidspunkt ?: behandling.virkningstidspunktEllerSøktFomDato

    fun sakForSøknad(søknadsid: Long) =
        forholdsmessigFordeling
            ?.søknader
            ?.find { it.søknadsid == søknadsid }
            ?.saksnummer ?: saksnummer

    val saksnummer get() = forholdsmessigFordeling?.tilhørerSak ?: behandling.saksnummer
    val gebyrSøknader get() =
        if (harGebyrsøknad) {
            gebyr?.gebyrSøknader
                ?: setOf(
                    GebyrRolleSøknad(
                        saksnummer = behandling.saksnummer,
                        søknadsid = behandling.soknadsid!!,
                        manueltOverstyrtGebyr =
                            RolleManueltOverstyrtGebyr(
                                overstyrGebyr = gebyr?.overstyrGebyr == true,
                                ilagtGebyr = gebyr?.ilagtGebyr,
                                begrunnelse = gebyr?.begrunnelse,
                                beregnetIlagtGebyr = gebyr?.beregnetIlagtGebyr,
                            ),
                    ),
                )
        } else {
            emptySet()
        }.toMutableSet()

    fun opppdaterGebyrTilNyVersjon(): GebyrRolle {
        gebyr = gebyr?.let {
            if (it.gebyrSøknader.isEmpty()) {
                it.gebyrSøknader = gebyrSøknader
            }
            it
        } ?: GebyrRolle(gebyrSøknader = gebyrSøknader)
        return gebyr!!
    }

    fun oppdaterGebyr(
        søknadsid: Long,
        manueltOverstyrtGebyr: RolleManueltOverstyrtGebyr,
    ) {
        if (!manueltOverstyrtGebyr.overstyrGebyr) {
            manueltOverstyrtGebyr.begrunnelse = null
        }
        val gebyr = hentEllerOpprettGebyr()
        val gebyrSøknad = gebyr.finnEllerOpprettGebyrForSøknad(søknadsid, sakForSøknad(søknadsid))
        gebyrSøknad.manueltOverstyrtGebyr = manueltOverstyrtGebyr
        gebyr.overstyrGebyr = manueltOverstyrtGebyr.overstyrGebyr
        gebyr.beregnetIlagtGebyr = manueltOverstyrtGebyr.beregnetIlagtGebyr
        gebyr.begrunnelse = manueltOverstyrtGebyr.begrunnelse
        gebyr.ilagtGebyr = manueltOverstyrtGebyr.ilagtGebyr
    }

    fun fjernGebyr(søknadsid: Long) {
        val gebyr = hentEllerOpprettGebyr()
        gebyr.gebyrSøknader =
            gebyr.gebyrSøknader
                .filter { it.søknadsid != søknadsid }
                .toMutableSet()
        if (gebyr.gebyrSøknader.isEmpty()) {
            harGebyrsøknad = false
        }
    }

    fun gebyrForSøknad(søknadsid: Long): GebyrRolleSøknad =
        hentEllerOpprettGebyr().finnEllerOpprettGebyrForSøknad(søknadsid, sakForSøknad(søknadsid))

    fun hentEllerOpprettGebyr() =
        gebyr?.let {
            opppdaterGebyrTilNyVersjon()
        } ?: GebyrRolle()

    val bidragsmottaker get() =
        behandling.alleBidragsmottakere.find {
            forholdsmessigFordeling?.bidragsmottaker != null && it.ident == forholdsmessigFordeling?.bidragsmottaker ||
                it.forholdsmessigFordeling?.tilhørerSak == forholdsmessigFordeling?.tilhørerSak ||
                forholdsmessigFordeling == null && it.forholdsmessigFordeling == null ||
                forholdsmessigFordeling?.tilhørerSak == behandling.saksnummer && it.forholdsmessigFordeling == null
        }
    val beregningGrunnlagFraVedtak get() = grunnlagFraVedtak ?: grunnlagFraVedtakForInnkreving?.vedtak
    val grunnlagFraVedtakForInnkreving get() = grunnlagFraVedtakListe.find { it.aldersjusteringForÅr == null }
    val personident get() = person?.ident?.let { Personident(it) } ?: this.ident?.let { Personident(it) }

    val erDirekteAvslag get() = avslag != null
    val opphørsdatoYearMonth get() = opphørsdato?.let { YearMonth.from(it) }
    val opphørTilDato get() = justerPeriodeTomOpphørsdato(opphørsdato)
    val henteFødselsdato get() = person?.fødselsdato ?: this.fødselsdato
    val opphørSistePeriode get() = opphørTilDato != null

    override fun toString(): String =
        "Rolle(id=$id, behandling=${behandling.id}, rolletype=$rolletype, ident=$ident, fødselsdato=$fødselsdato, opprettet=$opprettet, navn=$navn, deleted=$deleted, innbetaltBeløp=$innbetaltBeløp)"
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class GrunnlagFraVedtak(
    @Schema(
        description =
            "Årstall for aldersjustering av grunnlag. " +
                "Brukes hvis det er et vedtak som skal brukes for aldersjustering av grunnlag. " +
                "Dette er relevant ved omgjøring/klagebehanding i bidrag ellers aldersjusteres det for inneværende år eller ikke er relevant",
    )
    val aldersjusteringForÅr: Int? = null,
    val vedtak: Int? = null,
    val grunnlagFraOmgjøringsvedtak: Boolean = false,
    val vedtakstidspunkt: LocalDateTime? = null,
    @Schema(
        description =
            "Perioder i vedtaket som er valgt. " +
                "Brukes når vedtakstype er innkreving og det er valgt å innkreve en vedtak fra NAV som opprinnelig var uten innkreving",
    )
    val perioder: List<VedtakPeriodeDto> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GebyrRolle(
    var overstyrGebyr: Boolean = true,
    var ilagtGebyr: Boolean? = false,
    var begrunnelse: String? = null,
    var beregnetIlagtGebyr: Boolean? = false,
    var gebyrSøknader: MutableSet<GebyrRolleSøknad> = mutableSetOf(),
) {
    fun leggTilGebyr(gebyrSøknad: GebyrRolleSøknad) {
        gebyrSøknader.removeIf { it.søknadsid == gebyrSøknad.søknadsid }
        gebyrSøknader.add(gebyrSøknad)
    }

    fun finnGebyrForSøknad(søknadsid: Long): GebyrRolleSøknad? = gebyrSøknader.find { it.søknadsid == søknadsid }

    fun finnEllerOpprettGebyrForSøknad(
        søknadsid: Long,
        saksnummer: String,
    ): GebyrRolleSøknad =
        finnGebyrForSøknad(søknadsid)
            ?: GebyrRolleSøknad(
                saksnummer = saksnummer,
                søknadsid = søknadsid,
                null,
                null,
                RolleManueltOverstyrtGebyr(overstyrGebyr = false),
            )
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class GebyrRolleSøknad(
    val saksnummer: String,
    val søknadsid: Long,
    var referanse: String? = null,
    val behandlingid: Long? = null,
    var manueltOverstyrtGebyr: RolleManueltOverstyrtGebyr? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as GebyrRolleSøknad
        return saksnummer == other.saksnummer && søknadsid == other.søknadsid && behandlingid == other.behandlingid
    }

    override fun hashCode(): Int = saksnummer.hashCode() * 31 + søknadsid.hashCode() + (behandlingid?.hashCode() ?: 0)
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class RolleManueltOverstyrtGebyr(
    val overstyrGebyr: Boolean = true,
    val ilagtGebyr: Boolean? = false,
    var begrunnelse: String? = null,
    val beregnetIlagtGebyr: Boolean? = false,
)

fun Rolle.tilPersonident() = ident?.let { Personident(it) }

fun Rolle.tilNyestePersonident() = ident?.let { hentNyesteIdent(it) }

fun Rolle.hentNavn() = navn ?: hentPersonVisningsnavn(ident) ?: ""

fun Rolle.lagreSivilstandshistorikk(historikk: Set<Sivilstand>) {
    forrigeSivilstandshistorikk = commonObjectmapper.writeValueAsString(historikk.tilSerialiseringsformat())
}

fun Collection<GebyrRolleSøknad>.removeDuplicates(): MutableSet<GebyrRolleSøknad> =
    sortedByDescending { it.manueltOverstyrtGebyr != null }
        .distinctBy { Pair(it.saksnummer, it.søknadsid) }
        .toMutableSet()

fun Rolle.leggTilGebyr(fraRolle: Rolle) {
    val gebyr = gebyr ?: GebyrRolle()
    this@leggTilGebyr.gebyr =
        gebyr.let {
            it.gebyrSøknader.addAll(
                fraRolle.gebyrSøknader,
            )
            it.gebyrSøknader = it.gebyrSøknader.removeDuplicates()
            it
        }
}

fun Rolle.leggTilGebyr(gebyrSøknader: List<GebyrRolleSøknad>) {
    val gebyr = hentEllerOpprettGebyr()
    this@leggTilGebyr.gebyr =
        gebyr.let {
            it.gebyrSøknader.addAll(
                gebyrSøknader,
            )
            it.gebyrSøknader = it.gebyrSøknader.removeDuplicates()
            it
        }
}

fun Set<Sivilstand>.tilSerialiseringsformat() =
    this.map {
        SivilstandUtenBehandling(
            datoFom = it.datoFom,
            datoTom = it.datoTom,
            kilde = it.kilde,
            sivilstand = it.sivilstand,
        )
    }

fun Rolle.henteLagretSivilstandshistorikk(behandling: Behandling): Set<Sivilstand> {
    val sivilstand =
        jsonListeTilObjekt<SivilstandUtenBehandling>(
            forrigeSivilstandshistorikk ?: oppdateringAvBoforholdFeilet(
                "Fant ikke tidligere lagret sivilstandshistorikk for " +
                    "bidragsmottaker i behandling ${behandling.id}",
            ),
        )

    return sivilstand
        .map {
            Sivilstand(
                datoFom = it.datoFom!!,
                datoTom = it.datoTom,
                kilde = it.kilde,
                sivilstand = it.sivilstand,
                behandling = behandling,
            )
        }.toSet()
}

data class SivilstandUtenBehandling(
    val datoFom: LocalDate? = null,
    val datoTom: LocalDate? = null,
    @Enumerated(EnumType.STRING)
    val sivilstand: Sivilstandskode,
    @Enumerated(EnumType.STRING)
    val kilde: Kilde,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
)
