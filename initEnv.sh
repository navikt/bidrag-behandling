kubectx dev-gcp
kubectl exec --tty deployment/bidrag-behandling printenv | grep -E 'AZURE_APP_CLIENT_ID|AZURE_APP_CLIENT_SECRET|TOKEN_X|AZURE_OPENID_CONFIG_TOKEN_ENDPOINT|AZURE_APP_TENANT_ID|AZURE_APP_WELL_KNOWN_URL|_URL|SCOPE' > src/test/resources/application-lokal-nais-secrets.properties
