package no.nav.bidrag.behandling.transformers.vedtak.mapping.fravedtak

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Utgift
import no.nav.bidrag.behandling.database.datamodell.Utgiftspost
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatSærbidragsberegningDto
import no.nav.bidrag.behandling.dto.v2.behandling.UtgiftBeregningDto
import no.nav.bidrag.behandling.transformers.behandling.tilNotat
import no.nav.bidrag.behandling.transformers.beregning.ValiderBeregning
import no.nav.bidrag.behandling.transformers.beregning.erAvslagSomInneholderUtgifter
import no.nav.bidrag.behandling.transformers.byggResultatSærbidragsberegning
import no.nav.bidrag.behandling.transformers.sorter
import no.nav.bidrag.behandling.transformers.tilType
import no.nav.bidrag.behandling.transformers.utgift.tilBeregningDto
import no.nav.bidrag.behandling.transformers.utgift.tilDto
import no.nav.bidrag.commons.security.utils.TokenUtils
import no.nav.bidrag.commons.service.organisasjon.SaksbehandlernavnProvider
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentPerson
import no.nav.bidrag.transport.behandling.felles.grunnlag.særbidragskategori
import no.nav.bidrag.transport.behandling.felles.grunnlag.utgiftDirekteBetalt
import no.nav.bidrag.transport.behandling.felles.grunnlag.utgiftMaksGodkjentBeløp
import no.nav.bidrag.transport.behandling.felles.grunnlag.utgiftsposter
import no.nav.bidrag.transport.behandling.vedtak.response.VedtakDto
import no.nav.bidrag.transport.behandling.vedtak.response.saksnummer
import no.nav.bidrag.transport.behandling.vedtak.response.søknadId
import no.nav.bidrag.transport.behandling.vedtak.response.typeBehandling
import no.nav.bidrag.transport.behandling.vedtak.response.virkningstidspunkt
import no.nav.bidrag.transport.felles.ifTrue
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime
import no.nav.bidrag.transport.behandling.felles.grunnlag.NotatGrunnlag.NotatType as Notattype

