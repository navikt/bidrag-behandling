package no.nav.bidrag.behandling.service

import com.fasterxml.jackson.databind.node.POJONode
import io.getunleash.Unleash
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.bidrag.behandling.consumer.BidragSakConsumer
import no.nav.bidrag.behandling.consumer.BidragVedtakConsumer
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.tilNyestePersonident
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingFraVedtakRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OpprettBehandlingResponse
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatBeregningBarnDto
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatSærbidragsberegningDto
import no.nav.bidrag.behandling.rolleManglerIdent
import no.nav.bidrag.behandling.toggleFatteVedtakName
import no.nav.bidrag.behandling.transformers.behandling.tilKanBehandlesINyLøsningRequest
import no.nav.bidrag.behandling.transformers.tilType
import no.nav.bidrag.behandling.transformers.utgift.totalBeløpBetaltAvBp
import no.nav.bidrag.behandling.transformers.vedtak.StønadsendringPeriode
import no.nav.bidrag.behandling.transformers.vedtak.mapping.fravedtak.VedtakTilBehandlingMapping
import no.nav.bidrag.behandling.transformers.vedtak.mapping.fravedtak.tilBeregningResultat
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.VedtakGrunnlagMapper
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.byggGrunnlagGenerelt
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.byggStønadsendringerForVedtak
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.tilBehandlingreferanseListe
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.tilOpprettRequestDto
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.tilSkyldner
import no.nav.bidrag.behandling.transformers.vedtak.reelMottakerEllerBidragsmottaker
import no.nav.bidrag.behandling.transformers.vedtak.tilVedtakDto
import no.nav.bidrag.behandling.transformers.vedtak.validerGrunnlagsreferanser
import no.nav.bidrag.commons.util.secureLogger
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.Beslutningstype
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakskilde
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.organisasjon.Enhetsnummer
import no.nav.bidrag.domene.sak.Saksnummer
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.LøpendeBidragGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentAllePersoner
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.personObjekt
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettEngangsbeløpRequestDto
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettPeriodeRequestDto
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettStønadsendringRequestDto
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettVedtakRequestDto
import no.nav.bidrag.transport.behandling.vedtak.response.VedtakDto
import no.nav.bidrag.transport.behandling.vedtak.response.behandlingId
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDateTime

private val LOGGER = KotlinLogging.logger {}

