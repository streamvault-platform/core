# Streamvault Core — Claude Instructions

## Project
Core API service for Streamvault — self-hostable music/video streaming platform.
Handles: media library, user auth, streaming, WebSockets, Subsonic-compatible REST.
Java 21 · Quarkus 3.x · PostgreSQL 16 · Kafka (optional) · REST + WebSocket

## Stack constraints
- Framework: Quarkus only. Never Spring Boot, Micronaut, or Spring annotations.
- ORM: Hibernate Reactive with Panache (quarkus-hibernate-reactive-panache + quarkus-reactive-pg-client). Never plain JDBC, blocking ORM, or Spring Data.
- Auth: JWT via SmallRye JWT / Quarkus Security. No sessions, no cookies.
- DB migrations: Flyway. Files in src/main/resources/db/migration/. Never auto-ddl.
- Metrics: Quarkus Micrometer → Prometheus format. Never Dropwizard.
- Logging: Structured JSON via JBoss Logging. Never System.out.println.
- Config: application.properties + ENV overrides. 12-factor. Never hardcode values.
- Kafka: quarkus-messaging-kafka, only active when kafka.enabled=true in config.
  Fallback: in-process queue (java.util.concurrent.LinkedBlockingQueue) when disabled.
- OpenAPI: spec-first. openapi.yaml is the contract. JAX-RS annotations must match it.
- Reactive model: all DB operations return Uni<T> or Multi<T> (Mutiny). Use @ReactiveTransactional for writes.
- Blocking I/O (file streaming): annotate those endpoints with @Blocking. Never block the event loop elsewhere.

## Code style
- Java records for DTOs and value objects. No Lombok.
- Sealed interfaces for domain error hierarchies.
- No checked exceptions in domain/application layers.
- Hexagonal architecture: api → application → domain ← infra
- Tests: @QuarkusTest + Testcontainers (never mock the DB or Kafka).
- Minimum 80% test coverage on application and domain layers.

## File structure
src/main/java/io/streamvault/core/
  api/          ← JAX-RS resources (thin, delegate to application layer)
  application/  ← Use cases, application services
  domain/       ← Entities, value objects, domain services, repository interfaces
  infra/        ← Kafka producers, repo implementations, HTTP clients, filesystem

## Subsonic API
Implement Subsonic REST API 1.16.1 for compatibility with existing clients.
Endpoint prefix: /rest/
All Subsonic endpoints return XML by default, JSON if f=json param present.

## Kafka topics produced
- media.uploaded
- scrobble.events

## Kafka topics consumed
- media.transcoded     (mark media as playback-ready)
- media.metadata-ready (update library metadata)
- watch.sync-ready     (notify watch client via WebSocket)

## Do NOT
- Add Spring annotations (@Component, @Service, @Autowired etc.)
- Write business logic in resource (controller) classes
- Block the event loop — use @Blocking only for file I/O endpoints, never for DB calls
- Share DB tables or schemas with streamvault-pipeline
- Hardcode file paths, ports, or credentials
- Add dependencies without confirming first

## Core Feature List

### Auth
- Register first admin user on fresh install
- Login → JWT access token + refresh token
- Refresh token rotation
- Multi-user with roles — post-MVP (single user is fine for v1)
- OAuth2 / social login — post-MVP

### Library
- Admin uploads audio files via multipart POST /api/admin/upload (single or bulk)
- Supported formats: MP3, FLAC, OGG, AAC/M4A
- ID3/metadata extracted at upload time with JAudioTagger; falls back to filename
- Shared catalog: tracks, artists, albums — all authenticated users can browse
- Personal library: each user adds/removes tracks from the shared catalog
  - POST /api/library/my — add track to personal library
  - DELETE /api/library/my/{trackId} — remove track
  - GET /api/library/my — list personal library
- Storage backend is configurable via STREAMVAULT_STORAGE_BACKEND env var (default: filesystem)
  - filesystem: files stored at STREAMVAULT_MEDIA_PATH (default /var/streamvault/media)
  - s3: stub only — not yet implemented
  - Interface: application/storage/StorageBackend.java (port)
  - Implementations: infra/storage/ (adapters selected by StorageBackendProducer)
- Video library — post-MVP (music first)

### Streaming
- Serve original audio file (direct streaming)
- Range request support (seek works correctly)
- Subsonic-compatible REST API (1.16.1) — enables existing clients (Symfonium, Ultrasonic) to work on day one
- On-the-fly transcoding — post-MVP (serve originals first)
- HLS video streaming — post-MVP

### Playback state
- WebSocket endpoint for real-time playback events (play, pause, seek, track change)
- Persist last-played position per track per user
- Multi-device sync — post-MVP

### Watch sync
- API endpoint: request sync of a set of track IDs to watch
- Publish watch.sync-requested Kafka event
- Consume watch.sync-ready event → notify watch client via WebSocket
- Track sync status per device (pending / syncing / ready / failed)

### Observability
- /health liveness + readiness endpoints
- /metrics Prometheus endpoint (via Quarkus Micrometer)
- Structured JSON logs