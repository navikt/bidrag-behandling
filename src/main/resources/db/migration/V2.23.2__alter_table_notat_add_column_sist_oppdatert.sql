alter table notat
    add column if not exists sist_oppdatert timestamp without time zone DEFAULT now() NOT NULL

