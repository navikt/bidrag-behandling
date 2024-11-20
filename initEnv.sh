#!/bin/bash
kubectx dev-gcp

deployment="deployment/bidrag-behandling-q2"
[ "$1" == "q2" ] && deployment="deployment/bidrag-behandling"
echo "Henter miljøparametere fra deployment: $deployment"
kubectl exec --tty $deployment printenv | grep -E 'AZURE_|_URL|SCOPE|UNLEASH' | grep -v -e 'BIDRAG_FORSENDELSE_URL' -e 'BIDRAG_TILGANGSKONTROLL_URL' -e 'BIDRAG_GRUNNLAG_URL' -e 'BIDRAG_VEDTAK_SCOPE' -e 'BIDRAG_VEDTAK_URL' -e 'BIDRAG_STONAD_URL' > src/test/resources/application-lokal-nais-secrets.properties