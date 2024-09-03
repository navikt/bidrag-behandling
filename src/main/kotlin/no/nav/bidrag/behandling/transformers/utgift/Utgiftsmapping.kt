package no.nav.bidrag.behandling.transformers.utgift

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Utgift
import no.nav.bidrag.behandling.database.datamodell.Utgiftspost
import no.nav.bidrag.behandling.database.datamodell.særbidragKategori
import no.nav.bidrag.behandling.dto.v1.behandling.BegrunnelseDto
import no.nav.bidrag.behandling.dto.v2.behandling.SærbidragKategoriDto
import no.nav.bidrag.behandling.dto.v2.behandling.SærbidragUtgifterDto
import no.nav.bidrag.behandling.dto.v2.behandling.UtgiftBeregningDto
import no.nav.bidrag.behandling.dto.v2.behandling.UtgiftspostDto
import no.nav.bidrag.behandling.dto.v2.utgift.MaksGodkjentBeløpDto
import no.nav.bidrag.behandling.dto.v2.utgift.OppdatereUtgift
import no.nav.bidrag.behandling.dto.v2.utgift.OppdatereUtgiftResponse
import no.nav.bidrag.behandling.dto.v2.validering.UtgiftValideringsfeilDto
import no.nav.bidrag.behandling.service.NotatService.Companion.henteNotatinnhold
import no.nav.bidrag.behandling.transformers.behandling.henteRolleForNotat
import no.nav.bidrag.behandling.transformers.behandling.tilDto
import no.nav.bidrag.behandling.transformers.beregning.tilSærbidragAvslagskode
import no.nav.bidrag.behandling.transformers.erDatoForUtgiftForeldet
import no.nav.bidrag.behandling.transformers.erSærbidrag
import no.nav.bidrag.behandling.transformers.sorter
import no.nav.bidrag.behandling.transformers.validerUtgiftspost
import no.nav.bidrag.behandling.transformers.vedtak.ifTrue
import no.nav.bidrag.domene.enums.særbidrag.Særbidragskategori
import no.nav.bidrag.domene.enums.særbidrag.Utgiftstype
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import java.math.BigDecimal
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType as Notattype

val kategorierSomKreverType = listOf(Særbidragskategori.ANNET, Særbidragskategori.KONFIRMASJON)
val Behandling.kanInneholdeUtgiftBetaltAvBp get() = særbidragKategori == Særbidragskategori.KONFIRMASJON
val Utgift.totalGodkjentBeløpBp
    get() =
        behandling.kanInneholdeUtgiftBetaltAvBp.ifTrue {
            utgiftsposter.filter { it.betaltAvBp }.sumOf { it.godkjentBeløp }
        }
val Utgift.totalGodkjentBeløp get() = utgiftsposter.sumOf { it.godkjentBeløp }
val Utgift.totalKravbeløp get() = utgiftsposter.sumOf { it.kravbeløp }
val Utgift.totalBeløpBetaltAvBp
    get() = utgiftsposter.filter { it.betaltAvBp }.sumOf { it.godkjentBeløp } + beløpDirekteBetaltAvBp

fun Behandling.tilSærbidragKategoriDto() =
    SærbidragKategoriDto(
        kategori = særbidragKategori,
        beskrivelse = kategoriBeskrivelse,
    )

fun Utgift?.hentValideringsfeil() =
    UtgiftValideringsfeilDto(
        ugyldigUtgiftspost =
            this?.utgiftsposter?.any {
                OppdatereUtgift(
                    dato = it.dato,
                    type =
                        when {
                            kategorierSomKreverType.contains(behandling.særbidragKategori) -> it.type
                            else -> null
                        },
                    kravbeløp = it.kravbeløp,
                    godkjentBeløp = it.godkjentBeløp,
                    kommentar = it.kommentar,
                    betaltAvBp = it.betaltAvBp,
                    id = it.id,
                ).validerUtgiftspost(behandling).isNotEmpty()
            } ?: false,
        manglerUtgifter = this == null || utgiftsposter.isEmpty(),
    ).takeIf { it.harFeil }

