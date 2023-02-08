# Bidrag-template-spring
Template repo for å opprette ny Spring applikasjon for Bidrag

[![continuous integration](https://github.com/navikt/bidrag-template-spring/actions/workflows/ci.yaml/badge.svg)](https://github.com/navikt/bidrag-dialog/actions/workflows/ci.yaml)
[![release bidrag-template-spring](https://github.com/navikt/bidrag-template-spring/actions/workflows/release.yaml/badge.svg)](https://github.com/navikt/bidrag-dialog/actions/workflows/release.yaml)

## Beskrivelse

Erstatt alt som har postfix `-template-spring` med din applikasjonsnavn

Legg til Github secret `NAIS_DEPLOY_APIKEY` hvor secret hentes fra [Api key](https://deploy.nais.io/apikeys)

## Kjøre applikasjonen lokalt

Start opp applikasjonen ved å kjøre [BidragTemplateLocal.kt](src/test/kotlin/no/nav/bidrag/template/BidragTemplateLocal.kt).
Dette starter applikasjonen med profil `local` og henter miljøvariabler for Q1 miljøet fra filen [application-local.yaml](src/test/resources/application-local.yaml).

Her mangler det noen miljøvariabler som ikke bør committes til Git (Miljøvariabler for passord/secret osv).<br/>
Når du starter applikasjon må derfor følgende miljøvariabl(er) settes:
```bash
-DAZURE_APP_CLIENT_SECRET=<secret>
-DAZURE_APP_CLIENT_ID=<id>
```
Disse kan hentes ved å kjøre kan hentes ved å kjøre 
```bash
kubectl exec --tty deployment/bidrag-dialog-feature -- printenv | grep -e AZURE_APP_CLIENT_ID -e AZURE_APP_CLIENT_SECRET
```

### Live reload
Med `spring-boot-devtools` har Spring støtte for live-reload av applikasjon. Dette betyr i praksis at Spring vil automatisk restarte applikasjonen når en fil endres. Du vil derfor slippe å restarte applikasjonen hver gang du gjør endringer. Dette er forklart i [dokumentasjonen](https://docs.spring.io/spring-boot/docs/1.5.16.RELEASE/reference/html/using-boot-devtools.html#using-boot-devtools-restart).
For at dette skal fungere må det gjøres noe endringer i Intellij instillingene slik at Intellij automatisk re-bygger filene som er endret:

* Gå til `Preference -> Compiler -> check "Build project automatically"`
* Gå til `Preference -> Advanced settings -> check "Allow auto-make to start even if developed application is currently running"`

#### Kjøre lokalt mot sky
For å kunne kjøre lokalt mot sky må du gjøre følgende

Åpne terminal på root mappen til `bidrag-template-spring`
Konfigurer kubectl til å gå mot kluster `dev-gcp`
```bash
# Sett cluster til dev-gcp
kubectx dev-gcp
# Sett namespace til bidrag
kubens bidrag 

# -- Eller hvis du ikke har kubectx/kubens installert 
# (da må -n=bidrag legges til etter exec i neste kommando)
kubectl config use dev-gcp
```
Deretter kjør følgende kommando for å importere secrets. Viktig at filen som opprettes ikke committes til git

```bash
kubectl exec --tty deployment/bidrag-template-spring-feature printenv | grep -E 'AZURE_APP_CLIENT_ID|AZURE_APP_CLIENT_SECRET|TOKEN_X|AZURE_OPENID_CONFIG_TOKEN_ENDPOINT|AZURE_APP_TENANT_ID|AZURE_APP_WELL_KNOWN_URL|KODEVERK_URL|PDL_URL|KRR_URL|KODEVERK_URL|SCOPE' > src/main/resources/application-lokal-nais-secrets.properties
```

Deretter kan tokenet brukes til å logge inn på swagger-ui http://localhost:8080/swagger-ui.html