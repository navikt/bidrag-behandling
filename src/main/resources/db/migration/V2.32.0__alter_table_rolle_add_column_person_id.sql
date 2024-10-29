/*
    Legger til kolonne person_id i rolletabellen og oppretter fremmednøkkel bare dersom person_id-kolonnen ikke
    eksisterer.
*/

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1
	    FROM information_schema.columns
	    WHERE table_schema='public' AND table_name='rolle' AND column_name='person_id') THEN
            alter table rolle
                add column if not exists person_id bigint,
                add constraint fk_person foreign key (person_id)
                    references public.person (id) match simple
                    on update no action
                    on delete no action
                    not valid;
    END IF;
END $$;

