package no.nav.bidrag.behandling.service

import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.verify
import no.nav.bidrag.behandling.consumer.BidragBBMConsumer
import no.nav.bidrag.behandling.consumer.BidragStønadConsumer
import no.nav.bidrag.behandling.consumer.BidragVedtakConsumer
import no.nav.bidrag.behandling.transformers.beregning.ValiderBeregning
import no.nav.bidrag.behandling.transformers.grunnlag.tilGrunnlagsreferanse
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.BehandlingTilGrunnlagMappingV2
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.VedtakGrunnlagMapper
import no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak.grunnlagsreferanse_løpende_bidrag
import no.nav.bidrag.behandling.utils.testdata.SAKSNUMMER
import no.nav.bidrag.behandling.utils.testdata.SOKNAD_ID
import no.nav.bidrag.behandling.utils.testdata.SOKNAD_ID_2
import no.nav.bidrag.behandling.utils.testdata.SOKNAD_ID_3
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.behandling.utils.testdata.testdataBM
import no.nav.bidrag.behandling.utils.testdata.testdataBP
import no.nav.bidrag.behandling.utils.testdata.testdataBarn1
import no.nav.bidrag.behandling.utils.testdata.testdataBarn2
import no.nav.bidrag.behandling.utils.testdata.testdataHusstandsmedlem1
import no.nav.bidrag.beregn.barnebidrag.BeregnSamværsklasseApi
import no.nav.bidrag.beregn.vedtak.Vedtaksfiltrering
import no.nav.bidrag.commons.service.sjablon.SjablonService
import no.nav.bidrag.commons.web.mock.stubSjablonProvider
import no.nav.bidrag.commons.web.mock.stubSjablonService
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.beregning.Samværsklasse
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.vedtak.BehandlingsrefKilde
import no.nav.bidrag.domene.enums.vedtak.Beslutningstype
import no.nav.bidrag.domene.enums.vedtak.Innkrevingstype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.domene.enums.vedtak.Vedtakskilde
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.sak.Saksnummer
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.beregning.felles.BidragBeregningRequestDto
import no.nav.bidrag.transport.behandling.beregning.felles.BidragBeregningResponsDto
import no.nav.bidrag.transport.behandling.beregning.felles.BidragBeregningResponsDto.BidragBeregning
import no.nav.bidrag.transport.behandling.felles.grunnlag.LøpendeBidrag
import no.nav.bidrag.transport.behandling.felles.grunnlag.LøpendeBidragGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.bidragspliktig
import no.nav.bidrag.transport.behandling.felles.grunnlag.erPerson
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentAllePersoner
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentPerson
import no.nav.bidrag.transport.behandling.felles.grunnlag.innholdTilObjekt
import no.nav.bidrag.transport.behandling.felles.grunnlag.personIdent
import no.nav.bidrag.transport.behandling.felles.grunnlag.personObjekt
import no.nav.bidrag.transport.behandling.stonad.response.LøpendeBidragssak
import no.nav.bidrag.transport.behandling.stonad.response.LøpendeBidragssakerResponse
import no.nav.bidrag.transport.behandling.vedtak.response.BehandlingsreferanseDto
import no.nav.bidrag.transport.behandling.vedtak.response.HentVedtakForStønadResponse
import no.nav.bidrag.transport.behandling.vedtak.response.StønadsendringDto
import no.nav.bidrag.transport.behandling.vedtak.response.VedtakForStønad
import no.nav.bidrag.transport.behandling.vedtak.response.VedtakPeriodeDto
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension
import stubPersonConsumer
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@ExtendWith(SpringExtension::class)
class BeregningEvnevurderingServiceTest {
    @MockkBean
    lateinit var bidragStønadConsumer: BidragStønadConsumer

    @MockkBean
    lateinit var bidragVedtakConsumer: BidragVedtakConsumer

    @MockkBean
    lateinit var bidragBBMConsumer: BidragBBMConsumer

    val beregingVedtaksfiltrering: Vedtaksfiltrering = Vedtaksfiltrering()

    lateinit var evnevurderingService: BeregningEvnevurderingService
    lateinit var vedtakGrunnlagMapper: VedtakGrunnlagMapper
    lateinit var behandlingTilGrunnlagMapping: BehandlingTilGrunnlagMappingV2
    lateinit var personService: PersonService
    lateinit var sjablonService: SjablonService
    lateinit var validerBeregning: ValiderBeregning

