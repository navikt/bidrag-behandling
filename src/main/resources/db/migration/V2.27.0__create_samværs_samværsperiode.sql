CREATE TABLE IF NOT EXISTS samvær(
    id bigint PRIMARY KEY GENERATED BY DEFAULT AS IDENTITY (INCREMENT 1 START 1 MINVALUE 1),
    behandling_id bigint REFERENCES behandling(id) not null,
    rolle_id bigint REFERENCES rolle(id) not null
);

CREATE INDEX IF NOT EXISTS idx_samvær_behandling_id ON samvær(behandling_id);
CREATE INDEX IF NOT EXISTS idx_samvær_rolle_id ON samvær(rolle_id);

CREATE TABLE IF NOT EXISTS samværsperiode(
    id bigint PRIMARY KEY GENERATED BY DEFAULT AS IDENTITY (INCREMENT 1 START 1 MINVALUE 1),
    dato_fom date not null,
    dato_tom date,
    samvær_id bigint REFERENCES samvær(id) not null,
    beregning jsonb
);

CREATE INDEX IF NOT EXISTS idx_samværsperiode_samvær_id ON samværsperiode(samvær_id);
