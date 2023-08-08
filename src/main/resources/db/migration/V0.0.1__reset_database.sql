drop table if exists BEHANDLING cascade;
drop table if exists ROLLE cascade;
drop type if exists FORSKUDD_BEREGNING_KODE_AARSAK_TYPE cascade;
drop type if exists BEHANDLING_TYPE cascade;
drop type if exists SOKNAD_TYPE cascade;
drop type if exists ROLLE_TYPE cascade;
--
GRANT ALL PRIVILEGES ON TABLE public.flyway_schema_history TO cloudsqliamuser;