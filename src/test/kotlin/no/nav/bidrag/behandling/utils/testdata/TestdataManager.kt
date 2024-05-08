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
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.transport.behandling.grunnlag.response.AinntektGrunnlagDto
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
    fun opprettBehandlingNewTransacion(inkluderInntekter: Boolean = false): Behandling {
        return opprettBehandling(inkluderInntekter)
    }

    @Transactional
    fun opprettBehandling(inkluderInntekter: Boolean = false): Behandling {
        val behandling = oppretteBehandling()
        behandling.virkningstidspunktsbegrunnelseIVedtakOgNotat = "notat virkning med i vedtak"
        behandling.virkningstidspunktbegrunnelseKunINotat = "notat virkning"
        behandling.husstandsbarn =
            mutableSetOf(
                opprettHusstandsbarn(behandling, testdataBarn1),
                opprettHusstandsbarn(behandling, testdataBarn2),
            )
        behandling.roller =
            mutableSetOf(
                opprettRolle(behandling, testdataBarn1),
                opprettRolle(behandling, testdataBarn2),
                opprettRolle(behandling, testdataBM),
            )
        behandling.sivilstand =
            mutableSetOf(
                opprettSivilstand(
                    behandling,
                    LocalDate.parse("2023-01-01"),
                    LocalDate.parse("2023-05-31"),
                    Sivilstandskode.BOR_ALENE_MED_BARN,
                ),
                opprettSivilstand(
                    behandling,
                    LocalDate.parse("2023-06-01"),
                    null,
                    Sivilstandskode.BOR_ALENE_MED_BARN,
                ),
            )

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

    fun hentBehandling(id: Long): Behandling? {
        return entityManager.createNativeQuery(
            "SELECT * FROM behandling WHERE id = $id",
            Behandling::class.java,
        )
            .resultList
            .firstOrNull() as Behandling?
    }
}
