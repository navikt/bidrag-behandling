package no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak

import com.fasterxml.jackson.databind.node.POJONode
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.fantIkkeFødselsdatoTilSøknadsbarn
import no.nav.bidrag.behandling.fantIkkeRolleISak
import no.nav.bidrag.behandling.service.BeregningEvnevurderingService
import no.nav.bidrag.behandling.service.PersonService
import no.nav.bidrag.behandling.transformers.beregning.EvnevurderingBeregningResultat
import no.nav.bidrag.behandling.transformers.beregning.ValiderBeregning
import no.nav.bidrag.behandling.transformers.grunnlag.manglerRolleIGrunnlag
import no.nav.bidrag.behandling.transformers.grunnlag.valider
import no.nav.bidrag.behandling.transformers.tilType
import no.nav.bidrag.behandling.vedtakmappingFeilet
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.beregning.Samværsklasse
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.sak.Saksnummer
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.beregning.felles.BeregnGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.LøpendeBidrag
import no.nav.bidrag.transport.behandling.felles.grunnlag.LøpendeBidragGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.Person
import no.nav.bidrag.transport.behandling.felles.grunnlag.bidragsmottaker
import no.nav.bidrag.transport.behandling.felles.grunnlag.bidragspliktig
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentPerson
import no.nav.bidrag.transport.behandling.felles.grunnlag.tilPersonreferanse
import no.nav.bidrag.transport.behandling.stonad.response.LøpendeBidragssak
import no.nav.bidrag.transport.felles.toCompactString
import no.nav.bidrag.transport.sak.BidragssakDto
import no.nav.bidrag.transport.sak.RolleDto
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

fun finnBeregnTilDato(virkningstidspunkt: LocalDate) =
    maxOf(YearMonth.now().plusMonths(1).atDay(1), virkningstidspunkt!!.plusMonths(1).withDayOfMonth(1))

