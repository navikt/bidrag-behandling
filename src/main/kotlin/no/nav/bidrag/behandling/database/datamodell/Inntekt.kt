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
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.finnBeregnTilDatoBehandling
import no.nav.bidrag.domene.enums.diverse.Kilde
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
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
    open var gjelderBarn: String? = null,
    open var opprinneligFom: LocalDate? = null,
    open var opprinneligTom: LocalDate? = null,
) {
    val gjelderSøknadsbarn get() = behandling?.søknadsbarn?.find { it.ident == gjelderBarn }
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
