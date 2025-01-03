# Bidrag Behandling API

[![continuous integration](https://github.com/navikt/bidrag-behandling/actions/workflows/ci.yaml/badge.svg)](https://github.com/navikt/bidrag-dialog/actions/workflows/ci.yaml)
[![release bidrag-behandling](https://github.com/navikt/bidrag-behandling/actions/workflows/release.yaml/badge.svg)](https://github.com/navikt/bidrag-dialog/actions/workflows/release.yaml)

## Beskrivelse2

Legg til Github secret `NAIS_DEPLOY_APIKEY` hvor secret hentes fra [Api key](https://deploy.nais.io/apikeys)

## Kjøre applikasjonen lokalt

#### Dockermiljø
Et lokalt Dockermiljø må være tilgjengelig for lokal kjøring. Forebredelsessteg for lokal kjøring på Mac OS med Colima Docker:

> colima start \
> docker-compose build \
> docker-compose up 

#### Bygge applikasjonen

###### Maven wrapper
Maven wrapper sikrer at alle Maven bygg-forutsetninger er til stede. For å benytte Maven wrapper kjør
> mvn wrapper:wrapper

deretter kan Maven-kommandoer som clean install kjøres med
> ./mvnw clean install
 
###### Ktlint med Maven wrapper
verifisere
> ./mvnw antrun:run@ktlint

formattere
> ./mvnw antrun:run@ktlint-format

#### Starte applikasjon lokalt

Profil: local (angis i program arguments)

Start opp applikasjonen ved å kjøre [BidragTemplateLocal.kt](src/test/kotlin/no/nav/bidrag/behandling/BidragSpringAppLocal.kt).

Dette starter applikasjonen med profil `local` og henter miljøvariabler for Q1 miljøet fra filen [application-local.yaml](src/test/resources/application-local.yaml).

Her mangler det noen miljøvariabler som ikke bør committes til Git (Miljøvariabler for passord/secret osv).<br/>
Når du starter applikasjon må derfor følgende miljøvariabl(er) settes:
```bash
-DAZURE_APP_CLIENT_SECRET=<secret>
-DAZURE_APP_CLIENT_ID=<id>
```
Disse kan hentes ved å kjøre kan hentes ved å kjøre 
```bash
kubectl exec --tty deployment/bidrag-behandling-feature -- printenv | grep -e AZURE_APP_CLIENT_ID -e AZURE_APP_CLIENT_SECRET
```

#### Kjøre lokalt mot sky

Profil: local-nais (angis i program arguments)

For å kunne kjøre lokalt mot sky må du gjøre følgende

Åpne terminal på root mappen til `bidrag-behandling`
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
kubectl exec --tty deployment/bidrag-behandling printenv | grep -E 'AZURE_APP_CLIENT_ID|AZURE_APP_CLIENT_SECRET|TOKEN_X|AZURE_OPENID_CONFIG_TOKEN_ENDPOINT|AZURE_APP_TENANT_ID|AZURE_APP_WELL_KNOWN_URL|_URL|SCOPE' > src/test/resources/application-lokal-nais-secrets.properties
```

Deretter kan tokenet brukes til å logge inn på swagger-ui http://localhost:8990/swagger-ui.html

### Live reload
Med `spring-boot-devtools` har Spring støtte for live-reload av applikasjon. Dette betyr i praksis at Spring vil automatisk restarte applikasjonen når en fil endres. Du vil derfor slippe å restarte applikasjonen hver gang du gjør endringer. Dette er forklart i [dokumentasjonen](https://docs.spring.io/spring-boot/docs/1.5.16.RELEASE/reference/html/using-boot-devtools.html#using-boot-devtools-restart).
For at dette skal fungere må det gjøres noe endringer i Intellij instillingene slik at Intellij automatisk re-bygger filene som er endret:

* Gå til `Preference -> Compiler -> check "Build project automatically"`
* Gå til `Preference -> Advanced settings -> check "Allow auto-make to start even if developed application is currently running"`
* 