CREATE EXTENSION IF NOT EXISTS hstore;
alter table behandling
    add column if not exists metadata hstore;

CREATE INDEX behandling_metadata_idx ON behandling USING BTREE (metadata);
