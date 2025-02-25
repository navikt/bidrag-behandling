import java.time.LocalDate

data class Testperson(
    val ident: String,
    val fornavn: String,
    val alder: Int,
    val fødselsdato: LocalDate = LocalDate.now().minusYears(alder.toLong()),
) {
    companion object {
        val testpersonGråtass = Testperson("12345678910", "Gråtass", 40)
        val testpersonStreng = Testperson("11111122222", "Streng", 38)
        val testpersonSirup = Testperson("33333344444", "Sirup", 35)
        val testpersonBarn16 = Testperson("77777700000", "Grus", 16)
        val testpersonBarn10 = Testperson("33333355555", "Småstein", 10)
        val testpersonIkkeFunnet = Testperson("00000001231", "Utenfor", 29)
        val testpersonHarDiskresjon = Testperson("23451644512", "Diskos", 29)
        val testpersonHarMotpartMedDiskresjon = Testperson("56472134561", "Tordivel", 44)
        val testpersonHarBarnMedDiskresjon = Testperson("32456849111", "Kaktus", 48)
        val testpersonErDød = Testperson("77765415234", "Steindød", 35)
        val testpersonHarDødtBarn = Testperson("05784456310", "Albueskjell", 53)
        val testpersonDødMotpart = Testperson("445132456487", "Bunkers", 41)
        val testpersonServerfeil = Testperson("12000001231", "Feil", 78)
    }
}
