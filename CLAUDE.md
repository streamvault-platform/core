# Streamvault Core — Claude Instructions

## Project
Core API for Streamvault — self-hostable music streaming platform.
Java 25 · Quarkus 3.x · PostgreSQL 16 · Kafka · REST + WebSocket

Handles: auth, media library, file upload/streaming, WebSocket playback state, watch sync.
For platform-wide scope and Kafka topic definitions, see the root CLAUDE.md.

## Stack constraints
- Framework: Quarkus only. Never Spring Boot, Micronaut, or Spring annotations.
- ORM: Hibernate Reactive with Panache (`quarkus-hibernate-reactive-panache` + `quarkus-reactive-pg-client`). Never plain JDBC, blocking ORM, or Spring Data.
- Auth: JWT via SmallRye JWT / Quarkus Security. No sessions, no cookies.
- DB migrations: Flyway. Files in `src/main/resources/db/migration/`. Never auto-ddl.
- Metrics: Quarkus Micrometer → Prometheus. Never Dropwizard.
- Logging: Structured JSON via JBoss Logging. Never `System.out.println`.
- Config: `application.properties` + ENV overrides. 12-factor. Never hardcode values.
- Kafka: `quarkus-messaging-kafka`. Always required — no optional flag, no fallback.
- OpenAPI: code-first via `quarkus-smallrye-openapi`. Served at `GET /q/openapi`. Never hand-write `openapi.yaml`. Every new JAX-RS resource **must** include: `@Tag` on the class, `@Operation` + one `@APIResponse` per meaningful status code on every method, `@Parameter` on non-obvious path/query params. `HealthResource` is Vert.x router-based — OpenAPI annotations don't apply there.
- Reactive: all DB operations return `Uni<T>` or `Multi<T>`. Use `@ReactiveTransactional` for writes.
- Blocking I/O: `@Blocking` only on file-streaming endpoints. Never block the event loop elsewhere.

## Code style
- Java records for DTOs and value objects. No Lombok.
- Sealed interfaces for domain error hierarchies.
- No checked exceptions in domain/application layers.
- Hexagonal architecture: `api → application → domain ← infra`
- Tests: `@QuarkusTest` + Testcontainers. Never mock the DB or Kafka.
- Minimum 80% coverage on application and domain layers.

## File structure
```
src/main/java/io/streamvault/core/
  api/          ← JAX-RS resources (thin — delegate everything to application layer)
  application/  ← use cases, application services
  domain/       ← entities, value objects, domain services, repository interfaces
  infra/        ← Kafka producers, repo implementations, storage adapters
```

## Kafka topics
Produced: `media.uploaded`, `scrobble.events`
Consumed: `media.transcoded`, `media.metadata-ready`, `watch.sync-ready`

## Storage backend
Configurable via `STREAMVAULT_STORAGE_BACKEND`:
- `s3` (default): RustFS / S3-compatible. AWS SDK v2, path-style access required. Env vars: `STREAMVAULT_S3_ENDPOINT`, `STREAMVAULT_S3_ACCESS_KEY`, `STREAMVAULT_S3_SECRET_KEY`, `STREAMVAULT_S3_BUCKET`, `STREAMVAULT_S3_REGION`.
- `filesystem` (dev): files at `STREAMVAULT_MEDIA_PATH`. Pre-signed URLs are HMAC-signed and point to core's own `/internal/media` endpoints. Env vars: `STREAMVAULT_INTERNAL_BASE_URL`, `STREAMVAULT_INTERNAL_SIGNING_SECRET`.

Core owns all storage. Pipeline is storage-agnostic — `media.uploaded` carries `downloadUrl` and `uploadUrl` (pre-signed). Pipeline HTTP GET/PUTs those directly; no storage credentials in pipeline.

Port: `application/storage/StorageBackend.java`. Implementations in `infra/storage/`, selected by `StorageBackendProducer`.

## Test patterns
- Use `TRUNCATE ... CASCADE` in `@BeforeEach` — `user_library_tracks` and `refresh_tokens` have FKs on `users`; plain `TRUNCATE` will fail with a Postgres FK error.
- Never call `PanacheRepositoryBase.super.<method>()` — Panache replaces those via bytecode at build time; the interface default is a dead stub that throws. Use the Panache query API (`find`, `delete`, etc.) directly.

## Do NOT
- Add Spring annotations (`@Component`, `@Service`, `@Autowired`, etc.)
- Write business logic in resource classes
- Block the event loop — `@Blocking` only for file I/O, never for DB calls
- Share DB tables or schemas with `streamvault-pipeline`
- Hardcode file paths, ports, or credentials
- Add dependencies without confirming first