@Component
class VedtakTilBehandlingMapping(
    val validerBeregning: ValiderBeregning,
) {
    fun VedtakDto.tilBehandling(
        vedtakId: Long,
        lesemodus: Boolean = true,
        vedtakType: Vedtakstype? = null,
        mottattdato: LocalDate? = null,
        søktFomDato: LocalDate? = null,
        soknadFra: SøktAvType? = null,
        søknadRefId: Long? = null,
        søknadId: Long? = null,
        enhet: String? = null,
        opprinneligVedtakstidspunkt: Set<LocalDateTime> = emptySet(),
        opprinneligVedtakstype: Vedtakstype? = null,
    ): Behandling {
        val opprettetAv =
            if (lesemodus) {
                this.opprettetAv
            } else {
                TokenUtils.hentSaksbehandlerIdent()
                    ?: TokenUtils.hentApplikasjonsnavn()!!
            }
        val opprettetAvNavn =
            if (lesemodus) {
                this.opprettetAvNavn
            } else {
                TokenUtils
                    .hentSaksbehandlerIdent()
                    ?.let { SaksbehandlernavnProvider.hentSaksbehandlernavn(it) }
            }
        val behandling =
            Behandling(
                id = if (lesemodus) 1 else null,
                vedtakstype = vedtakType ?: type,
                opprinneligVedtakstype = opprinneligVedtakstype,
                virkningstidspunkt = virkningstidspunkt ?: hentSøknad().søktFraDato,
                kategori = grunnlagListe.særbidragskategori?.kategori?.name,
                kategoriBeskrivelse = grunnlagListe.særbidragskategori?.beskrivelse,
                opprinneligVirkningstidspunkt =
                    virkningstidspunkt
                        ?: hentSøknad().søktFraDato,
                opprinneligVedtakstidspunkt = opprinneligVedtakstidspunkt.toMutableSet(),
                innkrevingstype =
                    this.stønadsendringListe.firstOrNull()?.innkreving
                        ?: this.engangsbeløpListe.firstOrNull()?.innkreving
                        ?: Innkrevingstype.MED_INNKREVING,
                årsak = hentVirkningstidspunkt()?.årsak,
                avslag = avslagskode(),
                klageMottattdato = if (!lesemodus) mottattdato else hentSøknad().klageMottattDato,
                søktFomDato = søktFomDato ?: hentSøknad().søktFraDato,
                soknadFra = soknadFra ?: hentSøknad().søktAv,
                mottattdato =
                    when (typeBehandling) {
                        TypeBehandling.SÆRBIDRAG -> hentSøknad().mottattDato
                        else -> mottattdato ?: hentSøknad().mottattDato
                    },
                // TODO: Er dette riktig? Hva skjer hvis det finnes flere stønadsendringer/engangsbeløp? Fungerer for Forskudd men todo fram fremtiden
                stonadstype = stønadsendringListe.firstOrNull()?.type,
                engangsbeloptype = engangsbeløpListe.firstOrNull()?.type,
                vedtaksid = null,
                soknadRefId = søknadRefId,
                refVedtaksid = vedtakId,
                behandlerEnhet = enhet ?: enhetsnummer?.verdi!!,
                opprettetAv = opprettetAv,
                opprettetAvNavn = opprettetAvNavn,
                kildeapplikasjon = if (lesemodus) kildeapplikasjon else TokenUtils.hentApplikasjonsnavn()!!,
                datoTom = null,
                saksnummer = saksnummer!!,
                soknadsid = søknadId ?: this.søknadId!!,
            )

        behandling.roller = grunnlagListe.mapRoller(behandling, lesemodus)
        behandling.inntekter = grunnlagListe.mapInntekter(behandling, lesemodus)
        behandling.husstandsmedlem = grunnlagListe.mapHusstandsmedlem(behandling)
        behandling.sivilstand = grunnlagListe.mapSivilstand(behandling, lesemodus)
        behandling.utgift = grunnlagListe.mapUtgifter(behandling, lesemodus)
        behandling.grunnlag = grunnlagListe.mapGrunnlag(behandling, lesemodus)

        notatMedType(NotatGrunnlag.NotatType.BOFORHOLD, false)?.let {
            behandling.notater.add(behandling.tilNotat(NotatGrunnlag.NotatType.BOFORHOLD, it))
        }
        notatMedType(Notattype.UTGIFTER, false)?.let {
            behandling.notater.add(behandling.tilNotat(NotatGrunnlag.NotatType.UTGIFTER, it))
        }
        notatMedType(NotatGrunnlag.NotatType.VIRKNINGSTIDSPUNKT, false)?.let {
            behandling.notater.add(behandling.tilNotat(NotatGrunnlag.NotatType.VIRKNINGSTIDSPUNKT, it))
        }
        behandling.roller.forEach { r ->
            notatMedType(NotatGrunnlag.NotatType.INNTEKT, false, grunnlagListe.hentPerson(r.ident)?.referanse)?.let {
                behandling.notater.add(behandling.tilNotat(NotatGrunnlag.NotatType.INNTEKT, it, r))
            }
        }
        behandling.søknadsbarn.forEach { r ->
            notatMedType(NotatGrunnlag.NotatType.SAMVÆR, false, grunnlagListe.hentPerson(r.ident)?.referanse)?.let {
                behandling.notater.add(behandling.tilNotat(NotatGrunnlag.NotatType.SAMVÆR, it, r))
            }
        }

        return behandling
    }

    fun VedtakDto.tilBeregningResultatSærbidrag(): ResultatSærbidragsberegningDto? =
        engangsbeløpListe.firstOrNull()?.let { engangsbeløp ->
            val behandling = tilBehandling(1)
            grunnlagListe.byggResultatSærbidragsberegning(
                behandling.virkningstidspunkt!!,
                engangsbeløp.beløp,
                Resultatkode.fraKode(engangsbeløp.resultatkode)!!,
                engangsbeløp.grunnlagReferanseListe,
                behandling.utgift?.tilBeregningDto() ?: UtgiftBeregningDto(),
                behandling.utgift
                    ?.utgiftsposter
                    ?.sorter()
                    ?.map { it.tilDto() } ?: emptyList(),
                behandling.utgift?.maksGodkjentBeløpTaMed.ifTrue { behandling.utgift?.maksGodkjentBeløp },
            )
        }

    private fun List<GrunnlagDto>.mapUtgifter(
        behandling: Behandling,
        lesemodus: Boolean,
    ): Utgift? {
        if (behandling.tilType() !== TypeBehandling.SÆRBIDRAG ||
            validerBeregning.run { behandling.tilSærbidragAvslagskode() }?.erAvslagSomInneholderUtgifter() == false
        ) {
            return null
        }
        val utgift =
            Utgift(
                behandling,
                beløpDirekteBetaltAvBp = utgiftDirekteBetalt!!.beløpDirekteBetalt,
                maksGodkjentBeløpTaMed = utgiftMaksGodkjentBeløp != null,
                maksGodkjentBeløp = utgiftMaksGodkjentBeløp?.beløp,
                maksGodkjentBeløpBegrunnelse = utgiftMaksGodkjentBeløp?.begrunnelse,
            )
        utgift.utgiftsposter =
            utgiftsposter
                .mapIndexed { index, it ->
                    Utgiftspost(
                        id = if (lesemodus) index.toLong() else null,
                        utgift = utgift,
                        dato = it.dato,
                        type = it.type,
                        godkjentBeløp = it.godkjentBeløp,
                        kravbeløp = it.kravbeløp,
                        kommentar = it.kommentar,
                        betaltAvBp = it.betaltAvBp,
                    )
                }.toMutableSet()

        return utgift
    }

//    private fun List<GrunnlagDto>.mapSamvær(
//        behandling: Behandling,
//        lesemodus: Boolean,
//    ): Set<Samvær>? {
//        val samværsperioder =
//            filtrerBasertPåEgenReferanse(
//                Grunnlagstype.SAMVÆRSPERIODE,
//            ).groupBy { it.gjelderReferanse }.map { (gjelderReferanse, perioder) ->
//                val person = hentPersonMedReferanse(gjelderReferanse)!!
//                Samvær(
//                    behandling = behandling,
//                    rolle = behandling.roller.find { it.ident == person.personIdent }!!,
//                    perioder = emptySet(),
//                )
//            }
//
//        return emptySet()
//    }
}
