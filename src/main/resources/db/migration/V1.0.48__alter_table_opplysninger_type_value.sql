-- Table: opplysninger

/*
  Rollback

  update opplysninger set opplysninger_type = 'INNTEKTSOPPLYSNINGER' where opplysninger_type = 'INNTEKT_BEARBEIDET';
  update opplysninger set opplysninger_type = 'BOFORHOLD' where opplysninger_type = 'BOFORHOLD_BEARBEIDET';
 */

update opplysninger
set opplysninger_type = 'INNTEKT_BEARBEIDET'
where opplysninger_type = 'INNTEKTSOPPLYSNINGER';
update opplysninger
set opplysninger_type = 'BOFORHOLD_BEARBEIDET'
where opplysninger_type = 'BOFORHOLD';