    @BeforeEach
    fun init() {
        clearAllMocks(recordedCalls = true)
        personService = PersonService(stubPersonConsumer())
        sjablonService = stubSjablonService()
        validerBeregning = ValiderBeregning()
        behandlingTilGrunnlagMapping = BehandlingTilGrunnlagMappingV2(personService, BeregnSamværsklasseApi(sjablonService))

        evnevurderingService =
            BeregningEvnevurderingService(bidragStønadConsumer, bidragVedtakConsumer, bidragBBMConsumer, beregingVedtaksfiltrering)
        vedtakGrunnlagMapper = VedtakGrunnlagMapper(behandlingTilGrunnlagMapping, validerBeregning, evnevurderingService, personService)
        stubSjablonProvider()
        initMockTestdata()
    }

    @Test
    fun `skal opprette grunnlag for løpende bidrag`(): Unit =
        vedtakGrunnlagMapper.run {
            val behandling = opprettGyldigBehandlingForBeregningOgVedtak(typeBehandling = TypeBehandling.SÆRBIDRAG, generateId = true)
            val personobjekter = behandling.tilPersonobjekter().toList().toMutableList()
            val resultat = evnevurderingService.hentLøpendeBidragForBehandling(behandling).tilGrunnlagDto(behandling.tilPersonobjekter())

            resultat shouldHaveSize 3
            personobjekter.addAll(resultat.filter { it.erPerson() })
            val løpendeBidrag = resultat.find { it.type == Grunnlagstype.LØPENDE_BIDRAG }
            assertSoftly(løpendeBidrag!!) {
                it.referanse shouldBe grunnlagsreferanse_løpende_bidrag
                it.gjelderReferanse shouldBe behandling.tilPersonobjekter().bidragspliktig!!.referanse
                it.grunnlagsreferanseListe.shouldBeEmpty()
                val innhold = this.innholdTilObjekt<LøpendeBidragGrunnlag>()
                innhold.løpendeBidragListe shouldHaveSize 3
                val barn1Objekt = personobjekter.hentPerson(testdataBarn1.ident)
                val barn2Objekt = personobjekter.hentPerson(testdataBarn2.ident)
                val husstandsmedlemObjekt = personobjekter.hentPerson(testdataHusstandsmedlem1.ident)
                assertSoftly(
                    innhold.løpendeBidragListe.find { it.gjelderBarn == barn1Objekt!!.referanse }!!,
                ) {
                    type shouldBe Stønadstype.BIDRAG
                    løpendeBeløp shouldBe BigDecimal(5111)
                    samværsklasse shouldBe Samværsklasse.SAMVÆRSKLASSE_1
                    beregnetBeløp shouldBe BigDecimal(4515)
                    faktiskBeløp shouldBe BigDecimal(5160)
                    gjelderBarn shouldBe barn1Objekt!!.referanse
                }
                assertSoftly(
                    innhold.løpendeBidragListe.find { it.gjelderBarn == barn2Objekt!!.referanse }!!,
                ) {
                    type shouldBe Stønadstype.BIDRAG
                    løpendeBeløp shouldBe BigDecimal(5222)
                    samværsklasse shouldBe Samværsklasse.SAMVÆRSKLASSE_1
                    beregnetBeløp shouldBe BigDecimal(5934)
                    faktiskBeløp shouldBe BigDecimal(5930)
                    gjelderBarn shouldBe barn2Objekt!!.referanse
                }
                assertSoftly(
                    innhold.løpendeBidragListe.find {
                        it.gjelderBarn == husstandsmedlemObjekt!!.referanse
                    }!!,
                ) {
                    type shouldBe Stønadstype.BIDRAG18AAR
                    løpendeBeløp shouldBe BigDecimal(5333)
                    samværsklasse shouldBe Samværsklasse.SAMVÆRSKLASSE_0
                    beregnetBeløp shouldBe BigDecimal(7533)
                    faktiskBeløp shouldBe BigDecimal(4433)
                    gjelderBarn shouldBe husstandsmedlemObjekt!!.referanse
                }
            }

            verify(exactly = 1) {
                bidragBBMConsumer.hentBeregning(
                    withArg {
                        it.hentBeregningerFor shouldHaveSize 3
                        it.hentBeregningerFor[0].personidentBarn shouldBe Personident(testdataBarn1.ident)
                        it.hentBeregningerFor[0].søknadsid shouldBe SOKNAD_ID.toString()
                        it.hentBeregningerFor[1].personidentBarn shouldBe Personident(testdataBarn2.ident)
                        it.hentBeregningerFor[1].søknadsid shouldBe SOKNAD_ID_2.toString()
                        it.hentBeregningerFor[2].personidentBarn shouldBe Personident(testdataHusstandsmedlem1.ident)
                        it.hentBeregningerFor[2].søknadsid shouldBe SOKNAD_ID_3.toString()
                    },
                )
            }
            verify(exactly = 3) { bidragVedtakConsumer.hentVedtakForStønad(any()) }
            verify(exactly = 1) {
                bidragVedtakConsumer.hentVedtakForStønad(
                    withArg {
                        it.type shouldBe Stønadstype.BIDRAG
                        it.kravhaver shouldBe Personident(testdataBarn1.ident)
                    },
                )
            }
            verify(exactly = 1) {
                bidragVedtakConsumer.hentVedtakForStønad(
                    withArg {
                        it.type shouldBe Stønadstype.BIDRAG
                        it.kravhaver shouldBe Personident(testdataBarn2.ident)
                    },
                )
            }
            verify(exactly = 1) {
                bidragVedtakConsumer.hentVedtakForStønad(
                    withArg {
                        it.type shouldBe Stønadstype.BIDRAG18AAR
                        it.kravhaver shouldBe Personident(testdataHusstandsmedlem1.ident)
                    },
                )
            }
            verify(exactly = 1) {
                bidragStønadConsumer.hentLøpendeBidrag(
                    withArg {
                        it.skyldner shouldBe Personident(testdataBP.ident)
                    },
                )
            }
        }

