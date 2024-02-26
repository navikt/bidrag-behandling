delete from grunnlag;

alter table grunnlag
    add column if not exists rolle_id int not null;