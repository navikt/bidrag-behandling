package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.TestContainerRunner
import no.nav.bidrag.behandling.database.datamodell.OpplysningerType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class OpplysningerServiceTest : TestContainerRunner() {
    @Autowired
    lateinit var opplysningerService: OpplysningerService

    @Test
    fun `hente opplysninger`() {
        val res = opplysningerService.hentSistAktiv(1, OpplysningerType.BOFORHOLD)
        assertFalse(res.isPresent)
    }
}
