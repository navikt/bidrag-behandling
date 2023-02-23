CREATE TYPE BEHANDLING_TYPE AS ENUM ('FORSKUDD');
CREATE CAST (VARCHAR AS BEHANDLING_TYPE) WITH INOUT AS IMPLICIT;

CREATE TYPE SOKNAD_TYPE AS ENUM
(
    'ENDRING',
    'EGET_TILTAK',
    'SOKNAD',
    'INNKREVET_GRUNNLAG',
    'INDEKSREGULERING',
    'KLAGE_BEGR_SATS',
    'KLAGE',
    'FOLGER_KLAGE',
    'KORRIGERING',
    'KONVERTERING',
    'OPPHOR',
    'PRIVAT_AVTALE',
    'BEGR_REVURD',
    'REVURDERING',
    'KONVERTERT',
    'MANEDLIG_PALOP'
);

CREATE CAST (VARCHAR AS SOKNAD_TYPE) WITH INOUT AS IMPLICIT;

CREATE TABLE IF NOT EXISTS BEHANDLING
(
    ID                  int PRIMARY KEY GENERATED BY DEFAULT AS IDENTITY (INCREMENT 1 START 1 MINVALUE 1),
    BEHANDLING_TYPE     BEHANDLING_TYPE,
    SOKNAD_TYPE         SOKNAD_TYPE,
    DATO_FOM            DATE,
    DATO_TOM            DATE,
    SAKSNUMMER          VARCHAR(7),
    BEHANDLER_ENHET     CHAR(4)
);

CREATE TYPE ROLLE_TYPE AS ENUM
(
    'BIDRAGS_PLIKTIG',
    'BIDRAGS_MOTTAKER',
    'BARN',
    'REELL_MOTTAKER'
);

CREATE CAST (VARCHAR AS ROLLE_TYPE) WITH INOUT AS IMPLICIT;

CREATE TABLE IF NOT EXISTS ROLLE
(
    ID              int PRIMARY KEY GENERATED BY DEFAULT AS IDENTITY (INCREMENT 1 START 1 MINVALUE 1),
    IDENT           VARCHAR(20),
    OPPRETTET_DATO  DATE,
    ROLLE_TYPE      ROLLE_TYPE,
    NAVN            TEXT
);