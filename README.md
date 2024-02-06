# tms-brannslukning

App for å vise innhold på min-side ved diverse hendelser i NAV

## Features
- [x] Masseutsending av varsel og ekstern varsling
- [ ] Informasjon i kontekst for spesifikke personer

## Kjøre lokalt

1.
Run configurations i intellij:
VM Options: -Dio.ktor.development=true \
Environment variables: 
```
AZURE_APP_CLIENT_ID=tms-varsel-admin;AZURE_APP_TENANT_ID=nav.no;AZURE_APP_WELL_KNOWN_URL=http://host.docker.internal:8080/issueissue/.well-known/openid-configuration;AZURE_CLIENT_ID=local.dev;DB_DATABASE=brannslukning;DB_HOST=localhost;DB_PASSWORD=brannslukning;DB_PORT=5432;DB_USERNAME=postgres;DEV_MODE=true
```
2. Start docker: `docker-compose up`
3. Start appen
4. Gå til localhost:3000