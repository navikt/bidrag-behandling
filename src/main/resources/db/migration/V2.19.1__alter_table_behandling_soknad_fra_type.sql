alter table behandling
    alter column soknad_fra type text using soknad_fra::text;

alter table behandling
    alter column soknad_fra set default 'BIDRAGSMOTTAKER';

drop type if exists soknad_fra_type cascade ;
drop type if exists avslag_type cascade ;
