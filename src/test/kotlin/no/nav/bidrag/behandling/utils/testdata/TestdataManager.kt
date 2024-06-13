package no.nav.bidrag.behandling.utils.testdata

import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.grunnlag.SkattepliktigeInntekter
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagstype
import no.nav.bidrag.behandling.dto.v2.behandling.getOrMigrate
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.tilJson
import no.nav.bidrag.behandling.transformers.boforhold.tilBoforholdbBarnRequest
import no.nav.bidrag.behandling.transformers.boforhold.tilHusstandsbarn
import no.nav.bidrag.behandling.transformers.boforhold.tilSivilstand
import no.nav.bidrag.boforhold.BoforholdApi
import no.nav.bidrag.domene.enums.person.SivilstandskodePDL
import no.nav.bidrag.domene.enums.rolle.Rolletype
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
    fun oppretteBehandlingINyTransaksjon(inkluderInntekter: Boolean = false): Behandling {
        return oppretteBehandling(inkluderInntekter)
    }

    @Transactional
    fun oppretteBehandling(inkluderInntekter: Boolean = false): Behandling {
        val behandling = no.nav.bidrag.behandling.utils.testdata.oppretteBehandling()
        behandling.virkningstidspunktsbegrunnelseIVedtakOgNotat = "notat virkning med i vedtak"
        behandling.virkningstidspunktbegrunnelseKunINotat = "notat virkning"

        behandling.roller =
            mutableSetOf(
                opprettRolle(behandling, testdataBarn1),
                opprettRolle(behandling, testdataBarn2),
                opprettRolle(behandling, testdataBM),
            )

        oppretteBoforhold(behandling)
        oppretteSivilstand(behandling)

        if (inkluderInntekter) {
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
                            behandling.bidragsmottaker!!.ident!!,
                            behandling.søktFomDato,
                        )
                    },
                innhentet = innhentet,
                aktiv = aktiv,
                rolle = behandling.roller.first { r -> Rolletype.BIDRAGSMOTTAKER == r.rolletype },
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
        val husstandsbarn1 = opprettHusstandsbarn(behandling, testdataBarn1)
        husstandsbarn1.perioder.clear()

        val husstandsbarn2 = opprettHusstandsbarn(behandling, testdataBarn2)
        husstandsbarn2.perioder.clear()

        val grunnlagHusstandsmedlemmer =
            setOf(
                RelatertPersonGrunnlagDto(
                    relatertPersonPersonId = testdataBarn1.ident,
                    fødselsdato = testdataBarn1.fødselsdato,
                    erBarnAvBmBp = true,
                    navn = "Lyrisk Sopp",
                    partPersonId = behandling.bidragsmottaker!!.ident!!,
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
                    partPersonId = behandling.bidragsmottaker!!.ident!!,
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
                rolle = behandling.bidragsmottaker!!,
                type = Grunnlagsdatatype.BOFORHOLD,
                erBearbeidet = false,
            ),
        )

        val boforholdPeriodisert =
            BoforholdApi.beregnBoforholdBarnV2(
                behandling.virkningstidspunktEllerSøktFomDato,
                grunnlagHusstandsmedlemmer.tilBoforholdbBarnRequest(behandling.virkningstidspunktEllerSøktFomDato),
            )

        boforholdPeriodisert.filter { it.relatertPersonPersonId != null }.groupBy { it.relatertPersonPersonId }
            .forEach {
                behandling.grunnlag.add(
                    Grunnlag(
                        aktiv = LocalDateTime.now(),
                        behandling = behandling,
                        innhentet = LocalDateTime.now().minusDays(3),
                        data = commonObjectmapper.writeValueAsString(it.value),
                        rolle = behandling.bidragsmottaker!!,
                        type = Grunnlagsdatatype.BOFORHOLD,
                        gjelder = it.key,
                        erBearbeidet = true,
                    ),
                )
            }

        behandling.husstandsbarn.addAll(boforholdPeriodisert.tilHusstandsbarn(behandling))
    }

    private fun oppretteSivilstand(behandling: Behandling) {
        val sivilstandshistorikk =
            listOf(
                SivilstandGrunnlagDto(
                    bekreftelsesdato = behandling.virkningstidspunktEllerSøktFomDato.minusYears(8),
                    gyldigFom = behandling.virkningstidspunktEllerSøktFomDato.minusYears(8),
                    historisk = false,
                    master = "Freg",
                    personId = behandling.bidragsmottaker!!.ident!!,
                    registrert = behandling.virkningstidspunktEllerSøktFomDato.minusYears(8).atStartOfDay(),
                    type = SivilstandskodePDL.GIFT,
                ),
                SivilstandGrunnlagDto(
                    bekreftelsesdato = behandling.virkningstidspunktEllerSøktFomDato.minusMonths(9),
                    gyldigFom = behandling.virkningstidspunktEllerSøktFomDato.minusMonths(9),
                    historisk = false,
                    master = "Freg",
                    personId = behandling.bidragsmottaker!!.ident!!,
                    registrert = behandling.virkningstidspunktEllerSøktFomDato.minusMonths(9).atStartOfDay(),
                    type = SivilstandskodePDL.SKILT,
                ),
            )

        val periodiseringsrequest =
            SivilstandRequest(
                behandledeSivilstandsopplysninger = emptyList(),
                endreSivilstand = null,
                innhentedeOffentligeOpplysninger = sivilstandshistorikk,
            )

        val periodisertHistorikk =
            SivilstandApi.beregnV2(behandling.virkningstidspunktEllerSøktFomDato, periodiseringsrequest)

        behandling.grunnlag.add(
            Grunnlag(
                aktiv = LocalDateTime.now(),
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
                aktiv = LocalDateTime.now(),
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
