CREATE TABLE IF NOT EXISTS notat
(
    id bigint PRIMARY KEY GENERATED BY DEFAULT AS IDENTITY (INCREMENT 1 START 1 MINVALUE 1),
    behandling_id bigint REFERENCES behandling(id),
    rolle_id bigint REFERENCES rolle(id),
    type text,
    innhold text not null
);

CREATE INDEX IF NOT EXISTS idx_notat_behandling_id ON notat(behandling_id);
CREATE INDEX IF NOT EXISTS idx_notat_rolle_id ON notat(rolle_id);
