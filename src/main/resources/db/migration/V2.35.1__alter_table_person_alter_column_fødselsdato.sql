-- Gjøre fødselsdatokolonnen påkrevd
alter table person alter column fødselsdato set not null;
