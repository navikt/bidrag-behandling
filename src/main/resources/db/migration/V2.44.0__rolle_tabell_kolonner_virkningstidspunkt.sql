alter table rolle add column if not exists virkningstidspunkt date;
alter table rolle add column if not exists opprinnelig_virkningstidspunkt date;
alter table rolle add column if not exists årsak text;
alter table rolle add column if not exists avslag text;