    @Test
    fun `skal opprette grunnlag for barn som ikke er i personobjekter listen`(): Unit =
        vedtakGrunnlagMapper.run {
            val behandling = opprettGyldigBehandlingForBeregningOgVedtak(typeBehandling = TypeBehandling.SÆRBIDRAG, generateId = true)
            val personobjekter = behandling.tilPersonobjekter().toList().toMutableList()
            val resultat = evnevurderingService.hentLøpendeBidragForBehandling(behandling).tilGrunnlagDto(behandling.tilPersonobjekter())

            resultat shouldHaveSize 3
            personobjekter.addAll(resultat.filter { it.erPerson() })
            val løpendeBidrag = resultat.find { it.type == Grunnlagstype.LØPENDE_BIDRAG }
            assertSoftly(løpendeBidrag!!) {
                it.referanse shouldBe grunnlagsreferanse_løpende_bidrag
                it.gjelderReferanse shouldBe behandling.tilPersonobjekter().bidragspliktig!!.referanse
                it.grunnlagsreferanseListe.shouldBeEmpty()
                val innhold = this.innholdTilObjekt<LøpendeBidragGrunnlag>()
                innhold.løpendeBidragListe shouldHaveSize 3

                val barn1Objekt = personobjekter.hentPerson(testdataBarn1.ident)
                barn1Objekt shouldNotBe null
                assertSoftly(barn1Objekt!!) {
                    it.type shouldBe Grunnlagstype.PERSON_SØKNADSBARN
                    val personobjekt = it.personObjekt
                    personobjekt.ident shouldBe Personident(testdataBarn1.ident)
                    personobjekt.fødselsdato shouldBe testdataBarn1.fødselsdato
                    personobjekt.navn shouldBe null
                }

                val barn2Objekt = personobjekter.hentPerson(testdataBarn2.ident)
                barn2Objekt shouldNotBe null
                assertSoftly(barn2Objekt!!) {
                    it.type shouldBe Grunnlagstype.PERSON_BARN_BIDRAGSPLIKTIG
                    val personobjekt = it.personObjekt
                    personobjekt.ident shouldBe Personident(testdataBarn2.ident)
                    personobjekt.fødselsdato shouldBe testdataBarn2.fødselsdato
                    personobjekt.navn shouldBe null
                }

                val husstandsmedlemObjekt = personobjekter.hentPerson(testdataHusstandsmedlem1.ident)
                husstandsmedlemObjekt shouldNotBe null
                assertSoftly(husstandsmedlemObjekt!!) {
                    it.type shouldBe Grunnlagstype.PERSON_BARN_BIDRAGSPLIKTIG
                    val personobjekt = it.personObjekt
                    personobjekt.ident shouldBe Personident(testdataHusstandsmedlem1.ident)
                    personobjekt.fødselsdato shouldBe testdataHusstandsmedlem1.fødselsdato
                    personobjekt.navn shouldBe null
                }
            }
        }

