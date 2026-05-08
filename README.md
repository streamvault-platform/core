# streamvault-core

[![CI](https://github.com/streamvault-platform/core/actions/workflows/ci.yml/badge.svg)](https://github.com/streamvault-platform/core/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/java-21-orange)
![Quarkus](https://img.shields.io/badge/quarkus-3.x-blueviolet)
![License](https://img.shields.io/badge/license-Apache%202.0-blue)

Core API for [Streamvault](https://github.com/streamvault-app) — self-hostable music streaming with native smartwatch sync.

**Java 21 · Quarkus 3.x · PostgreSQL 16 · Kafka · RustFS**

Handles auth, the media library, file upload, audio streaming, and WebSocket playback state. Part of a multi-repo platform — see [streamvault-infra](../infra) to run the full stack.

---

## API surface

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/auth/login` | JWT login |
| `POST` | `/api/auth/refresh` | Refresh token rotation |
| `POST` | `/api/admin/upload` | Bulk multipart upload (MP3, FLAC, OGG, AAC/M4A) |
| `GET` | `/api/library` | Shared catalog |
| `GET/POST/DELETE` | `/api/library/my` | Personal library management |
| `GET` | `/stream/{trackId}` | Range-aware audio streaming |
| `WS` | `/ws/playback` | Real-time play/pause/seek events |

OpenAPI spec: `GET /q/openapi` · Swagger UI: `GET /q/swagger-ui`

---

## Running locally

```sh
# Start Postgres, Kafka, RustFS, Redis, Envoy
cd ../infra && docker compose up -d

# Dev server with hot reload
./mvnw quarkus:dev
```

---

## Tests

```sh
./mvnw test          # unit tests
./mvnw verify        # unit + integration tests (requires Docker for Testcontainers)
```

Integration tests use real Postgres, Kafka, and S3 via Testcontainers — nothing is mocked.

---

## Configuration

| Variable | Default | Notes |
|----------|---------|-------|
| `STREAMVAULT_STORAGE_BACKEND` | `s3` | `s3` or `filesystem` |
| `STREAMVAULT_S3_ENDPOINT` | — | RustFS / S3-compatible endpoint |
| `STREAMVAULT_S3_BUCKET` | — | |
| `STREAMVAULT_S3_ACCESS_KEY` | — | |
| `STREAMVAULT_S3_SECRET_KEY` | — | |
| `STREAMVAULT_MEDIA_PATH` | `/var/streamvault/media` | Filesystem backend root |
| `STREAMVAULT_INTERNAL_BASE_URL` | — | Core's own URL (filesystem pre-signed URLs) |
| `STREAMVAULT_INTERNAL_SIGNING_SECRET` | — | HMAC key for internal URL signing |

Full variable reference in `../infra/.env.example`.

---

## Architecture

Hexagonal layout — resources are thin and delegate to the application layer:

```
api/          ← JAX-RS resources
application/  ← use cases, services
domain/       ← entities, value objects, repository interfaces
infra/        ← Kafka producers, storage adapters, repo implementations
```

Reactive throughout (Mutiny `Uni`/`Multi`). Blocking I/O (file streaming) is isolated to `@Blocking` endpoints.
