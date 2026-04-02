# SNOMED Simplex Toolkit

A web application for authoring and managing SNOMED CT terminology extensions. It integrates with Snowstorm (terminology server), Snolate (JPA-backed translation store and dashboard), and SNOMED release infrastructure.

## Architecture

Two-module Maven project:

- `api/` — Spring Boot 3 backend (Java 17)
- `angular-ui/` — Angular 18 frontend

**Key backend packages:**
- `client/` — External service clients (Snowstorm, RVF, SRS, diagram generator)
- `rest/` — REST controllers
- `service/` — Core business logic
- `translation/` — Translation workflow services and source abstractions
- `snolate/` — Snolate domain, repositories, translation sets, and tool services

## Build & Run

```bash
# Build everything
mvn clean package

# Run backend
java -Xms2g -jar api/snomed-simplex-toolkit*.jar

# Frontend dev server
cd angular-ui && ng serve   # http://localhost:4200/
```

**Local dev stack (Docker):**
```bash
docker-compose -f docker/docker-compose.yml up
```
- App: http://localhost:8081/simplex-toolkit/
- API docs: http://localhost:8081/simplex/api/swagger-ui/
- Snowstorm: http://localhost:8080/

## Tests

```bash
# Backend
mvn test

# Frontend unit tests
cd angular-ui && ng test

# Frontend E2E (Cypress)
cd angular-ui && npx cypress open
```

## Key Configuration

`api/src/main/resources/application.properties` — primary config file.

Important properties:
- `snowstorm.url` — terminology server (default: `http://localhost:8080/`)
- `elasticsearch.urls` — Elasticsearch endpoint
- `diagram.storage.*` — object/file storage for generated concept diagrams
- `translation-copy.storage.*` — translation snapshot storage for sync
- `openai.api-key` — for AI-assisted translation
- `server.port` — default `8081`

## Conventions

- Layered architecture: controllers → services → clients → domain
- External services have dedicated client classes in `client/`
- Translation sources implement `TranslationSource` interface; `TranslationMergeService` combines them
- Background jobs use `@EnableAsync` and `ServiceCallable`
- Security integrates with SNOMED IMS; admin group is `simplex-admin`
- AI translation uses LangChain4J with OpenAI (GPT-4o-mini for fast, GPT-4o for quality)
- Frontend uses RxJS observables throughout; Angular Material for UI
