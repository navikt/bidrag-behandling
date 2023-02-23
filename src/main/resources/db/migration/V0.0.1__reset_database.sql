drop table if exists BEHANDLING cascade;
drop table if exists ROLLE cascade;
drop type if exists BEHANDLING_TYPE;
drop type if exists SOKNAD_TYPE;
drop type if exists ROLLE_TYPE;
--
GRANT ALL PRIVILEGES ON TABLE public.flyway_schema_history TO cloudsqliamuser;