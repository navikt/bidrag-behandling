---- Hent info
--- Behandling
select b.id,

       b.vedtaksid,
       b.vedtak_fattet_av,
       b.vedtakstidspunkt,
       b.vedtakstype,
       b.stonadstype,
       b.grunnlag_sist_innhentet,
       b.deleted,
       b.slettet_tidspunkt,
       r.ident,
       r.rolletype,
       r.foedselsdato,
       r.deleted
from behandling as b
         inner join public.rolle r on b.id = r.behandling_id
where b.id = :behandling_id;
--- Husstandsbarn
select b.id,
       b.grunnlag_sist_innhentet,
       bih.ident,
       bih.kilde,
       bih.foedselsdato,
       r.ident,
       r.rolletype
from behandling as b
         inner join public.barn_i_husstand bih on b.id = bih.behandling_id
         inner join public.rolle r on b.id = r.behandling_id
where b.id = :behandling_id;
--- Inntekter
select b.id,
       b.grunnlag_sist_innhentet,
       i.ta_med,
       i.inntektsrapportering,
       i.dato_fom,
       i.dato_tom,
       i.opprinnelig_fom,
       i.opprinnelig_tom,
       i.gjelder_barn,
       i.kilde
from behandling as b
         inner join public.inntekt as i on b.id = i.behandling_id
where b.id = :behandling_id;

--- Innteksposter
select ip.inntektstype,
       ip.belop,
       ip.kode,
       i.ta_med,
       i.inntektsrapportering,
       i.dato_fom,
       i.dato_tom,
       i.opprinnelig_fom,
       i.opprinnelig_tom,
       i.gjelder_barn,
       i.kilde
from inntektspost as ip
         inner join public.inntekt i on i.id = ip.inntekt_id
where i.behandling_id = :behandling_id;
--- Reset grunnlag
delete
from husstandsbarnperiode bip using husstandsbarn bh
where bh.id = bip.husstandsbarn_id
  and bh.behandling_id = :behandling_id;
delete
from husstandsbarn
where behandling_id = :behandling_id;
-- delete from barn_i_husstand_periode bip using barn_i_husstand bh where bh.id = bip.barn_i_husstand_id and bh.behandling_id = :behandling_id;
-- delete from barn_i_husstand where behandling_id = :behandling_id;
delete
from sivilstand
where behandling_id = :behandling_id;
delete
from inntektspost ip using inntekt i
where i.id = ip.inntekt_id
  and i.behandling_id = :behandling_id;
delete
from inntekt
where behandling_id = :behandling_id;
delete
from grunnlag
where behandling_id = :behandling_id;
update behandling
set grunnlag_sist_innhentet = null
where id = :behandling_id;
delete from grunnlag where type = 'BOFORHOLD' and behandling_id = :behandling_id;
update behandling
set vedtaksid        = null,
    vedtakstidspunkt = null,
    vedtak_fattet_av = null
where id = :behandling_id;

-- Reset behandling sak
delete
from barn_i_husstand_periode bip using barn_i_husstand bh inner join behandling b on b.id = bh.behandling_id and b.saksnummer = :saksnummer
where bh.id = bip.barn_i_husstand_id
  and bh.behandling_id = b.id;
delete
from barn_i_husstand using behandling b
where behandling_id = b.id
  and b.saksnummer = :saksnummer;
delete
from sivilstand using behandling b
where behandling_id = b.id
  and b.saksnummer = :saksnummer;
delete
from inntektspost ip using inntekt i inner join behandling b on b.id = i.behandling_id and b.saksnummer = :saksnummer
where i.id = ip.inntekt_id
  and i.behandling_id = b.id;
delete
from inntekt using behandling b
where behandling_id = b.id
  and b.saksnummer = :saksnummer;
delete
from grunnlag using behandling b
where behandling_id = b.id
  and b.saksnummer = :saksnummer;
update behandling
set grunnlag_sist_innhentet = null
where saksnummer = :saksnummer;

delete
from inntektspost ip using inntekt i
where i.id = ip.inntekt_id
  and i.behandling_id = :behandling_id
  and i.id = :inntekt_id;
delete
from inntekt
where behandling_id = :behandling_id
  and id = :inntekt_id;
---- Resetter vedtak
update behandling
set vedtaksid = null
where id = :behandling_id

select *
from grunnlag
where data::text like '%barnetillegg%'
  and er_bearbeidet = false;
select *
from behandling b
         inner join public.barn_i_husstand bih on b.id = bih.behandling_id
where b.id = :behandling_id;


delete
from inntektspost ip using inntekt i
where i.id = ip.inntekt_id
  and i.behandling_id = :behandling_id
  and i.inntektsrapportering = 'BARNETILLEGG';
delete
from inntekt
where behandling_id = :behandling_id
  and inntektsrapportering = 'BARNETILLEGG';