@Component
class VedtakGrunnlagMapper(
    private val mapper: BehandlingTilGrunnlagMappingV2,
    val validering: ValiderBeregning,
    private val beregningEvnevurderingService: BeregningEvnevurderingService,
    private val personService: PersonService,
) {
    fun Behandling.tilSærbidragAvslagskode() = validering.run { tilSærbidragAvslagskode() }

    fun Behandling.tilPersonobjekter(søknadsbarnRolle: Rolle? = null) = mapper.run { tilPersonobjekter(søknadsbarnRolle) }

    fun BidragssakDto.hentRolleMedFnr(fnr: String): RolleDto =
        roller.firstOrNull { personService.hentNyesteIdent(it.fødselsnummer?.verdi) == personService.hentNyesteIdent(fnr) }
            ?: fantIkkeRolleISak(saksnummer.verdi, fnr)

    fun Behandling.byggGrunnlagForAvslagUgyldigUtgifter() =
        mapper.run {
            listOf(
                tilPersonobjekter() + byggGrunnlagUtgiftsposter() +
                    byggGrunnlagUtgiftDirekteBetalt() +
                    byggGrunnlagUtgiftMaksGodkjentBeløp(),
                byggGrunnlagGenereltAvslag(),
            )
        }

    fun byggGrunnlagForBeregning(
        behandling: Behandling,
        søknadsbarnRolle: Rolle,
    ): BeregnGrunnlag {
        mapper.run {
            behandling.run {
                val personobjekter = tilPersonobjekter(søknadsbarnRolle)
                val søknadsbarn = søknadsbarnRolle.tilGrunnlagPerson()
                val bostatusBarn = tilGrunnlagBostatus(personobjekter)
                val inntekter = tilGrunnlagInntekt(personobjekter, søknadsbarn, false)
                val grunnlagsliste = (personobjekter + bostatusBarn + inntekter).toMutableSet()

                when (tilType()) {
                    TypeBehandling.FORSKUDD ->
                        grunnlagsliste.addAll(
                            tilGrunnlagSivilstand(
                                personobjekter.bidragsmottaker ?: manglerRolleIGrunnlag(Rolletype.BIDRAGSMOTTAKER, id),
                            ),
                        )

                    TypeBehandling.SÆRBIDRAG -> {
                        grunnlagsliste.add(tilGrunnlagUtgift())
                        val grunnlagLøpendeBidrag =
                            beregningEvnevurderingService
                                .hentLøpendeBidragForBehandling(behandling)
                                .tilGrunnlagDto(grunnlagsliste)
                        grunnlagsliste.addAll(grunnlagLøpendeBidrag)
                    }

                    else -> {}
                }
                val beregnFraDato = virkningstidspunkt ?: vedtakmappingFeilet("Virkningstidspunkt må settes for beregning")
                val beregningTilDato = finnBeregnTilDato(virkningstidspunkt!!)
                return BeregnGrunnlag(
                    periode =
                        ÅrMånedsperiode(
                            beregnFraDato,
                            beregningTilDato,
                        ),
                    søknadsbarnReferanse = søknadsbarn.referanse,
                    grunnlagListe = grunnlagsliste.toList(),
                )
            }
        }
    }

    fun Behandling.byggGrunnlagForVedtak(personobjekterFraBeregning: MutableSet<GrunnlagDto> = mutableSetOf()): Set<GrunnlagDto> {
        mapper.run {
            val personobjekter = (tilPersonobjekter() + personobjekterFraBeregning).toSet()
            val bostatus = tilGrunnlagBostatus(personobjekter)
            val personobjekterMedHusstandsmedlemmer =
                (personobjekter + bostatus.husstandsmedlemmer()).toMutableSet()
            val innhentetGrunnlagListe = byggInnhentetGrunnlag(personobjekterMedHusstandsmedlemmer)
            val inntekter = tilGrunnlagInntekt(personobjekter)

            val grunnlagListe = (personobjekter + bostatus + inntekter + innhentetGrunnlagListe).toMutableSet()
            when (tilType()) {
                TypeBehandling.FORSKUDD ->
                    grunnlagListe.addAll(
                        tilGrunnlagSivilstand(
                            personobjekter.bidragsmottaker ?: manglerRolleIGrunnlag(Rolletype.BIDRAGSMOTTAKER, id),
                        ),
                    )

                TypeBehandling.SÆRBIDRAG ->
                    grunnlagListe.addAll(
                        byggGrunnlagUtgiftsposter() + byggGrunnlagUtgiftDirekteBetalt() + byggGrunnlagUtgiftMaksGodkjentBeløp(),
                    )

                else -> {}
            }
            return grunnlagListe.toSet()
        }
    }

    fun Behandling.byggGrunnlagGenereltAvslag(): Set<GrunnlagDto> {
        val grunnlagListe = (byggGrunnlagNotaterDirekteAvslag() + byggGrunnlagSøknad()).toMutableSet()
        when (tilType()) {
            TypeBehandling.FORSKUDD -> grunnlagListe.addAll(byggGrunnlagVirkningsttidspunkt())
            TypeBehandling.SÆRBIDRAG -> {
                grunnlagListe.addAll(byggGrunnlagVirkningsttidspunkt() + byggGrunnlagSærbidragKategori())
                if (validering.run { tilSærbidragAvslagskode() } == Resultatkode.ALLE_UTGIFTER_ER_FORELDET) {
                    grunnlagListe.addAll(byggGrunnlagUtgiftsposter() + byggGrunnlagUtgiftDirekteBetalt())
                }
            }

            else -> {}
        }
        return grunnlagListe
    }

    fun EvnevurderingBeregningResultat.tilGrunnlagDto(personGrunnlagListe: MutableSet<GrunnlagDto>): List<GrunnlagDto> {
        val grunnlagslistePersoner: MutableList<GrunnlagDto> = mutableListOf()

        fun LøpendeBidragssak.tilPersonGrunnlag(): GrunnlagDto {
            val fødselsdato = personService.hentPersonFødselsdato(kravhaver.verdi) ?: fantIkkeFødselsdatoTilSøknadsbarn(-1)
            val nyesteIdent = (personService.hentNyesteIdent(kravhaver.verdi) ?: kravhaver)

            return GrunnlagDto(
                referanse =
                    Grunnlagstype.PERSON_BARN_BIDRAGSPLIKTIG.tilPersonreferanse(
                        fødselsdato.toCompactString(),
                        (kravhaver.verdi + 1).hashCode(),
                    ),
                type = Grunnlagstype.PERSON_BARN_BIDRAGSPLIKTIG,
                innhold =
                    POJONode(
                        Person(
                            ident = nyesteIdent,
                            fødselsdato = fødselsdato,
                        ).valider(),
                    ),
            )
        }

        fun LøpendeBidragssak.opprettPersonGrunnlag(): GrunnlagDto {
            val relatertPersonGrunnlag = tilPersonGrunnlag()
            grunnlagslistePersoner.add(relatertPersonGrunnlag)
            return relatertPersonGrunnlag
        }

        val grunnlag =
            GrunnlagDto(
                referanse = grunnlagsreferanse_løpende_bidrag,
                gjelderReferanse = personGrunnlagListe.bidragspliktig!!.referanse,
                type = Grunnlagstype.LØPENDE_BIDRAG,
                innhold =
                    POJONode(
                        LøpendeBidragGrunnlag(
                            løpendeBidragListe =
                                løpendeBidragsaker.map { løpendeStønad ->
                                    val beregning = beregnetBeløpListe.beregningListe.find { it.personidentBarn == løpendeStønad.kravhaver }
                                    val personObjekt =
                                        personGrunnlagListe.hentPerson(løpendeStønad.kravhaver.verdi)
                                            ?: løpendeStønad.opprettPersonGrunnlag()
                                    LøpendeBidrag(
                                        faktiskBeløp = beregning?.faktiskBeløp ?: BigDecimal.ZERO,
                                        samværsklasse = beregning?.samværsklasse ?: Samværsklasse.SAMVÆRSKLASSE_0,
                                        beregnetBeløp = beregning?.beregnetBeløp ?: BigDecimal.ZERO,
                                        løpendeBeløp = løpendeStønad.løpendeBeløp,
                                        type = løpendeStønad.type,
                                        gjelderBarn = personObjekt.referanse,
                                        saksnummer = Saksnummer(løpendeStønad.sak.verdi),
                                        valutakode = løpendeStønad.valutakode,
                                    )
                                },
                        ),
                    ),
            )
        return grunnlagslistePersoner + mutableListOf(grunnlag)
    }
}
