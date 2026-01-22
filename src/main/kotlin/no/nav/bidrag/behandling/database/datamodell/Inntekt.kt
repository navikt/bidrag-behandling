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
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.finnBeregnTilDatoBehandling
import no.nav.bidrag.behandling.transformers.vedtak.nullIfEmpty
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.felles.toYearMonth
import java.math.BigDecimal
import java.time.LocalDate

@Entity(name = "inntekt")
open class Inntekt(
    @Enumerated(EnumType.STRING)
    @Column(name = "inntektsrapportering")
    open var type: Inntektsrapportering,
    open var belop: BigDecimal,
    open var datoFom: LocalDate?,
    open var datoTom: LocalDate?,
    @Deprecated("Bruk heller rolle")
    open var ident: String,
    @Enumerated(EnumType.STRING)
    open var kilde: Kilde,
    open var taMed: Boolean,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null,
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "behandling_id", nullable = false)
    open val behandling: Behandling? = null,
    @OneToMany(
        fetch = FetchType.EAGER,
        mappedBy = "inntekt",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    open var inntektsposter: MutableSet<Inntektspost> = mutableSetOf(),
    @Deprecated("Bruk heller gjelderBarnRolle")
    open var gjelderBarn: String? = null,
    open var opprinneligFom: LocalDate? = null,
    open var opprinneligTom: LocalDate? = null,
    @OneToOne(
        fetch = FetchType.LAZY,
        cascade = [CascadeType.MERGE, CascadeType.PERSIST],
        orphanRemoval = true,
    )
    @JoinColumn(name = "rolle_id", nullable = true)
    open val rolle: Rolle? = null,
    @OneToOne(
        fetch = FetchType.LAZY,
        cascade = [CascadeType.MERGE, CascadeType.PERSIST],
        orphanRemoval = true,
    )
    @JoinColumn(name = "gjelder_barn_rolle_id", nullable = true)
    open val gjelderBarnRolle: Rolle? = null,
) {
    val gjelderIdent get() = rolle?.ident ?: ident
    val gjelderBarnIdent get() = gjelderBarnRolle?.ident ?: gjelderBarn

    // TODO: Bytt dette til å bruke rolle etter migrering
    val gjelderRolle get() =
        if (rolle != null) {
            rolle
        } else {
            behandling?.roller?.find { it.ident == gjelderIdent }
        }

    // TODO: Bytt dette til gjelderRolle etter migrering
    val gjelderSøknadsbarn get() =
        if (gjelderBarnRolle != null) {
            gjelderBarnRolle
        } else {
            behandling?.søknadsbarn?.find { it.ident == gjelderBarn }
        }

    fun tilhørerSammePerson(
        ident: String?,
        rolleId: Long?,
    ) = if (rolleId != null && rolle != null) {
        rolle!!.id == rolleId
    } else {
        this.ident.nullIfEmpty() == ident.nullIfEmpty()
    }

    fun tilhørerSammePerson(annenInntekt: Inntekt) =
        if (annenInntekt.rolle == null || this.rolle == null) {
            this.ident.nullIfEmpty() == annenInntekt.ident.nullIfEmpty()
        } else {
            erSammeRolle(annenInntekt.rolle!!)
        }

    fun tilhørerSammeBarn(annenInntekt: Inntekt) =
        if (annenInntekt.gjelderBarnRolle == null || this.gjelderBarnRolle == null) {
            this.gjelderBarn.nullIfEmpty() == annenInntekt.gjelderBarn.nullIfEmpty()
        } else {
            inntektGjelderBarn(annenInntekt.rolle!!)
        }

    fun tilhørerSammeBarn(
        ident: String?,
        rolleId: Long?,
    ) = if (rolleId != null && gjelderBarnRolle != null) {
        gjelderBarnRolle!!.id == rolleId
    } else {
        this.gjelderBarn.nullIfEmpty() == ident.nullIfEmpty()
    }

    fun erSammeRolle(rolle: Rolle) = if (this.rolle != null) this.rolle!!.erSammeRolle(rolle) else this.ident == rolle.ident

    fun erSammeRolle(
        ident: String,
        stønadstype: Stønadstype?,
    ) = if (this.rolle != null) {
        this.rolle!!.erSammeRolle(ident, stønadstype)
    } else {
        this.ident == ident
    }

    fun inntektGjelderBarn(rolle: Rolle) =
        if (gjelderBarnRolle != null) {
            gjelderBarnRolle!!.erSammeRolle(rolle)
        } else {
            gjelderBarn == rolle.ident
        }

    val opphørsdato get() = if (gjelderSøknadsbarn != null) gjelderSøknadsbarn!!.opphørsdato else behandling?.globalOpphørsdato
    val beregnTilDato get() =
        if (gjelderSøknadsbarn != null) {
            behandling?.finnBeregnTilDatoBehandling(gjelderSøknadsbarn)
        } else {
            behandling?.globalOpphørsdato
        }
    val opphørTilDato get() = if (gjelderSøknadsbarn != null) gjelderSøknadsbarn!!.opphørTilDato else behandling?.opphørTilDato
    val datoFomEllerOpprinneligFom get() = datoFom ?: opprinneligFom
    val datoTomEllerOpprinneligFom get() = datoTom ?: opprinneligTom

    val opprinneligPeriode get() = opprinneligFom?.let { ÅrMånedsperiode(it, opprinneligTom) }
    val periode get() = datoFom?.let { ÅrMånedsperiode(it, datoTom) }

    override fun toString(): String =
        try {
            "Inntekt($type, beløp=$belop, datoFom=$datoFom, " +
                "datoTom=$datoTom, ident='$ident', gjelderBarn='$gjelderBarn'," +
                "opprinneligFom=$opprinneligFom, opprinneligTom=$opprinneligTom, " +
                " kilde=$kilde, taMed=$taMed, id=$id, behandling=${behandling?.id})"
        } catch (e: Exception) {
            "Inntekt${this.hashCode()}"
        }
}