    @Test
    fun `skal opprette grunnlag for løpende bidrag hvis vedtakfilter returnerer null`(): Unit =
        vedtakGrunnlagMapper.run {
            val response = opprettVedtakForStønadRespons(testdataBarn2.ident, Stønadstype.BIDRAG)
            every {
                bidragVedtakConsumer.hentVedtakForStønad(
                    coMatch {
                        it.kravhaver.verdi == testdataBarn2.ident
                    },
                )
            } returns
                response.copy(
                    vedtakListe =
                        listOf(
                            response.vedtakListe.first().copy(
                                stønadsendring =
                                    response.vedtakListe.first().stønadsendring.copy(
                                        beslutning = Beslutningstype.STADFESTELSE,
                                    ),
                            ),
                        ),
                )

            val behandling = opprettGyldigBehandlingForBeregningOgVedtak(typeBehandling = TypeBehandling.SÆRBIDRAG, generateId = true)
            val resultat = evnevurderingService.hentLøpendeBidragForBehandling(behandling).tilGrunnlagDto(behandling.tilPersonobjekter())

            resultat shouldHaveSize 3
            val løpendeBidrag = resultat.find { it.type == Grunnlagstype.LØPENDE_BIDRAG }
            assertSoftly(løpendeBidrag!!) {
                it.referanse shouldBe grunnlagsreferanse_løpende_bidrag
                it.gjelderReferanse shouldBe behandling.tilPersonobjekter().bidragspliktig!!.referanse
                it.grunnlagsreferanseListe.shouldBeEmpty()
                val innhold = this.innholdTilObjekt<LøpendeBidragGrunnlag>()
                innhold.løpendeBidragListe shouldHaveSize 3
                assertSoftly(innhold.løpendeBidragListe[0]) {
                    type shouldBe Stønadstype.BIDRAG
                    faktiskBeløp shouldNotBe BigDecimal.ZERO
                    beregnetBeløp shouldNotBe BigDecimal.ZERO
                    samværsklasse shouldBe Samværsklasse.SAMVÆRSKLASSE_1
                }
                assertSoftly(innhold.løpendeBidragListe[1]) {
                    type shouldBe Stønadstype.BIDRAG
                    faktiskBeløp shouldBe BigDecimal.ZERO
                    beregnetBeløp shouldBe BigDecimal.ZERO
                    samværsklasse shouldBe Samværsklasse.SAMVÆRSKLASSE_0
                }
                assertSoftly(innhold.løpendeBidragListe[2]) {
                    type shouldBe Stønadstype.BIDRAG18AAR
                    faktiskBeløp shouldNotBe BigDecimal.ZERO
                    beregnetBeløp shouldNotBe BigDecimal.ZERO
                    samværsklasse shouldBe Samværsklasse.SAMVÆRSKLASSE_0
                }
            }
        }

    @Test
    fun `skal opprette grunnlag for løpende bidrag hvis BP ikke har noen løpende bidrag`(): Unit =
        vedtakGrunnlagMapper.run {
            every {
                bidragStønadConsumer.hentLøpendeBidrag(any())
            } returns LøpendeBidragssakerResponse(emptyList())

            val behandling = opprettGyldigBehandlingForBeregningOgVedtak(typeBehandling = TypeBehandling.SÆRBIDRAG, generateId = true)
            val resultat = evnevurderingService.hentLøpendeBidragForBehandling(behandling).tilGrunnlagDto(behandling.tilPersonobjekter())

            resultat shouldHaveSize 1
            val løpendeBidrag = resultat.find { it.type == Grunnlagstype.LØPENDE_BIDRAG }
            assertSoftly(løpendeBidrag!!) {
                val innhold = this.innholdTilObjekt<LøpendeBidragGrunnlag>()
                innhold.løpendeBidragListe shouldHaveSize 0
            }
        }

