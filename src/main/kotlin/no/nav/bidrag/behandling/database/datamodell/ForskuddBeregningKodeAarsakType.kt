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
