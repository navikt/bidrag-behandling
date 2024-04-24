alter table grunnlag
    add column if not exists gjelder character varying(11) default null;