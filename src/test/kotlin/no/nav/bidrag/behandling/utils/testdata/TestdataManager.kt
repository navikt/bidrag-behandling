package no.nav.bidrag.behandling.utils.testdata

import jakarta.transaction.Transactional
import no.nav.bidrag.behandling.behandlingNotFoundException
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Grunnlagsdatatype
import no.nav.bidrag.behandling.database.datamodell.getOrMigrate
import no.nav.bidrag.behandling.database.grunnlag.GrunnlagInntekt
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.objektTilJson
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.transport.behandling.grunnlag.response.AinntektGrunnlagDto
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Component
class TestdataManager(private val behandlingRepository: BehandlingRepository) {
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    fun opprettBehandling(inkluderInntekter: Boolean = true): Behandling {
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

    fun <T> oppretteOgLagreGrunnlag(
        behandlingsid: Long,
        grunnlagsdatatype: Grunnlagsdatatype = Grunnlagsdatatype.INNTEKT,
        innhentet: LocalDateTime,
        aktiv: LocalDateTime? = null,
        grunnlagsdata: T? = null,
    ) {
        behandlingRepository.findBehandlingById(behandlingsid)
            .orElseThrow { behandlingNotFoundException(behandlingsid) }.let {
                it.grunnlag.add(
                    Grunnlag(
                        it,
                        grunnlagsdatatype.getOrMigrate(),
                        data =
                            if (grunnlagsdata != null) {
                                objektTilJson(grunnlagsdata)
                            } else {
                                oppretteGrunnlagInntektsdata(
                                    grunnlagsdatatype,
                                    it.getBidragsmottaker()!!.ident!!,
                                    it.søktFomDato,
                                )
                            },
                        innhentet = innhentet,
                        aktiv = aktiv ?: LocalDateTime.now(),
                    ),
                )
            }
    }

    private fun oppretteGrunnlagInntektsdata(
        grunnlagsdatatype: Grunnlagsdatatype,
        gjelderIdent: String,
        søktFomDato: LocalDate,
    ) = when (grunnlagsdatatype) {
        Grunnlagsdatatype.INNTEKT ->
            objektTilJson(
                GrunnlagInntekt(
                    ainntekt =
                        listOf(
                            AinntektGrunnlagDto(
                                personId = gjelderIdent,
                                periodeFra = søktFomDato,
                                periodeTil = søktFomDato.plusMonths(1),
                                ainntektspostListe =
                                    listOf(
                                        tilAinntektspostDto(
                                            beløp = BigDecimal(70000),
                                            fomDato = søktFomDato,
                                            tilDato = søktFomDato.plusMonths(1),
                                        ),
                                    ),
                            ),
                        ),
                ),
            )

        else -> ""
    }
}
