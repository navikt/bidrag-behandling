package no.nav.bidrag.behandling.database.datamodell

import io.swagger.v3.oas.annotations.media.Schema

@Schema(enumAsRef = true)
enum class ForskuddBeregningKodeAarsakType {
    SF,
    NF,
    OF,
    AF,
    CF,
    DF,
    LF,
    GF,
    HF,
    BF,
    KF,
    QF,
    MF,
    PF,
    EF,
    FF,
}
/*
    SF = "Annet",
    NF = "Endring 3 måneder tilbake",
    OF = "Endring 3 års regelen",
    AF = "Fra barnets fødsel",
    CF = "Fra barnets flyttemåned",
    DF = "Fra kravfremsettelse",
    LF = "Fra måneden etter inntekten økte",
    GF = "Fra oppholdstillatelse",
    HF = "Fra søknadstidspunkt",
    BF = "Fra samlivsbrudd",
    KF = "Fra samme måned som inntekten ble redusert",
    QF = "Revurdering måneden etter",
    MF = "Søknadstidspunkt endring",
    PF = "Tidligere feilaktig avslag",
    EF = "3 måneder tilbake",
    FF = "3 års regelen",
 */
