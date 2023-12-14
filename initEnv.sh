kubectx dev-gcp
kubectl exec --tty deployment/bidrag-behandling printenv | grep -E 'AZURE_|_URL|SCOPE' | grep -v -e 'BIDRAG_FORSENDELSE_URL' -e 'BIDRAG_TILGANGSKONTROLL_URL' -e 'BIDRAG_GRUNNLAG_URL' -e 'BIDRAG_BEREGN_FORSKUDD_URL' > src/test/resources/application-lokal-nais-secrets.properties