select b.id, b.deleted, r.id, r.ident, r.rolletype, r.deleted
from behandling b
         inner join public.rolle r on b.id = r.behandling_id
where saksnummer = '2401387';

--- Undelete
update behandling
set deleted = false
where id = :behandlingId;
update rolle
set deleted = false
where behandling_id = :behandlingId;
update behandling
set deleted = true
where id = :behandlingId_new;
update inntekt
set dato_fom = null,
    dato_tom = null
where ta_med = false;


select *
from inntekt i
where i.behandling_id = :behandling_id;
select *
from inntektspost ip
         inner join inntekt i on i.id = ip.inntekt_id
where i.behandling_id = :behandling_id;
select data, rolletype, type, er_bearbeidet, ident, r.id
from grunnlag g
         inner join public.rolle r on r.id = g.rolle_id
where g.behandling_id = :behandling_id
order by er_bearbeidet;
select *
from rolle
         inner join public.behandling b on b.id = rolle.behandling_id
where b.id = :behandling_id;


select *
from inntektspost
         inner join public.inntekt i on i.id = inntektspost.inntekt_id
where i.inntektsrapportering = 'BARNETILLEGG';


select *
from husstandsbarnperiode
         inner join husstandsbarn h on h.id = husstandsbarnperiode.husstandsbarn_id
where h.behandling_id = '1161';

delete
from husstandsbarnperiode bip using husstandsbarn bh
where bh.id = bip.husstandsbarn_id
  and bh.kilde = 'MANUELL'
  and bh.behandling_id = :behandling_id;
delete
from husstandsbarn
where behandling_id = :behandling_id
  and kilde = 'MANUELL';



--- Ryddejobb
-- select *
-- from husstandsbarn
--     inner join behandling b on b.id = husstandsbarn.behandling_id
--   where kilde = 'MANUELL' and b.vedtaksid is not null;
--
-- delete
-- from husstandsbarnperiode bip using husstandsbarn bh, behandling b
-- where bh.id = bip.husstandsbarn_id
--   and bh.kilde = 'MANUELL'
--   and b.vedtaksid is not null;
-- delete
-- from husstandsbarn
-- using behandling b
--   where kilde = 'MANUELL' and b.vedtaksid is not null;

update behandling set virkningsdato = dato_fom where virkningsdato is null;
update behandling set aarsak = 'FRA_SØKNADSTIDSPUNKT' where behandling.aarsak is null;

select *
from husstandsbarnperiode bip
         inner join husstandsbarn bh on bh.id = bip.husstandsbarn_id and bh.behandling_id = :behandling_id;


----- Statistikk -------

select *
from behandling
where vedtaksid is not null and vedtak_fattet_av != 'J141208'
order by vedtakstidspunkt desc;
select *
from behandling
where opprettet_av = 'O149215' order by opprettet_tidspunkt desc;

select b.id, i.ta_med, b.saksnummer, b.vedtak_fattet_av, i.kilde, i.inntektsrapportering, i.belop
from inntekt i
         inner join public.behandling b on b.id = i.behandling_id
where b.vedtaksid is not null;

select i.inntektsrapportering, count(*) as count, i.kilde
from inntekt i
         inner join behandling b on b.id = i.behandling_id and b.vedtaksid is not null and i.ta_med = true
group by i.inntektsrapportering, i.kilde order by count desc;


WITH total AS (SELECT count(*) as count
               from inntekt i
                        inner join behandling b
                                   on b.id = i.behandling_id and b.vedtaksid is not null and i.ta_med = true),
     manuelle AS (SELECT count(*) as count
                  FROM inntekt i
                           inner join behandling b on b.id = i.behandling_id and b.vedtaksid is not null
                      and i.ta_med = true and i.kilde == 'MANUELL'),
     offentlige AS (SELECT count(*) as count
                    FROM inntekt i
                             inner join behandling b on b.id = i.behandling_id and b.vedtaksid is not null
                        and i.ta_med = true and i.kilde = 'OFFENTLIG')
SELECT manuelle.count                                       as manuelle,
       offentlige.count                                     as offentlige,
       total.count                                          as totalt,
       floor(manuelle.count / (total.count)::float * 100)   as andel_manuelle,
       floor(offentlige.count / (total.count)::float * 100) as andel_offentlige
FROM inntekt,
     total,
     manuelle,
     offentlige
GROUP BY total.count, manuelle.count, offentlige.count;


alter table behandling
    alter opprinnelig_vedtakstidspunkt drop not null,
    alter opprinnelig_vedtakstidspunkt drop default ,
    alter opprinnelig_vedtakstidspunkt type timestamp using opprinnelig_vedtakstidspunkt[1]::timestamp,
    alter opprinnelig_vedtakstidspunkt set default null
;