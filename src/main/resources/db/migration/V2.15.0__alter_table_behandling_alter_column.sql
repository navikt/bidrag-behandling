alter table behandling drop column if exists grunnlagspakkeid;
alter table behandling add column if not exists grunnlagsinnhenting_feilet jsonb;
