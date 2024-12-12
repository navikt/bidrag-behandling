-- Sette fødselsdato for personer med rolle lik fødselsdato i rolletabellen
update person p
set fødselsdato = r.fødselsdato
from rolle r
where p.id = r.person_id
  and p.ident is not null
  and p.fødselsdato is null;