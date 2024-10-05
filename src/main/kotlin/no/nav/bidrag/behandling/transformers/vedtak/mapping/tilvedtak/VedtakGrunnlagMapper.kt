package no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak

import com.fasterxml.jackson.databind.node.POJONode
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.fantIkkeFødselsdatoTilSøknadsbarn
import no.nav.bidrag.behandling.fantIkkeRolleISak
import no.nav.bidrag.behandling.service.BeregningEvnevurderingService
import no.nav.bidrag.behandling.service.PersonService
import no.nav.bidrag.behandling.service.hentNyesteIdent
import no.nav.bidrag.behandling.transformers.beregning.EvnevurderingBeregningResultat
import no.nav.bidrag.behandling.transformers.beregning.ValiderBeregningV2
import no.nav.bidrag.behandling.transformers.grunnlag.finnBeregnTilDato
import no.nav.bidrag.behandling.transformers.grunnlag.manglerRolleIGrunnlag
import no.nav.bidrag.behandling.transformers.grunnlag.tilPersonobjekter
import no.nav.bidrag.behandling.transformers.grunnlag.valider
import no.nav.bidrag.behandling.transformers.tilType
import no.nav.bidrag.behandling.vedtakmappingFeilet
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.sak.Saksnummer
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import no.nav.bidrag.transport.behandling.beregning.felles.BeregnGrunnlag
import no.nav.bidrag.transport.behandling.beregning.felles.BidragBeregningResponsDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.LøpendeBidrag
import no.nav.bidrag.transport.behandling.felles.grunnlag.LøpendeBidragGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.Person
import no.nav.bidrag.transport.behandling.felles.grunnlag.bidragsmottaker
import no.nav.bidrag.transport.behandling.felles.grunnlag.bidragspliktig
import no.nav.bidrag.transport.behandling.felles.grunnlag.hentPerson
import no.nav.bidrag.transport.behandling.felles.grunnlag.tilPersonreferanse
import no.nav.bidrag.transport.felles.toCompactString
import no.nav.bidrag.transport.sak.BidragssakDto
import no.nav.bidrag.transport.sak.RolleDto
import org.springframework.stereotype.Component

@Component
class VedtakGrunnlagMapper(
    val mapper: BehandlingTilGrunnlagMappingV2,
    val validering: ValiderBeregningV2,
    val beregningEvnevurderingService: BeregningEvnevurderingService,
    private val personService: PersonService,
) {
    fun Collection<GrunnlagDto>.husstandsmedlemmer() = filter { it.type == Grunnlagstype.PERSON_HUSSTANDSMEDLEM }

    fun BidragssakDto.hentRolleMedFnr(fnr: String): RolleDto =
        roller.firstOrNull { hentNyesteIdent(it.fødselsnummer?.verdi) == hentNyesteIdent(fnr) }
            ?: fantIkkeRolleISak(saksnummer.verdi, fnr)

    fun Behandling.byggGrunnlagForAvslagUgyldigUtgifter() =
        listOf(
            tilPersonobjekter() + byggGrunnlagUtgiftsposter() +
                byggGrunnlagUtgiftDirekteBetalt() +
                byggGrunnlagUtgiftMaksGodkjentBeløp(),
            byggGrunnlagGenereltAvslag(),
        )

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
                        val grunnlagLøpendeBidrag =
                            beregningEvnevurderingService
                                .hentLøpendeBidragForBehandling(behandling)
                                .tilGrunnlagDto(grunnlagsliste)
                        grunnlagsliste.add(tilGrunnlagUtgift())
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

    fun Behandling.byggGrunnlagGenerelt(): Set<GrunnlagDto> {
        val grunnlagListe = (byggGrunnlagNotater() + byggGrunnlagSøknad()).toMutableSet()
        when (tilType()) {
            TypeBehandling.FORSKUDD -> grunnlagListe.addAll(byggGrunnlagVirkningsttidspunkt())
            TypeBehandling.SÆRBIDRAG ->
                grunnlagListe.addAll(byggGrunnlagVirkningsttidspunkt() + byggGrunnlagSærbidragKategori())

            else -> {}
        }
        return grunnlagListe
    }

    fun Behandling.byggGrunnlagGenereltAvslag(): Set<GrunnlagDto> {
        val grunnlagListe = (byggGrunnlagNotaterDirekteAvslag() + byggGrunnlagSøknad()).toMutableSet()
        when (tilType()) {
            TypeBehandling.FORSKUDD -> grunnlagListe.addAll(byggGrunnlagVirkningsttidspunkt())
            TypeBehandling.SÆRBIDRAG -> {
                grunnlagListe.addAll(byggGrunnlagVirkningsttidspunkt() + byggGrunnlagSærbidragKategori())
                if (validering.behandlingTilSærbidragAvslagskode(this) == Resultatkode.ALLE_UTGIFTER_ER_FORELDET) {
                    grunnlagListe.addAll(byggGrunnlagUtgiftsposter() + byggGrunnlagUtgiftDirekteBetalt())
                }
            }

            else -> {}
        }
        return grunnlagListe
    }

    private fun EvnevurderingBeregningResultat.tilGrunnlagDto(personGrunnlagListe: MutableSet<GrunnlagDto>): List<GrunnlagDto> {
        val grunnlagslistePersoner: MutableList<GrunnlagDto> = mutableListOf()

        fun BidragBeregningResponsDto.BidragBeregning.tilPersonGrunnlag(): GrunnlagDto {
            val fødselsdato = personService.hentPersonFødselsdato(personidentBarn.verdi) ?: fantIkkeFødselsdatoTilSøknadsbarn(-1)
            val nyesteIdent = (personService.hentNyesteIdent(personidentBarn.verdi) ?: personidentBarn)

            return GrunnlagDto(
                referanse =
                    Grunnlagstype.PERSON_BARN_BIDRAGSPLIKTIG.tilPersonreferanse(
                        fødselsdato.toCompactString(),
                        (personidentBarn.verdi + 1).hashCode(),
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

        fun BidragBeregningResponsDto.BidragBeregning.opprettPersonGrunnlag(): GrunnlagDto {
            val relatertPersonGrunnlag = tilPersonGrunnlag()
            grunnlagslistePersoner.add(relatertPersonGrunnlag)
//            personGrunnlagListe.add(relatertPersonGrunnlag)
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
                                beregnetBeløpListe.beregningListe.map { beregning ->
                                    val løpendeBeløp = løpendeBidragsaker.find { it.kravhaver == beregning.personidentBarn }!!.løpendeBeløp
                                    val personObjekt =
                                        personGrunnlagListe.hentPerson(beregning.personidentBarn.verdi) ?: beregning.opprettPersonGrunnlag()
                                    LøpendeBidrag(
                                        faktiskBeløp = beregning.faktiskBeløp,
                                        samværsklasse = beregning.samværsklasse!!,
                                        beregnetBeløp = beregning.beregnetBeløp,
                                        løpendeBeløp = løpendeBeløp,
                                        type = beregning.stønadstype,
                                        gjelderBarn = personObjekt.referanse,
                                        saksnummer = Saksnummer(beregning.saksnummer),
                                    )
                                },
                        ),
                    ),
            )
        return grunnlagslistePersoner + mutableListOf(grunnlag)
    }
}
