alter table underholdskostnad add column if not exists rolle_id bigint REFERENCES rolle(id);
alter table underholdskostnad alter column person_id drop not null;

UPDATE underholdskostnad underhold
SET rolle_id = rolle.rolle_id
FROM (
         SELECT person_id, min(id) AS rolle_id
         FROM rolle
         GROUP BY person_id
     ) AS rolle
WHERE underhold.person_id = rolle.person_id;