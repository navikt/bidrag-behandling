-- TABLE: GRUNNLAG

/* ##### RULLE TILBAKE #####

   UPDATE grunnlag
       set data = data::jsonb - 'utvidet_barnetrygd' || jsonb_build_object('ubstListe', data->'utvidet_barnetrygd')
       where data::jsonb ? 'utvidet_barnetrygd' and behandling_id = 279 and type = 'INNTEKT';

    DELETE FROM FLYWAY_SCHEMA_HISTORY WHERE VERSION = '2.0.0';
 */

UPDATE grunnlag
set data = data::jsonb - 'ubstListe' || jsonb_build_object('utvidet_barnetrygd', data->'ubstListe')
where data::jsonb ? 'ubstListe' and behandling_id = 279 and type = 'INNTEKT';
