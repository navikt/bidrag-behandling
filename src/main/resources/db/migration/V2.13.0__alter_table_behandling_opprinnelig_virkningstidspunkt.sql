alter table behandling
    alter opprinnelig_vedtakstidspunkt type timestamp[] using array[opprinnelig_vedtakstidspunkt],
    alter opprinnelig_vedtakstidspunkt set not null,
    alter opprinnelig_vedtakstidspunkt set default '{}'
;

update behandling set opprinnelig_vedtakstidspunkt = '{}' where opprinnelig_vedtakstidspunkt = '{null}';


--- Rollback changes

-- alter table behandling
--     alter opprinnelig_vedtakstidspunkt drop not null,
--     alter opprinnelig_vedtakstidspunkt drop default ,
--     alter opprinnelig_vedtakstidspunkt type timestamp using opprinnelig_vedtakstidspunkt[1]::timestamp,
--     alter opprinnelig_vedtakstidspunkt set default null
-- ;



-- Fix migration error
-- update behandling set opprinnelig_vedtakstidspunkt = null where opprinnelig_vedtakstidspunkt = '';

-- alter table behandling add column temp_opprinnelig_vedtakstidspunkt timestamp;
-- update behandling set temp_opprinnelig_vedtakstidspunkt = opprinnelig_vedtakstidspunkt[1]::timestamp;
-- alter table behandling drop column temp_opprinnelig_vedtakstidspunkt;