    @Test
    fun `skal opprette grunnlag for løpende bidrag hvis det ikke finnes noen beregning i BBM`(): Unit =
        vedtakGrunnlagMapper.run {
            every {
                bidragBBMConsumer.hentBeregning(any())
            } returns BidragBeregningResponsDto(emptyList())

            val behandling = opprettGyldigBehandlingForBeregningOgVedtak(typeBehandling = TypeBehandling.SÆRBIDRAG, generateId = true)
            val resultat = evnevurderingService.hentLøpendeBidragForBehandling(behandling).tilGrunnlagDto(behandling.tilPersonobjekter())

            resultat shouldHaveSize 3
            val løpendeBidrag = resultat.find { it.type == Grunnlagstype.LØPENDE_BIDRAG }
            val personer = resultat.hentAllePersoner().toList()
            val person1 = personer.find { testdataBarn2.ident == it.personIdent }
            val person2 = personer.find { testdataHusstandsmedlem1.ident == it.personIdent }
            val søknadsbarn = behandling.søknadsbarn.first()
            assertSoftly(løpendeBidrag!!) {
                val innhold = this.innholdTilObjekt<LøpendeBidragGrunnlag>()
                innhold.løpendeBidragListe shouldHaveSize 3
                assertSoftly(innhold.løpendeBidragListe.finnForKravhaver(person1!!.referanse)!!) {
                    it.type shouldBe Stønadstype.BIDRAG
                    it.gjelderBarn shouldBe person1.referanse
                    it.faktiskBeløp shouldBe BigDecimal.ZERO
                    it.beregnetBeløp shouldBe BigDecimal.ZERO
                    it.samværsklasse shouldBe Samværsklasse.SAMVÆRSKLASSE_0
                    it.løpendeBeløp shouldBe BigDecimal(5222)
                    it.saksnummer shouldBe Saksnummer(SAKSNUMMER)
                }
                assertSoftly(innhold.løpendeBidragListe.finnForKravhaver(person2!!.referanse)!!) {
                    it.type shouldBe Stønadstype.BIDRAG18AAR
                    it.gjelderBarn shouldBe person2.referanse
                    it.faktiskBeløp shouldBe BigDecimal.ZERO
                    it.beregnetBeløp shouldBe BigDecimal.ZERO
                    it.samværsklasse shouldBe Samværsklasse.SAMVÆRSKLASSE_0
                    it.løpendeBeløp shouldBe BigDecimal(5333)
                    it.saksnummer shouldBe Saksnummer(SAKSNUMMER)
                }
                assertSoftly(innhold.løpendeBidragListe.finnForKravhaver(søknadsbarn.tilGrunnlagsreferanse())!!) {
                    it.type shouldBe Stønadstype.BIDRAG
                    it.gjelderBarn shouldBe søknadsbarn.tilGrunnlagsreferanse()
                    it.faktiskBeløp shouldBe BigDecimal.ZERO
                    it.beregnetBeløp shouldBe BigDecimal.ZERO
                    it.samværsklasse shouldBe Samværsklasse.SAMVÆRSKLASSE_0
                    it.løpendeBeløp shouldBe BigDecimal(5111)
                    it.saksnummer shouldBe Saksnummer(SAKSNUMMER)
                }
            }
        }

    private fun initMockTestdata() {
        every { bidragStønadConsumer.hentLøpendeBidrag(any()) } returns
            LøpendeBidragssakerResponse(
                listOf(
                    opprettLøpendeBidraggsak(testdataBarn1.ident, Stønadstype.BIDRAG).copy(
                        løpendeBeløp = BigDecimal(5111),
                    ),
                    opprettLøpendeBidraggsak(testdataBarn2.ident, Stønadstype.BIDRAG).copy(
                        løpendeBeløp = BigDecimal(5222),
                    ),
                    opprettLøpendeBidraggsak(testdataHusstandsmedlem1.ident, Stønadstype.BIDRAG18AAR).copy(
                        løpendeBeløp = BigDecimal(5333),
                    ),
                ),
            )

        every {
            bidragVedtakConsumer.hentVedtakForStønad(
                coMatch {
                    it.kravhaver.verdi == testdataBarn1.ident
                },
            )
        } returns
            opprettVedtakForStønadRespons(testdataBarn1.ident, Stønadstype.BIDRAG)

        every {
            bidragVedtakConsumer.hentVedtakForStønad(
                coMatch {
                    it.kravhaver.verdi == testdataBarn2.ident
                },
            )
        } returns
            opprettVedtakForStønadRespons(testdataBarn2.ident, Stønadstype.BIDRAG)

        every {
            bidragVedtakConsumer.hentVedtakForStønad(
                coMatch {
                    it.kravhaver.verdi == testdataHusstandsmedlem1.ident
                },
            )
        } returns
            opprettVedtakForStønadRespons(testdataHusstandsmedlem1.ident, Stønadstype.BIDRAG18AAR)

        every { bidragBBMConsumer.hentBeregning(any()) } answers {
            val request = it.invocation.args[0] as BidragBeregningRequestDto
            BidragBeregningResponsDto(
                request.hentBeregningerFor.map {
                    opprettBidragBeregning(it.personidentBarn.verdi, it.stønadstype)
                },
            )
        }
    }
}