@Service
class VedtakService(
    private val behandlingService: BehandlingService,
    private val grunnlagService: GrunnlagService,
    private val notatOpplysningerService: NotatOpplysningerService,
    private val beregningService: BeregningService,
    private val tilgangskontrollService: TilgangskontrollService,
    private val vedtakConsumer: BidragVedtakConsumer,
    private val sakConsumer: BidragSakConsumer,
    private val unleashInstance: Unleash,
    private val mapper: VedtakGrunnlagMapper,
    private val vedtakTilBehandlingMapping: VedtakTilBehandlingMapping,
    private val vedtakValiderBehandlingService: ValiderBehandlingService,
) {
    fun konverterVedtakTilBehandlingForLesemodus(vedtakId: Long): Behandling? {
        try {
            LOGGER.info { "Konverterer vedtak $vedtakId for lesemodus" }
            val vedtak = vedtakConsumer.hentVedtak(vedtakId) ?: return null
            tilgangskontrollService.sjekkTilgangVedtak(vedtak)

            secureLogger.info { "Konverterer vedtak $vedtakId for lesemodus med innhold $vedtak" }
            return vedtakTilBehandlingMapping.run { vedtak.tilBehandling(vedtakId, lesemodus = true) }
        } catch (e: Exception) {
            LOGGER.error(e) { "Det skjedde en feil ved konvertering av vedtak $vedtakId for lesemodus" }
            throw e
        }
    }

    private fun hentOpprinneligVedtakstype(vedtak: VedtakDto): Vedtakstype {
        val vedtaksiderStønadsendring = vedtak.stønadsendringListe.mapNotNull { it.omgjørVedtakId }
        val vedtaksiderEngangsbeløp = vedtak.engangsbeløpListe.mapNotNull { it.omgjørVedtakId }
        val refererTilVedtakId = (vedtaksiderEngangsbeløp + vedtaksiderStønadsendring).toSet()
        if (refererTilVedtakId.isNotEmpty()) {
            val opprinneligVedtak = vedtakConsumer.hentVedtak(refererTilVedtakId.first().toLong())!!
            return hentOpprinneligVedtakstype(opprinneligVedtak)
        }
        return vedtak.type
    }

    private fun hentOpprinneligVedtakstidspunkt(vedtak: VedtakDto): Set<LocalDateTime> {
        val vedtaksiderStønadsendring = vedtak.stønadsendringListe.mapNotNull { it.omgjørVedtakId }
        val vedtaksiderEngangsbeløp = vedtak.engangsbeløpListe.mapNotNull { it.omgjørVedtakId }
        val refererTilVedtakId = (vedtaksiderEngangsbeløp + vedtaksiderStønadsendring).toSet()
        if (refererTilVedtakId.isNotEmpty()) {
            return refererTilVedtakId
                .flatMap { vedtaksid ->
                    val opprinneligVedtak = vedtakConsumer.hentVedtak(vedtaksid.toLong())!!
                    hentOpprinneligVedtakstidspunkt(opprinneligVedtak)
                }.toSet() + setOf(vedtak.vedtakstidspunkt)
        }
        return setOf(vedtak.vedtakstidspunkt)
    }

    @Transactional
    fun opprettBehandlingFraVedtak(
        request: OpprettBehandlingFraVedtakRequest,
        refVedtaksid: Long,
    ): OpprettBehandlingResponse {
        try {
            LOGGER.info {
                "Oppretter behandling fra vedtak $refVedtaksid med søktAv ${request.søknadFra}, " +
                    "søktFomDato ${request.søktFomDato}, mottatDato ${request.mottattdato}, søknadId ${request.søknadsid}: $request"
            }
            val konvertertBehandling =
                konverterVedtakTilBehandling(request, refVedtaksid)
                    ?: throw RuntimeException("Fant ikke vedtak for vedtakid $refVedtaksid")

            tilgangskontrollService.sjekkTilgangBehandling(konvertertBehandling)
            val behandlingDo = behandlingService.opprettBehandling(konvertertBehandling)
            grunnlagService.oppdatereGrunnlagForBehandling(behandlingDo)

            LOGGER.info {
                "Opprettet behandling ${behandlingDo.id} fra vedtak $refVedtaksid med søktAv ${request.søknadFra}, " +
                    "søktFomDato ${request.søktFomDato}, mottatDato ${request.mottattdato}, søknadId ${request.søknadsid}: $request"
            }
            return OpprettBehandlingResponse(behandlingDo.id!!)
        } catch (e: Exception) {
            LOGGER.error(e) { "Det skjedde en feil ved opprettelse av behandling fra vedtak $refVedtaksid: ${e.message}" }
            throw e
        }
    }

    fun konverterVedtakTilBehandling(
        request: OpprettBehandlingFraVedtakRequest,
        refVedtaksid: Long,
    ): Behandling? {
        // TODO: Sjekk tilganger
        val vedtak =
            vedtakConsumer.hentVedtak(refVedtaksid) ?: return null
        if (vedtak.behandlingId == null) {
            throw HttpClientErrorException(
                HttpStatus.BAD_REQUEST,
                "Vedtak $refVedtaksid er ikke fattet gjennom ny løsning og kan derfor ikke konverteres til behandling",
            )
        }
        return vedtakTilBehandlingMapping.run {
            vedtak.tilBehandling(
                vedtakId = refVedtaksid,
                søktFomDato = request.søktFomDato,
                mottattdato = request.mottattdato,
                soknadFra = request.søknadFra,
                vedtakType = request.vedtakstype,
                søknadRefId = request.søknadsreferanseid,
                enhet = request.behandlerenhet,
                søknadId = request.søknadsid,
                lesemodus = false,
                opprinneligVedtakstidspunkt = hentOpprinneligVedtakstidspunkt(vedtak).toSet(),
                opprinneligVedtakstype = hentOpprinneligVedtakstype(vedtak),
            )
        }
    }

    fun konverterVedtakTilBeregningResultat(vedtakId: Long): List<ResultatBeregningBarnDto> {
        val vedtak = vedtakConsumer.hentVedtak(vedtakId) ?: return emptyList()
        return vedtak.tilBeregningResultat()
    }

    fun konverterVedtakTilBeregningResultatSærbidrag(vedtakId: Long): ResultatSærbidragsberegningDto? {
        val vedtak = vedtakConsumer.hentVedtak(vedtakId) ?: return null
        return vedtakTilBehandlingMapping.run { vedtak.tilBeregningResultatSærbidrag() }
    }

    fun fatteVedtak(behandlingId: Long): Int {
        val behandling = behandlingService.hentBehandlingById(behandlingId)
        behandling.validerKanFatteVedtak()
        return when (behandling.tilType()) {
            TypeBehandling.FORSKUDD -> fatteVedtakForskudd(behandling)
            TypeBehandling.SÆRBIDRAG -> fatteVedtakSærbidrag(behandling)
            else -> throw HttpClientErrorException(
                HttpStatus.BAD_REQUEST,
                "Fatte vedtak av behandlingstype ${behandling.tilType()} støttes ikke",
            )
        }
    }

    fun fatteVedtakSærbidrag(behandling: Behandling): Int {
        val behandlingId = behandling.id!!
        val isEnabled = unleashInstance.isEnabled(toggleFatteVedtakName, false)
        if (isEnabled.not()) {
            throw HttpClientErrorException(
                HttpStatus.PRECONDITION_FAILED,
                "Fattevedtak er ikke aktivert",
            )
        }
        vedtakValiderBehandlingService.validerKanBehandlesINyLøsning(behandling.tilKanBehandlesINyLøsningRequest())
        mapper.validering.run {
            behandling.validerTekniskForBeregningAvSærbidrag()
            behandling.validerForBeregningSærbidrag()
        }

        val request =
            mapper.validering.run {
                if (behandling.erDirekteAvslagUtenBeregning()) {
                    behandling.byggOpprettVedtakRequestForAvslagSærbidrag()
                } else {
                    behandling.byggOpprettVedtakRequestSærbidrag()
                }
            }
        request.validerGrunnlagsreferanser()
        secureLogger.info { "Fatter vedtak for særbidrag behandling $behandlingId med forespørsel $request" }
        val response = vedtakConsumer.fatteVedtak(request)
        behandlingService.oppdaterVedtakFattetStatus(
            behandlingId,
            vedtaksid = response.vedtaksid.toLong(),
        )
        opprettNotat(behandling)
        LOGGER.info {
            "Fattet vedtak for særbidrag behandling $behandlingId med vedtaksid ${response.vedtaksid}"
        }
        return response.vedtaksid
    }

    fun fatteVedtakForskudd(behandling: Behandling): Int {
        val behandlingId = behandling.id!!
        mapper.validering.run { behandling.validerForBeregning() }

        val request =
            if (behandling.avslag != null) {
                behandling.byggOpprettVedtakRequestForAvslag()
            } else {
                behandling.byggOpprettVedtakRequestForskudd()
            }

        request.validerGrunnlagsreferanser()
        secureLogger.info { "Fatter vedtak for behandling $behandlingId med forespørsel $request" }
        val response = vedtakConsumer.fatteVedtak(request)
        behandlingService.oppdaterVedtakFattetStatus(
            behandlingId,
            vedtaksid = response.vedtaksid.toLong(),
        )
        opprettNotat(behandling)
        LOGGER.info {
            "Fattet vedtak for behandling $behandlingId med ${
                behandling.årsak?.let { "årsakstype $it" }
                    ?: "avslagstype ${behandling.avslag}"
            } med vedtaksid ${response.vedtaksid}"
        }
        return response.vedtaksid
    }

    fun behandlingTilVedtakDto(behandlingId: Long): VedtakDto {
        mapper.validering.run {
            val behandling = behandlingService.hentBehandlingById(behandlingId)
            val request =
                when (behandling.tilType()) {
                    TypeBehandling.SÆRBIDRAG ->
                        if (behandling.erDirekteAvslagUtenBeregning()) {
                            behandling.byggOpprettVedtakRequestForAvslagSærbidrag()
                        } else {
                            behandling.byggOpprettVedtakRequestSærbidrag()
                        }

                    TypeBehandling.FORSKUDD ->
                        if (behandling.avslag !=
                            null
                        ) {
                            behandling.byggOpprettVedtakRequestForAvslag()
                        } else {
                            behandling.byggOpprettVedtakRequestForskudd()
                        }

                    else -> throw HttpClientErrorException(
                        HttpStatus.BAD_REQUEST,
                        "Behandlingstype ${behandling.tilType()} støttes ikke",
                    )
                }
            return request.tilVedtakDto()
        }
    }

    private fun Behandling.byggOpprettVedtakRequestObjekt(): OpprettVedtakRequestDto =
        OpprettVedtakRequestDto(
            enhetsnummer = Enhetsnummer(behandlerEnhet),
            vedtakstidspunkt = LocalDateTime.now(),
            type = vedtakstype,
            stønadsendringListe = emptyList(),
            engangsbeløpListe = emptyList(),
            behandlingsreferanseListe = tilBehandlingreferanseListe(),
            grunnlagListe = emptyList(),
            kilde = Vedtakskilde.MANUELT,
            fastsattILand = null,
            innkrevingUtsattTilDato = null,
            // Settes automatisk av bidrag-vedtak basert på token
            opprettetAv = null,
        )

    private fun Behandling.byggOpprettVedtakRequestForAvslag(): OpprettVedtakRequestDto =
        mapper.run {
            val sak = sakConsumer.hentSak(saksnummer)
            val grunnlagListe = byggGrunnlagGenereltAvslag()

            return byggOpprettVedtakRequestObjekt()
                .copy(
                    stønadsendringListe =
                        søknadsbarn.map {
                            OpprettStønadsendringRequestDto(
                                innkreving = Innkrevingstype.MED_INNKREVING,
                                skyldner = tilSkyldner(),
                                omgjørVedtakId = refVedtaksid?.toInt(),
                                kravhaver =
                                    it.tilNyestePersonident()
                                        ?: rolleManglerIdent(Rolletype.BARN, id!!),
                                mottaker =
                                    roller
                                        .reelMottakerEllerBidragsmottaker(
                                            sak.hentRolleMedFnr(it.ident!!),
                                        ),
                                sak = Saksnummer(saksnummer),
                                type = stonadstype!!,
                                beslutning = Beslutningstype.ENDRING,
                                grunnlagReferanseListe = grunnlagListe.map { it.referanse },
                                periodeListe =
                                    listOf(
                                        OpprettPeriodeRequestDto(
                                            periode = ÅrMånedsperiode(virkningstidspunktEllerSøktFomDato, null),
                                            beløp = null,
                                            resultatkode = avslag!!.name,
                                            valutakode = "NOK",
                                            grunnlagReferanseListe = emptyList(),
                                        ),
                                    ),
                            )
                        },
                    grunnlagListe = (grunnlagListe + tilPersonobjekter()).map(GrunnlagDto::tilOpprettRequestDto),
                )
        }

    private fun Behandling.byggOpprettVedtakRequestForskudd(): OpprettVedtakRequestDto {
        val behandling = this
        val sak = sakConsumer.hentSak(saksnummer)
        val beregning = beregningService.beregneForskudd(id!!)

        mapper.run {
            val stønadsendringPerioder =
                beregning.map { it.byggStønadsendringerForVedtak(behandling) }

            val grunnlagListeVedtak = byggGrunnlagForVedtak()
            val stønadsendringGrunnlagListe = byggGrunnlagGenerelt()

            val grunnlagListe =
                (
                    grunnlagListeVedtak +
                        stønadsendringPerioder.flatMap(
                            StønadsendringPeriode::grunnlag,
                        ) + stønadsendringGrunnlagListe
                ).toSet()

            return byggOpprettVedtakRequestObjekt().copy(
                stønadsendringListe =
                    stønadsendringPerioder.map {
                        OpprettStønadsendringRequestDto(
                            innkreving = Innkrevingstype.MED_INNKREVING,
                            skyldner = tilSkyldner(),
                            omgjørVedtakId = refVedtaksid?.toInt(),
                            kravhaver =
                                it.barn.tilNyestePersonident()
                                    ?: rolleManglerIdent(Rolletype.BARN, id!!),
                            mottaker =
                                roller
                                    .reelMottakerEllerBidragsmottaker(
                                        sak.hentRolleMedFnr(it.barn.ident!!),
                                    ),
                            sak = Saksnummer(saksnummer),
                            type = stonadstype!!,
                            beslutning = Beslutningstype.ENDRING,
                            grunnlagReferanseListe = stønadsendringGrunnlagListe.map(GrunnlagDto::referanse),
                            periodeListe = it.perioder,
                            // Settes null for forskudd men skal settes til riktig verdi for bidrag
                            førsteIndeksreguleringsår = null,
                        )
                    },
                grunnlagListe = grunnlagListe.map(GrunnlagDto::tilOpprettRequestDto),
            )
        }
    }

    private fun Behandling.byggOpprettVedtakRequestForAvslagSærbidrag(): OpprettVedtakRequestDto {
        mapper.run {
            val sak = sakConsumer.hentSak(saksnummer)
            val grunnlagListe = byggGrunnlagGenereltAvslag()
            val barn = søknadsbarn.first()

            return byggOpprettVedtakRequestObjekt()
                .copy(
                    engangsbeløpListe =
                        listOf(
                            OpprettEngangsbeløpRequestDto(
                                type = engangsbeloptype!!,
                                beløp = null,
                                resultatkode = tilSærbidragAvslagskode()!!.name,
                                valutakode = "NOK",
                                betaltBeløp = null,
                                innkreving = innkrevingstype!!,
                                skyldner = tilSkyldner(),
                                omgjørVedtakId = refVedtaksid?.toInt(),
                                kravhaver =
                                    barn.tilNyestePersonident()
                                        ?: rolleManglerIdent(Rolletype.BARN, id!!),
                                mottaker =
                                    roller
                                        .reelMottakerEllerBidragsmottaker(
                                            sak.hentRolleMedFnr(barn.ident!!),
                                        ),
                                sak = Saksnummer(saksnummer),
                                beslutning = Beslutningstype.ENDRING,
                                grunnlagReferanseListe = grunnlagListe.map(GrunnlagDto::referanse),
                            ),
                        ),
                    grunnlagListe = (grunnlagListe + tilPersonobjekter()).map(GrunnlagDto::tilOpprettRequestDto),
                )
        }
    }

    private fun Behandling.byggOpprettVedtakRequestSærbidrag(): OpprettVedtakRequestDto {
        mapper.run {
            val sak = sakConsumer.hentSak(saksnummer)
            val beregning = beregningService.beregneSærbidrag(id!!)
            val resultat = beregning.beregnetSærbidragPeriodeListe.first().resultat
            val (grunnlagListeVedtak, grunnlaglisteGenerelt) =
                if (resultat.resultatkode == Resultatkode.GODKJENT_BELØP_ER_LAVERE_ENN_FORSKUDDSSATS) {
                    byggGrunnlagForAvslagUgyldigUtgifter()
                } else {
                    val personobjekterFraBeregning = beregning.grunnlagListe.hentAllePersoner().toMutableSet() as MutableSet<GrunnlagDto>
                    listOf(byggGrunnlagForVedtak(personobjekterFraBeregning), byggGrunnlagGenerelt())
                }

            val grunnlagliste = (grunnlagListeVedtak + grunnlaglisteGenerelt + beregning.grunnlagListe).toSet()

            val grunnlagslisteEngangsbeløp =
                grunnlaglisteGenerelt +
                    beregning.grunnlagListe.filter { it.type == Grunnlagstype.SLUTTBEREGNING_SÆRBIDRAG }

            val barn = søknadsbarn.first()

            return byggOpprettVedtakRequestObjekt().copy(
                engangsbeløpListe =
                    listOf(
                        OpprettEngangsbeløpRequestDto(
                            type = engangsbeloptype!!,
                            beløp = resultat.beløp,
                            resultatkode = resultat.resultatkode.name,
                            valutakode = "NOK",
                            betaltBeløp = utgift!!.totalBeløpBetaltAvBp,
                            innkreving = innkrevingstype!!,
                            skyldner = tilSkyldner(),
                            omgjørVedtakId = refVedtaksid?.toInt(),
                            kravhaver =
                                barn.tilNyestePersonident()
                                    ?: rolleManglerIdent(Rolletype.BARN, id!!),
                            mottaker =
                                roller
                                    .reelMottakerEllerBidragsmottaker(
                                        sak.hentRolleMedFnr(barn.ident!!),
                                    ),
                            sak = Saksnummer(saksnummer),
                            beslutning = Beslutningstype.ENDRING,
                            grunnlagReferanseListe = grunnlagslisteEngangsbeløp.map(GrunnlagDto::referanse),
                        ),
                    ),
                grunnlagListe = grunnlagliste.map(GrunnlagDto::tilOpprettRequestDto),
            )
        }
    }

    /**
     * Slå sammen personobjekter hvor grunnlagstypen er ulik men referer til samme person.
     * LØPENDE_BIDRAG grunnlaget opprettes før INNHENTET_HUSSTANDSMEDLEM grunnlaget opprettes. Derfor kan det hende at begge har opprette grunnlag for samme person
     * det er mest beskrivende å bruke PERSON_HUSSTANDSMEDLEM istedenfor PERSON_BARN_BIDRAGSPLIKTIG. Derfor ersattes grunnlag og referansen til PERSON_HUSSTANDSMEDLEM
     */
    private fun Set<GrunnlagDto>.slåSammenPersonobjekter(): Set<GrunnlagDto> {
        val personerIkkeBarnBidragspliktig = this.hentAllePersoner().filter { it.type != Grunnlagstype.PERSON_BARN_BIDRAGSPLIKTIG }
        val duplikatPersonBarnBidragspliktig =
            this
                .filter { it.type == Grunnlagstype.PERSON_BARN_BIDRAGSPLIKTIG }
                .mapNotNull { grunnlagDto ->
                    personerIkkeBarnBidragspliktig
                        .find { it.personObjekt.ident == grunnlagDto.personObjekt.ident }
                        ?.referanse
                        ?.let { grunnlagDto.referanse to it }
                }.toMap()

        return this
            .filterNot {
                it.type == Grunnlagstype.PERSON_BARN_BIDRAGSPLIKTIG &&
                    duplikatPersonBarnBidragspliktig.containsKey(it.referanse)
            }.map { grunnlagDto ->
                if (grunnlagDto.type != Grunnlagstype.LØPENDE_BIDRAG) return@map grunnlagDto

                val løpendeBidragGrunnlag = grunnlagDto.innholdTilObjekt<LøpendeBidragGrunnlag>()
                val oppdatertLøpendeBidragListe =
                    løpendeBidragGrunnlag.løpendeBidragListe.map { lb ->
                        lb.copy(gjelderBarn = duplikatPersonBarnBidragspliktig[lb.gjelderBarn] ?: lb.gjelderBarn)
                    }
                grunnlagDto.copy(innhold = POJONode(løpendeBidragGrunnlag.copy(løpendeBidragListe = oppdatertLøpendeBidragListe)))
            }.toSet()
    }

    private fun Behandling.validerKanFatteVedtak() {
        if (erVedtakFattet) vedtakAlleredeFattet()
        if (virkningstidspunkt == null) {
            throw HttpClientErrorException(HttpStatus.BAD_REQUEST, "Virkningstidspunkt må settes")
        }

        val erVirkningstidspunktSenereEnnOpprinnerligVirknignstidspunkt =
            erKlageEllerOmgjøring &&
                opprinneligVirkningstidspunkt != null &&
                virkningstidspunkt?.isAfter(opprinneligVirkningstidspunkt) == true
        if (erVirkningstidspunktSenereEnnOpprinnerligVirknignstidspunkt) {
            throw HttpClientErrorException(
                HttpStatus.BAD_REQUEST,
                "Virkningstidspunkt ikke være senere enn opprinnelig virkningstidspunkt",
            )
        }

        if (saksnummer.isEmpty()) {
            throw HttpClientErrorException(HttpStatus.BAD_REQUEST, "Saksnummer mangler")
        }
    }

    private fun opprettNotat(behandling: Behandling) {
        try {
            notatOpplysningerService.opprettNotat(behandling.id!!)
        } catch (e: Exception) {
            LOGGER.error(
                e,
            ) { "Det skjedde en feil ved opprettelse av notat for behandling ${behandling.id} og vedtaksid ${behandling.vedtaksid}" }
        }
    }

    private fun Behandling.vedtakAlleredeFattet(): Nothing =
        throw HttpClientErrorException(
            HttpStatus.BAD_REQUEST,
            "Vedtak er allerede fattet for behandling $id med vedtakId $vedtaksid",
        )
}
