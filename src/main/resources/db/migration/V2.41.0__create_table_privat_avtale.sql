CREATE TABLE IF NOT EXISTS privat_avtale
(
    id                   bigint PRIMARY KEY GENERATED BY DEFAULT AS IDENTITY (INCREMENT 1 START 1 MINVALUE 1),
    behandling_id        bigint REFERENCES behandling (id) not null,
    person_id            bigint REFERENCES person (id)     not null,
    avtale_dato          date,
    skal_indeksreguleres boolean                           not null default true
);

CREATE INDEX IF NOT EXISTS idx_privat_avtale_behandling_id ON privat_avtale (behandling_id);
CREATE INDEX IF NOT EXISTS idx_privat_avtale_person_id ON privat_avtale (person_id);

CREATE TABLE IF NOT EXISTS privat_avtale_periode
(
    id    bigint PRIMARY KEY GENERATED BY DEFAULT AS IDENTITY (INCREMENT 1 START 1 MINVALUE 1),
    privat_avtale_id bigint REFERENCES privat_avtale(id) not null,
    fom   date    not null,
    tom   date,
    beløp numeric not null
);

CREATE INDEX IF NOT EXISTS idx_privat_avtale_periode_samvær_id ON privat_avtale_periode (privat_avtale_id);