fun opprettBidragBeregning(
    kravhaver: String,
    stønadstype: Stønadstype,
) = BidragBeregning(
    saksnummer = SAKSNUMMER,
    stønadstype = stønadstype,
    personidentBarn = Personident(kravhaver),
    beregnetBeløp =
        if (kravhaver == testdataBarn1.ident) {
            BigDecimal(4515)
        } else if (kravhaver == testdataBarn2.ident) {
            BigDecimal(5934)
        } else {
            BigDecimal(7533)
        },
    faktiskBeløp =
        if (kravhaver == testdataBarn1.ident) {
            BigDecimal(5160)
        } else if (kravhaver == testdataBarn2.ident) {
            BigDecimal(5930)
        } else {
            BigDecimal(4433)
        },
    beløpSamvær =
        if (kravhaver == testdataBarn1.ident) {
            BigDecimal(450)
        } else if (kravhaver == testdataBarn2.ident) {
            BigDecimal(489)
        } else {
            BigDecimal(0)
        },
    samværsklasse =
        if (kravhaver == testdataBarn1.ident) {
            Samværsklasse.SAMVÆRSKLASSE_1
        } else if (kravhaver == testdataBarn2.ident) {
            Samværsklasse.SAMVÆRSKLASSE_1
        } else {
            Samværsklasse.SAMVÆRSKLASSE_0
        },
    datoSøknad = LocalDate.parse("2024-07-01"),
    gjelderFom = LocalDate.parse("2024-07-01"),
)

fun opprettVedtakForStønadRespons(
    kravhaver: String,
    stønadstype: Stønadstype,
) = HentVedtakForStønadResponse(
    listOf(
        opprettVedtakForStønad(kravhaver, stønadstype),
    ),
)

fun opprettVedtakForStønad(
    kravhaver: String,
    stønadstype: Stønadstype,
) = VedtakForStønad(
    vedtaksid = 1,
    type = Vedtakstype.FASTSETTELSE,
    kilde = Vedtakskilde.MANUELT,
    vedtakstidspunkt = LocalDateTime.parse("2024-01-01T00:00:00"),
    behandlingsreferanser =
        listOf(
            BehandlingsreferanseDto(
                kilde = BehandlingsrefKilde.BISYS_SØKNAD,
                referanse =
                    if (kravhaver == testdataBarn1.ident) {
                        SOKNAD_ID.toString()
                    } else if (kravhaver == testdataBarn2.ident) {
                        SOKNAD_ID_2.toString()
                    } else {
                        SOKNAD_ID_3.toString()
                    },
            ),
        ),
    stønadsendring =
        StønadsendringDto(
            type = stønadstype,
            sak = Saksnummer(SAKSNUMMER),
            skyldner = Personident(testdataBP.ident),
            kravhaver = Personident(kravhaver),
            mottaker = Personident(testdataBM.ident),
            førsteIndeksreguleringsår = 0,
            innkreving = Innkrevingstype.MED_INNKREVING,
            beslutning = Beslutningstype.ENDRING,
            omgjørVedtakId = null,
            eksternReferanse = "123456",
            grunnlagReferanseListe = emptyList(),
            periodeListe =
                listOf(
                    VedtakPeriodeDto(
                        periode = ÅrMånedsperiode(LocalDate.parse("2024-07-01"), null),
                        beløp = BigDecimal(5160),
                        valutakode = "NOK",
                        resultatkode = "KBB",
                        delytelseId = null,
                        grunnlagReferanseListe = emptyList(),
                    ),
                ),
        ),
)

fun opprettLøpendeBidraggsak(
    kravhaver: String = testdataBarn1.ident,
    type: Stønadstype = Stønadstype.BIDRAG,
) = LøpendeBidragssak(
    sak = Saksnummer(SAKSNUMMER),
    type = type,
    kravhaver = Personident(kravhaver),
    løpendeBeløp = BigDecimal(5160),
)

private fun List<LøpendeBidrag>.finnForKravhaver(gjelderBarnReferanse: String) = find { it.gjelderBarn == gjelderBarnReferanse }