fun Behandling.tilUtgiftDto() =
    utgift?.let { utgift ->
        val valideringsfeil = utgift.hentValideringsfeil()
        if (avslag != null) {
            SærbidragUtgifterDto(
                avslag = avslag,
                kategori = tilSærbidragKategoriDto(),
                begrunnelse = BegrunnelseDto(henteNotatinnhold(this, Notattype.UTGIFTER) ?: ""),
                valideringsfeil = valideringsfeil,
            )
        } else {
            SærbidragUtgifterDto(
                avslag = tilSærbidragAvslagskode(),
                beregning = utgift.tilBeregningDto(),
                kategori = tilSærbidragKategoriDto(),
                maksGodkjentBeløp = utgift.tilMaksGodkjentBeløpDto(),
                begrunnelse =
                    BegrunnelseDto(
                        innhold = henteNotatinnhold(this, Notattype.UTGIFTER),
                        gjelder = this.henteRolleForNotat(Notattype.UTGIFTER, null).tilDto(),
                    ),
                utgifter = utgift.utgiftsposter.sorter().map { it.tilDto() },
                valideringsfeil = valideringsfeil,
            )
        }
    } ?: if (erSærbidrag()) {
        SærbidragUtgifterDto(
            avslag = avslag,
            kategori = tilSærbidragKategoriDto(),
            begrunnelse =
                BegrunnelseDto(
                    innhold = henteNotatinnhold(this, Notattype.UTGIFTER),
                    gjelder = this.henteRolleForNotat(Notattype.UTGIFTER, null).tilDto(),
                ),
            valideringsfeil = utgift.hentValideringsfeil(),
        )
    } else {
        null
    }

fun Utgift.tilMaksGodkjentBeløpDto() =
    MaksGodkjentBeløpDto(
        beløp = maksGodkjentBeløp,
        kommentar = maksGodkjentBeløpKommentar,
    )

fun Utgift.tilUtgiftResponse(utgiftspostId: Long? = null) =
    if (behandling.avslag != null) {
        OppdatereUtgiftResponse(
            avslag = behandling.avslag,
            begrunnelse = henteNotatinnhold(behandling, Notattype.UTGIFTER),
            valideringsfeil = behandling.utgift.hentValideringsfeil(),
        )
    } else {
        OppdatereUtgiftResponse(
            avslag = behandling.tilSærbidragAvslagskode(),
            oppdatertUtgiftspost = utgiftsposter.find { it.id == utgiftspostId }?.tilDto(),
            utgiftposter = utgiftsposter.sorter().map { it.tilDto() },
            maksGodkjentBeløp = tilMaksGodkjentBeløpDto(),
            begrunnelse = henteNotatinnhold(behandling, Notattype.UTGIFTER),
            beregning = tilBeregningDto(),
            valideringsfeil = behandling.utgift.hentValideringsfeil(),
        )
    }

fun Utgift.tilBeregningDto() =
    UtgiftBeregningDto(
        beløpDirekteBetaltAvBp = beløpDirekteBetaltAvBp,
        totalBeløpBetaltAvBp = totalBeløpBetaltAvBp,
        totalGodkjentBeløp = totalGodkjentBeløp,
        totalKravbeløp = totalKravbeløp,
        totalGodkjentBeløpBp = totalGodkjentBeløpBp,
    )

fun Utgiftspost.tilDto() =
    UtgiftspostDto(
        id = id!!,
        kommentar = kommentar ?: "",
        type = type,
        godkjentBeløp = godkjentBeløp,
        kravbeløp = kravbeløp,
        betaltAvBp = betaltAvBp,
        dato = dato,
    )

fun OppdatereUtgift.tilUtgiftspost(utgift: Utgift) =
    Utgiftspost(
        utgift = utgift,
        kommentar = kommentar,
        type =
            when {
                kategorierSomKreverType.contains(utgift.behandling.særbidragKategori) -> type!!
                utgift.behandling.særbidragKategori == Særbidragskategori.OPTIKK -> Utgiftstype.OPTIKK.name
                utgift.behandling.særbidragKategori == Særbidragskategori.TANNREGULERING -> Utgiftstype.TANNREGULERING.name
                else -> throw HttpClientErrorException(HttpStatus.BAD_REQUEST, "Kunne ikke bestemme type for utgiftspost")
            },
        godkjentBeløp =
            if (utgift.behandling.erDatoForUtgiftForeldet(dato)) {
                BigDecimal.ZERO
            } else {
                godkjentBeløp
            },
        kravbeløp = kravbeløp,
        betaltAvBp = betaltAvBp,
        dato = dato,
    )
