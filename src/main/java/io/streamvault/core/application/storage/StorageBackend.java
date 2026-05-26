package io.streamvault.core.application.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

import java.time.Duration;
import java.util.UUID;

/**
 * Port for file storage. Implementations live in infra/storage/.
 * Selected at startup via streamvault.storage.backend config property.
 *
 * storedPath semantics are backend-specific:
 *   filesystem → absolute path, e.g. /var/streamvault/media/uuid.mp3
 *   s3         → object key,    e.g. originals/uuid.mp3
 */
public interface StorageBackend {

    String store(Path tempFile, String originalFilename, String extension);

    StoredFileMetadata metadata(String storedPath) throws IOException;

    InputStream openFull(String storedPath) throws IOException;

    InputStream openRange(String storedPath, long offset, long length) throws IOException;

    /** Returns a time-limited URL pipeline can use to GET the original file. */
    String presignDownload(String storedPath, Duration expiry);

    /** Returns a time-limited URL pipeline can use to PUT the transcoded file. */
    String presignUpload(String storedPath, Duration expiry);

    /**
     * Returns the backend-specific storedPath for a track's transcoded AAC file.
     * S3: object key  →  transcoded/{trackId}.aac
     * Filesystem: absolute path under mediaPath
     */
    String transcodedStoredPath(UUID trackId);

    /** Stores cover art and returns the backend-specific storedPath. */
    String storeCoverArt(Path tempFile, UUID albumId, String extension);

    /** Deletes the object/file at storedPath. No-op if not found. */
    void delete(String storedPath);
}
