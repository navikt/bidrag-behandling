package no.nav.bidrag.behandling.dto.v2.gebyr

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.bidrag.behandling.database.datamodell.RolleManueltOverstyrtGebyr
import no.nav.bidrag.behandling.utils.testdata.opprettGyldigBehandlingForBeregningOgVedtak
import no.nav.bidrag.domene.enums.behandling.TypeBehandling
import no.nav.bidrag.domene.enums.beregning.Resultatkode
import org.junit.jupiter.api.Test

class GebyrValideringsfeilTest {
    @Test
    fun `skal feile validering hvis ingen begrunnelse for manuelt overstyrt gebyr`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.bidragspliktig!!.harGebyrsøknad = false
        val bm = behandling.bidragsmottaker!!
        bm.harGebyrsøknad = true
        bm.manueltOverstyrtGebyr = RolleManueltOverstyrtGebyr(true, false, null)
        val resultat = behandling.validerGebyr()
        resultat.shouldHaveSize(1)
        resultat.first().manglerBegrunnelse shouldBe true
    }

    @Test
    fun `skal feile validering hvis gebyr ikke satt ved avslag`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.avslag = Resultatkode.BIDRAGSPLIKTIG_ER_DØD
        behandling.bidragspliktig!!.harGebyrsøknad = false
        val bm = behandling.bidragsmottaker!!
        bm.harGebyrsøknad = true
        bm.manueltOverstyrtGebyr = RolleManueltOverstyrtGebyr(true, null, null)
        val resultat = behandling.validerGebyr()
        resultat.shouldHaveSize(1)
        resultat.first().manglerBegrunnelse shouldBe true
    }

    @Test
    fun `skal ikke feile validering hvis gebyr og begrunnelse er satt ved avslag`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.avslag = Resultatkode.BIDRAGSPLIKTIG_ER_DØD
        behandling.bidragspliktig!!.harGebyrsøknad = false
        val bm = behandling.bidragsmottaker!!
        bm.harGebyrsøknad = true
        bm.manueltOverstyrtGebyr = RolleManueltOverstyrtGebyr(true, true, "Begrunnelse")
        val resultat = behandling.validerGebyr()
        resultat.shouldHaveSize(0)
    }

    @Test
    fun `skal ikke feile validering hvis gebyr og begrunnelse er satt`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.bidragspliktig!!.harGebyrsøknad = false
        val bm = behandling.bidragsmottaker!!
        bm.harGebyrsøknad = true
        bm.manueltOverstyrtGebyr = RolleManueltOverstyrtGebyr(true, true, "Begrunnelse")
        val resultat = behandling.validerGebyr()
        resultat.shouldHaveSize(0)
    }

    @Test
    fun `skal ikke feile validering hvis ikke manuelt overstyrt`() {
        val behandling = opprettGyldigBehandlingForBeregningOgVedtak(true, typeBehandling = TypeBehandling.BIDRAG)
        behandling.bidragspliktig!!.harGebyrsøknad = false
        val bm = behandling.bidragsmottaker!!
        bm.harGebyrsøknad = true
        bm.manueltOverstyrtGebyr = RolleManueltOverstyrtGebyr(false, null, null)
        val resultat = behandling.validerGebyr()
        resultat.shouldHaveSize(0)
    }
}
