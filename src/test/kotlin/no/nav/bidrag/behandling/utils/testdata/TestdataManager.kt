package no.nav.bidrag.behandling.utils.testdata

import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.grunnlag.SkattepliktigeInntekter
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagstype
import no.nav.bidrag.behandling.dto.v2.behandling.getOrMigrate
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.tilJson
import no.nav.bidrag.behandling.transformers.TypeBehandling
import no.nav.bidrag.behandling.transformers.boforhold.tilBoforholdBarnRequest
import no.nav.bidrag.behandling.transformers.boforhold.tilHusstandsmedlem
import no.nav.bidrag.behandling.transformers.boforhold.tilSivilstand
import no.nav.bidrag.boforhold.BoforholdApi
import no.nav.bidrag.domene.enums.person.SivilstandskodePDL
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.Engangsbeløptype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.sivilstand.SivilstandApi
import no.nav.bidrag.sivilstand.dto.SivilstandRequest
import no.nav.bidrag.transport.behandling.grunnlag.response.AinntektGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.BorISammeHusstandDto
import no.nav.bidrag.transport.behandling.grunnlag.response.RelatertPersonGrunnlagDto
import no.nav.bidrag.transport.behandling.grunnlag.response.SivilstandGrunnlagDto
import no.nav.bidrag.transport.felles.commonObjectmapper
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Component
class TestdataManager(
    private val behandlingRepository: BehandlingRepository,
    private val entityManager: EntityManager,
) {
    @Transactional
    fun lagreBehandling(behandling: Behandling): Behandling {
        return behandlingRepository.save(behandling)
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    fun lagreBehandlingNewTransaction(behandling: Behandling): Behandling {
        return behandlingRepository.save(behandling)
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    fun oppretteBehandlingINyTransaksjon(
        inkluderInntekter: Boolean = false,
        inkludereSivilstand: Boolean = true,
        inkludereBoforhold: Boolean = true,
        inkludereBp: Boolean = false,
        behandlingstype: TypeBehandling = TypeBehandling.FORSKUDD,
    ): Behandling {
        return oppretteBehandling(inkluderInntekter, inkludereSivilstand, inkludereBoforhold, inkludereBp, behandlingstype)
    }

    @Transactional
    fun oppretteBehandling(
        inkludereInntekter: Boolean = false,
        inkludereSivilstand: Boolean = true,
        inkludereBoforhold: Boolean = true,
        inkludereBp: Boolean = false,
        behandlingstype: TypeBehandling = TypeBehandling.FORSKUDD,
    ): Behandling {
        val behandling = no.nav.bidrag.behandling.utils.testdata.oppretteBehandling()
        when (behandlingstype) {
            TypeBehandling.FORSKUDD -> {
                behandling.stonadstype = Stønadstype.FORSKUDD
            }

            TypeBehandling.SÆRLIGE_UTGIFTER -> {
                behandling.engangsbeloptype = Engangsbeløptype.SÆRTILSKUDD
                behandling.stonadstype = null
            }

            else -> throw IllegalStateException("Behandlingstype $behandlingstype er foreløpig ikke støttet")
        }

        behandling.virkningstidspunktsbegrunnelseIVedtakOgNotat = "notat virkning med i vedtak"
        behandling.virkningstidspunktbegrunnelseKunINotat = "notat virkning"

        behandling.roller =
            mutableSetOf(
                opprettRolle(behandling, testdataBarn1),
                opprettRolle(behandling, testdataBarn2),
                opprettRolle(behandling, testdataBM),
            )

        if (inkludereBp) {
            behandling.roller.add(opprettRolle(behandling, testdataBP))
        }

        if (inkludereBoforhold) {
            oppretteBoforhold(behandling)
        }

        if (inkludereSivilstand) {
            oppretteSivilstand(behandling)
        }

        if (inkludereInntekter) {
            behandling.inntekter = opprettInntekter(behandling, testdataBM)
            behandling.inntekter.forEach {
                it.inntektsposter = opprettInntektsposter(it)
            }
        }

        return behandlingRepository.save(behandling)
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    fun <T> oppretteOgLagreGrunnlagINyTransaksjon(
        behandling: Behandling,
        grunnlagstype: Grunnlagstype =
            Grunnlagstype(
                Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER,
                false,
            ),
        innhentet: LocalDateTime = LocalDateTime.now(),
        aktiv: LocalDateTime? = null,
        grunnlagsdata: T? = null,
    ): Behandling {
        oppretteOgLagreGrunnlag(behandling, grunnlagstype, innhentet, aktiv, grunnlagsdata)
        return behandlingRepository.save(behandling)
    }

    @Transactional
    fun <T> oppretteOgLagreGrunnlag(
        behandling: Behandling,
        grunnlagstype: Grunnlagstype =
            Grunnlagstype(
                Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER,
                false,
            ),
        innhentet: LocalDateTime = LocalDateTime.now(),
        aktiv: LocalDateTime? = null,
        grunnlagsdata: T? = null,
        gjelderIdent: String? = null,
        rolle: Rolle? = behandling.roller.first { r -> Rolletype.BIDRAGSMOTTAKER == r.rolletype },
    ) {
        behandling.grunnlag.add(
            Grunnlag(
                behandling,
                grunnlagstype.type.getOrMigrate(),
                grunnlagstype.erBearbeidet,
                data =
                    if (grunnlagsdata != null) {
                        tilJson(grunnlagsdata)
                    } else {
                        oppretteGrunnlagInntektsdata(
                            grunnlagstype.type.getOrMigrate(),
                            rolle!!.ident!!,
                            behandling.søktFomDato,
                        )
                    },
                innhentet = innhentet,
                aktiv = aktiv,
                rolle = rolle!!,
                gjelder = gjelderIdent,
            ),
        )
    }

    fun oppretteGrunnlagInntektsdata(
        grunnlagsdatatype: Grunnlagsdatatype,
        gjelderIdent: String,
        søktFomDato: LocalDate,
    ) = when (grunnlagsdatatype) {
        Grunnlagsdatatype.SKATTEPLIKTIGE_INNTEKTER ->
            tilJson(
                SkattepliktigeInntekter(
                    listOf(
                        AinntektGrunnlagDto(
                            personId = gjelderIdent,
                            periodeFra = søktFomDato.withDayOfMonth(1),
                            periodeTil = søktFomDato.plusMonths(1).withDayOfMonth(1),
                            ainntektspostListe =
                                listOf(
                                    tilAinntektspostDto(
                                        beløp = BigDecimal(70000),
                                        fomDato = søktFomDato,
                                        tilDato = søktFomDato.plusMonths(1).withDayOfMonth(1),
                                    ),
                                ),
                        ),
                    ),
                ),
            )

        else -> ""
    }

    private fun oppretteBoforhold(behandling: Behandling) {
        val husstandsmedlem1 = oppretteHusstandsmedlem(behandling, testdataBarn1)
        husstandsmedlem1.perioder.clear()

        val husstandsmedlem2 = oppretteHusstandsmedlem(behandling, testdataBarn2)
        husstandsmedlem2.perioder.clear()

        val grunnlagHusstandsmedlemmer =
            setOf(
                RelatertPersonGrunnlagDto(
                    relatertPersonPersonId = testdataBarn1.ident,
                    fødselsdato = testdataBarn1.fødselsdato,
                    erBarnAvBmBp = true,
                    navn = "Lyrisk Sopp",
                    partPersonId = behandling.rolleGrunnlagSkalHentesFor!!.ident,
                    borISammeHusstandDtoListe =
                        listOf(
                            BorISammeHusstandDto(
                                periodeFra = LocalDate.parse("2023-01-01"),
                                periodeTil = LocalDate.parse("2023-05-31"),
                            ),
                        ),
                ),
                RelatertPersonGrunnlagDto(
                    relatertPersonPersonId = testdataBarn2.ident,
                    fødselsdato = testdataBarn2.fødselsdato,
                    erBarnAvBmBp = true,
                    navn = "Lyrisk Sopp",
                    partPersonId = behandling.rolleGrunnlagSkalHentesFor!!.ident,
                    borISammeHusstandDtoListe =
                        listOf(
                            BorISammeHusstandDto(
                                periodeFra = LocalDate.parse("2023-01-01"),
                                periodeTil = LocalDate.parse("2023-05-31"),
                            ),
                        ),
                ),
            )

        behandling.grunnlag.add(
            Grunnlag(
                aktiv = LocalDateTime.now(),
                behandling = behandling,
                innhentet = LocalDateTime.now().minusDays(3),
                data = commonObjectmapper.writeValueAsString(grunnlagHusstandsmedlemmer),
                rolle = behandling.rolleGrunnlagSkalHentesFor!!,
                type = Grunnlagsdatatype.BOFORHOLD,
                erBearbeidet = false,
            ),
        )

        val boforholdPeriodisert =
            BoforholdApi.beregnBoforholdBarnV2(
                behandling.virkningstidspunktEllerSøktFomDato,
                grunnlagHusstandsmedlemmer.tilBoforholdBarnRequest(behandling),
            )

        boforholdPeriodisert.filter { it.relatertPersonPersonId != null }.groupBy { it.relatertPersonPersonId }
            .forEach {
                behandling.grunnlag.add(
                    Grunnlag(
                        aktiv = LocalDateTime.now(),
                        behandling = behandling,
                        innhentet = LocalDateTime.now().minusDays(3),
                        data = commonObjectmapper.writeValueAsString(it.value),
                        rolle = behandling.rolleGrunnlagSkalHentesFor!!,
                        type = Grunnlagsdatatype.BOFORHOLD,
                        gjelder = it.key,
                        erBearbeidet = true,
                    ),
                )
            }

        behandling.husstandsmedlem.addAll(boforholdPeriodisert.tilHusstandsmedlem(behandling))
    }

    private fun oppretteSivilstand(behandling: Behandling) {
        val sivilstandshistorikk =
            listOf(
                SivilstandGrunnlagDto(
                    bekreftelsesdato = behandling.virkningstidspunktEllerSøktFomDato.minusYears(8),
                    gyldigFom = behandling.virkningstidspunktEllerSøktFomDato.minusYears(8),
                    historisk = true,
                    master = "Freg",
                    personId = behandling.bidragsmottaker!!.ident!!,
                    registrert = behandling.virkningstidspunktEllerSøktFomDato.minusYears(8).atStartOfDay(),
                    type = SivilstandskodePDL.UGIFT,
                ),
                SivilstandGrunnlagDto(
                    bekreftelsesdato = behandling.virkningstidspunktEllerSøktFomDato.minusYears(5),
                    gyldigFom = behandling.virkningstidspunktEllerSøktFomDato.minusYears(5),
                    historisk = true,
                    master = "Freg",
                    personId = behandling.bidragsmottaker!!.ident!!,
                    registrert = behandling.virkningstidspunktEllerSøktFomDato.minusYears(5).atStartOfDay(),
                    type = SivilstandskodePDL.GIFT,
                ),
                SivilstandGrunnlagDto(
                    bekreftelsesdato = behandling.virkningstidspunktEllerSøktFomDato.plusMonths(3),
                    gyldigFom = behandling.virkningstidspunktEllerSøktFomDato.plusMonths(3),
                    historisk = false,
                    master = "Freg",
                    personId = behandling.bidragsmottaker!!.ident!!,
                    registrert = behandling.virkningstidspunktEllerSøktFomDato.plusMonths(9).atStartOfDay(),
                    type = SivilstandskodePDL.SKILT,
                ),
            )

        val periodiseringsrequest =
            SivilstandRequest(
                behandledeSivilstandsopplysninger = emptyList(),
                endreSivilstand = null,
                innhentedeOffentligeOpplysninger = sivilstandshistorikk,
                fødselsdatoBM = behandling.bidragsmottaker!!.foedselsdato,
            )

        val periodisertHistorikk =
            SivilstandApi.beregnV2(behandling.virkningstidspunktEllerSøktFomDato, periodiseringsrequest)

        behandling.grunnlag.add(
            Grunnlag(
                aktiv = LocalDateTime.now().minusDays(5),
                behandling = behandling,
                data = commonObjectmapper.writeValueAsString(sivilstandshistorikk),
                erBearbeidet = false,
                innhentet = LocalDateTime.now().minusDays(5),
                rolle = behandling.bidragsmottaker!!,
                type = Grunnlagsdatatype.SIVILSTAND,
            ),
        )

        behandling.grunnlag.add(
            Grunnlag(
                aktiv = LocalDateTime.now().minusDays(5),
                behandling = behandling,
                data = commonObjectmapper.writeValueAsString(periodisertHistorikk),
                erBearbeidet = true,
                innhentet = LocalDateTime.now().minusDays(5),
                rolle = behandling.bidragsmottaker!!,
                type = Grunnlagsdatatype.SIVILSTAND,
            ),
        )

        behandling.sivilstand.addAll(periodisertHistorikk.toSet().tilSivilstand(behandling))
    }

    fun hentBehandling(id: Long): Behandling? {
        return entityManager.createNativeQuery(
            "SELECT * FROM behandling WHERE id = $id",
            Behandling::class.java,
        )
            .resultList
            .firstOrNull() as Behandling?
    }
}
