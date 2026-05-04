package io.streamvault.core.application.storage;

import java.io.IOException;
import java.io.InputStream;
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

    /**
     * Returns size and last-modified timestamp for the stored file.
     * Throws {@link java.io.FileNotFoundException} if the path does not exist.
     */
    StoredFileMetadata metadata(String storedPath) throws IOException;

    /** Opens a stream over the entire file. */
    InputStream openFull(String storedPath) throws IOException;

    /**
     * Opens a stream limited to {@code length} bytes starting at {@code offset}.
     * S3 implementations should map this to a native byte-range GET request.
     */
    InputStream openRange(String storedPath, long offset, long length) throws IOException;
}
