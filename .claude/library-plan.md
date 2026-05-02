# Library Feature — Implementation Plan (Upload-based)

## Model

**No folder scanning.** Audio files are uploaded by an admin via multipart HTTP.
The server stores them, extracts metadata, and populates a shared catalog.
Users browse the shared catalog and maintain a personal library (a subset of the catalog).

---

## Data model

| Table | Purpose |
|---|---|
| `artists` | Deduplicated by name |
| `albums` | Deduplicated by (title, artist_id) |
| `tracks` | One row per file, keyed on `file_path` |
| `user_library_tracks` | Join table: (user_id, track_id) — personal collections |

---

## API

| Method | Path | Role | Description |
|---|---|---|---|
| POST | /api/admin/upload | ADMIN | Upload one or more audio files (multipart) |
| GET | /api/library/tracks | Authenticated | Browse full catalog |
| GET | /api/library/artists | Authenticated | Browse artists |
| GET | /api/library/albums | Authenticated | Browse albums |
| GET | /api/library/my | Authenticated | List personal library |
| POST | /api/library/my | Authenticated | Add track to personal library |
| DELETE | /api/library/my/{trackId} | Authenticated | Remove track from personal library |

---

## Upload flow

1. `POST /api/admin/upload` receives `multipart/form-data` with field `files` (one or many)
2. For each file:
   - Validate extension (mp3 / flac / ogg / aac / m4a) → 422 if unsupported
   - Generate UUID, move from Quarkus temp dir to `STREAMVAULT_MEDIA_PATH/{uuid}{ext}`
   - Extract ID3 tags with JAudioTagger on worker pool thread (blocking)
   - Upsert artist → album → track inside a reactive transaction
3. Returns 201 with list of `UploadResponse` (trackId, title, artist, album, durationMs, mimeType)

---

## Personal library flow

- `POST /api/library/my` with `{ "trackId": "..." }` inserts into `user_library_tracks`
- Duplicate → 409 ALREADY_IN_LIBRARY
- Unknown trackId → 404 TRACK_NOT_FOUND
- `DELETE /api/library/my/{trackId}` removes the row; not present → 404 NOT_IN_LIBRARY

---

## Key classes

| Class | Layer | Responsibility |
|---|---|---|
| `UploadService` | application | Store file + extract metadata + upsert catalog |
| `UserLibraryService` | application | add/remove/list personal library |
| `TrackMetadata` | application | Record carrying extracted tag data |
| `AdminUploadResource` | api/admin | ADMIN-only upload endpoint |
| `UserLibraryResource` | api/library | Personal library CRUD |
| `TrackResource` | api/library | Catalog browse (tracks, artists, albums) |

---

## Config

```
streamvault.media.path   — where uploaded files are stored (ENV: STREAMVAULT_MEDIA_PATH)
quarkus.http.body.*      — multipart temp upload directory and cleanup settings
```

---

## What was removed

Folder scanning (`media_folders` table, `MediaScanner`, `LibraryService`, scan endpoints)
was dropped in favour of this upload-based model. Rationale: folder scanning assumes
the server has direct filesystem access to a music collection, which is awkward for
typical VPS deployments. Upload-based ingestion works everywhere.
