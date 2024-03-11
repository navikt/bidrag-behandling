kubectx dev-gcp
kubectl exec --tty deployment/bidrag-behandling printenv | grep -E 'AZURE_|_URL|SCOPE|UNLEASH' | grep -v -e 'DB_URL' > src/test/resources/application-lokal-nais-secrets.properties
