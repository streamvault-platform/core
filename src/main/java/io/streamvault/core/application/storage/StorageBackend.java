package io.streamvault.core.application.storage;

import java.nio.file.Path;

/**
 * Port for file storage. Implementations live in infra/storage/.
 * Selected at startup via streamvault.storage.backend config property.
 */
public interface StorageBackend {

    /**
     * Moves/uploads a file from the temp upload path to permanent storage.
     *
     * @param tempFile         path to the temporary uploaded file
     * @param originalFilename original filename from the multipart upload
     * @param extension        lowercase file extension including dot, e.g. ".mp3"
     * @return the permanent identifier for the stored file (absolute path for
     *         filesystem, object key for S3)
     */
    String store(Path tempFile, String originalFilename, String extension);
}
