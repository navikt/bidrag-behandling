package no.nav.bidrag.behandling.utils.testdata

import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.Grunnlag
import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.Underholdskostnad
import no.nav.bidrag.behandling.database.grunnlag.SkattepliktigeInntekter
import no.nav.bidrag.behandling.database.repository.BehandlingRepository
import no.nav.bidrag.behandling.database.repository.InntektRepository
import no.nav.bidrag.behandling.database.repository.PersonRepository
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagsdatatype
import no.nav.bidrag.behandling.dto.v2.behandling.Grunnlagstype
import no.nav.bidrag.behandling.dto.v2.behandling.getOrMigrate
import no.nav.bidrag.behandling.transformers.Jsonoperasjoner.Companion.tilJson
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.transport.behandling.grunnlag.response.AinntektGrunnlagDto
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Component
class TestdataManager(
    private val behandlingRepository: BehandlingRepository,
    private val personRepository: PersonRepository,
    private val entityManager: EntityManager,
    private val inntektRepository: InntektRepository,
) {
    @Transactional
    fun lagreBehandling(behandling: Behandling): Behandling = behandlingRepository.save(behandling)

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    fun lagreBehandlingNewTransaction(behandling: Behandling): Behandling {
        val inntekter = mutableSetOf<Inntekt>()
        behandling.inntekter.forEach { inntekter.add(it) }
        behandling.inntekter = mutableSetOf()

        val underholdskostnader = mutableSetOf<Underholdskostnad>()
        behandling.underholdskostnader.forEach { underholdskostnader.add(it) }
        behandling.underholdskostnader = mutableSetOf()

        behandlingRepository.save(behandling)

        inntekter.forEach { inntektRepository.save(it) }
        behandling.inntekter.addAll(inntekter)

        underholdskostnader.forEach { personRepository.save(it.person) }
        behandling.underholdskostnader.addAll(underholdskostnader)

        return behandlingRepository.save(behandling)
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    fun oppretteBehandlingINyTransaksjon(
        inkluderInntekter: Boolean = false,
        inkludereSivilstand: Boolean = true,
        inkludereBoforhold: Boolean = true,
        inkludereBp: Boolean = false,
        behandlingstype: TypeBehandling = TypeBehandling.FORSKUDD,
        inkludereVoksneIBpsHusstand: Boolean = true,
    ): Behandling =
        oppretteBehandling(
            inkluderInntekter,
            inkludereSivilstand,
            inkludereBoforhold,
            inkludereBp,
            behandlingstype,
            inkludereVoksneIBpsHusstand,
        )

    @Transactional
    fun oppretteBehandling(
        inkludereInntekter: Boolean = false,
        inkludereSivilstand: Boolean = true,
        inkludereBoforhold: Boolean = true,
        inkludereBp: Boolean = false,
        behandlingstype: TypeBehandling = TypeBehandling.FORSKUDD,
        inkludereVoksneIBpsHusstand: Boolean = false,
        setteDatabaseider: Boolean = false,
        inkludereArbeidsforhold: Boolean = false,
    ): Behandling {
        val behandling =
            oppretteTestbehandling(
                inkludereInntekter,
                inkludereSivilstand,
                inkludereBoforhold,
                inkludereBp,
                behandlingstype,
                inkludereVoksneIBpsHusstand,
                setteDatabaseider,
                inkludereArbeidsforhold,
            )

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

    fun hentBehandling(id: Long): Behandling? =
        entityManager
            .createNativeQuery(
                "SELECT * FROM behandling WHERE id = $id",
                Behandling::class.java,
            ).resultList
            .firstOrNull() as Behandling?
}
