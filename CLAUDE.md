# Streamvault Core — Claude Instructions

## Project
Core API service for Streamvault — self-hostable music/video streaming platform.
Handles: media library, user auth, streaming, WebSockets, Subsonic-compatible REST.
Java 21 · Quarkus 3.x · PostgreSQL 16 · Kafka · REST + WebSocket

## Stack constraints
- Framework: Quarkus only. Never Spring Boot, Micronaut, or Spring annotations.
- ORM: Hibernate Reactive with Panache (quarkus-hibernate-reactive-panache + quarkus-reactive-pg-client). Never plain JDBC, blocking ORM, or Spring Data.
- Auth: JWT via SmallRye JWT / Quarkus Security. No sessions, no cookies.
- DB migrations: Flyway. Files in src/main/resources/db/migration/. Never auto-ddl.
- Metrics: Quarkus Micrometer → Prometheus format. Never Dropwizard.
- Logging: Structured JSON via JBoss Logging. Never System.out.println.
- Config: application.properties + ENV overrides. 12-factor. Never hardcode values.
- Kafka: quarkus-messaging-kafka. Always required — no optional flag, no fallback queue.
- OpenAPI: code-first via quarkus-smallrye-openapi. Spec auto-generated from JAX-RS
  annotations and served at GET /q/openapi. Enrich with @Operation/@APIResponse/@Tag
  annotations where the generated output is unclear. Never hand-write openapi.yaml.
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

## Test patterns
- Always use `TRUNCATE ... CASCADE` in `@BeforeEach` cleanup — `user_library_tracks` and
  `refresh_tokens` both have FKs on `users`, plain TRUNCATE will fail with a Postgres FK error.
- Never call `PanacheRepositoryBase.super.<method>()` — Panache replaces those via bytecode
  generation at build time; the interface default is a dead stub that throws. Use the Panache
  query API (`find`, `delete`, etc.) directly instead.

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
- Storage backend is configurable via STREAMVAULT_STORAGE_BACKEND env var
  - `s3` (default in prod): RustFS / any S3-compatible store. Uses AWS SDK v2 with path-style
    access. Env vars: STREAMVAULT_S3_ENDPOINT, STREAMVAULT_S3_ACCESS_KEY,
    STREAMVAULT_S3_SECRET_KEY, STREAMVAULT_S3_BUCKET, STREAMVAULT_S3_REGION.
  - `filesystem` (dev/simple): files stored at STREAMVAULT_MEDIA_PATH. Pre-signed URLs
    point to core's own internal endpoints — file transfer goes through core (not
    horizontally scalable, but functional without object storage).
  - Interface: application/storage/StorageBackend.java (port). Methods:
    - store(path, filename, ext) → storedPath
    - openFull(storedPath) → InputStream
    - openRange(storedPath, start, length) → InputStream
    - metadata(storedPath) → StoredFileMetadata
    - presignDownload(storedPath, expiry) → URL string
    - presignUpload(storedPath, expiry) → URL string
  - Implementations: infra/storage/ (adapters selected by StorageBackendProducer)
- Pipeline file access — core owns storage. The media.uploaded Kafka event carries:
  - downloadUrl: pre-signed URL for pipeline to fetch the original file
  - uploadUrl: pre-signed URL for pipeline to PUT the transcoded file
  Pipeline uses these URLs directly (HTTP GET/PUT). No storage credentials in pipeline.
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

---

## Deferred / Future Work

Things explicitly decided to defer — not forgotten, not in scope for MVP.

### Streaming
- **On-the-fly transcoding** — post-MVP via FFmpeg. `transcodedPath` on a track is the
  watch-optimized AAC file (for watch sync packages only). Streaming always serves the
  original file. These are separate concerns.

### Storage / Pipeline file transfer
- **Architecture decision (implemented)** — core owns all storage. Pipeline is storage-
  agnostic. The `media.uploaded` Kafka event carries `downloadUrl` and `uploadUrl`
  (pre-signed URLs generated by core). Pipeline HTTP GET/PUTs those URLs directly;
  no storage credentials or SDK needed in pipeline.
- **S3/RustFS backend** — uses AWS SDK v2 (`software.amazon.awssdk:s3`). Pre-signed
  GET/PUT URLs point directly to RustFS — pipeline bypasses core for file transfer.
  Required env vars: STREAMVAULT_S3_ENDPOINT, STREAMVAULT_S3_ACCESS_KEY,
  STREAMVAULT_S3_SECRET_KEY, STREAMVAULT_S3_BUCKET, STREAMVAULT_S3_REGION.
  Path-style access always enabled (required for self-hosted S3-compatible stores).
- **Filesystem backend** — fully supported. `presignDownload`/`presignUpload` generate
  HMAC-signed URLs pointing at core's own `GET/PUT /api/internal/media/{download,upload}`
  endpoints. File bytes flow through core (not horizontally scalable, but functional).
  Required env vars: STREAMVAULT_INTERNAL_BASE_URL (e.g. `http://core:8080`),
  STREAMVAULT_INTERNAL_SIGNING_SECRET.
- **Transcoded file flow** — on upload, core pre-determines the transcoded destination
  (`transcoded/{trackId}.aac`) and includes it as `transcodedStoredPath` in
  `media.uploaded`. Pipeline echoes it back in `media.transcoded`. Core writes it to
  `tracks.transcoded_path`. Used by watch sync only. Originals are never deleted.

### Playback state
- **Heartbeat position updates** — clients will send periodic position events (~every 15s). Do NOT write each one to Postgres. Buffer in Redis (already in stack), flush to Postgres on PAUSE or via a 30s background job.
- **Multi-device sync** — broadcast playback events to other connected sessions of the same user.

### Pipeline / Kafka
- **`scrobble.events`** — produce from PlaybackWebSocket PLAY events for play history tracking.
- **`watch.sync-requested`** — produce when user requests watch sync. Deferred until watch app is built.
- **`watch.sync-ready`** — consume and notify watch client via WebSocket. Deferred until watch app is built.

### Library / Clients
- **Subsonic 1.16.1 API** (`/rest/`) — intentionally skipped, user is building own platform. Could be useful for migration tooling later.
- **Spotify / Subsonic import** — migration tooling for users moving from other platforms. Post-launch feature.

### Auth
- **Multi-user with roles** — single user (admin) is fine for v1.
- **OAuth2 / social login** — post-MVP.