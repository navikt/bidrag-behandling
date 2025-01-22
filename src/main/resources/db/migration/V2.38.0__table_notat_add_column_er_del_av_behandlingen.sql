alter table notat add column if not exists er_del_av_behandlingen boolean default true;

comment on column notat.er_del_av_behandlingen is 'Angir om begrunnelsen er for visning eller er del av behandlingen og skal lagres i vedtaket';