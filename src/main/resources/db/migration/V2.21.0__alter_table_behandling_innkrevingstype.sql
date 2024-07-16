alter table behandling add column if not exists innkrevingstype text;
update behandling set innkrevingstype = 'MED_INNKREVING' where stonadstype = 'FORSKUDD';