package no.nav.bidrag.behandling.utils.testdata

import jakarta.transaction.Transactional
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagstype
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.tilJson
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.transport.behandling.grunnlag.response.AinntektGrunnlagDto
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Component
class TestdataManager(private val behandlingRepository: BehandlingRepository) {
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

    @Transactional
    fun <T> oppretteOgLagreGrunnlag(
        behandling: Behandling,
        grunnlagstype: Grunnlagstype = Grunnlagstype(Grunnlagsdatatype.SKATTEGRUNNLAG, false),
        innhentet: LocalDateTime,
        aktiv: LocalDateTime? = null,
        grunnlagsdata: T? = null,
    ) {
        behandling.grunnlag.add(
            Grunnlag(
                behandling,
                grunnlagstype.type,
                grunnlagstype.erBearbeidet,
                data =
                    if (grunnlagsdata != null) {
                        tilJson(grunnlagsdata)
                    } else {
                        oppretteGrunnlagInntektsdata(
                            grunnlagstype.type,
                            behandling.bidragsmottaker!!.ident!!,
                            behandling.søktFomDato,
                        )
                    },
                innhentet = innhentet,
                aktiv = aktiv,
                rolle = behandling.roller.first { r -> Rolletype.BIDRAGSMOTTAKER == r.rolletype },
            ),
        )
    }

    fun oppretteGrunnlagInntektsdata(
        grunnlagsdatatype: Grunnlagsdatatype,
        gjelderIdent: String,
        søktFomDato: LocalDate,
    ) = when (grunnlagsdatatype) {
        Grunnlagsdatatype.AINNTEKT ->
            tilJson(
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
            )

        else -> ""
    }
}
