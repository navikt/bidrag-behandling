package no.nav.bidrag.behandling.database.repository

import no.nav.bidrag.behandling.database.datamodell.Inntekt
import no.nav.bidrag.behandling.database.datamodell.PrivatAvtale
import no.nav.bidrag.domene.enums.diverse.Kilde
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import java.util.Optional

interface PrivatavtaleRepository : CrudRepository<PrivatAvtale, Long> {
    @Query("select p from privat_avtale p inner join rolle r on r.id = p.rolle.id where p.behandling.id != r.behandling.id")
    fun hentPrivatAvtalerMedFeilReferanse(): List<PrivatAvtale>

    @Query("select p from privat_avtale p where p.rolle is null")
    fun hentPrivatAvtalerMedRolleNull(): List<PrivatAvtale>
}
