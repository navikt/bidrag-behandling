alter table privat_avtale add column if not exists rolle_id bigint REFERENCES rolle(id);
alter table privat_avtale alter column person_id drop not null;

UPDATE privat_avtale pa
SET rolle_id = rolle.rolle_id
FROM (
         SELECT person_id, min(id) AS rolle_id
         FROM rolle
         GROUP BY person_id
     ) AS rolle
WHERE pa.person_id = rolle.person_id;