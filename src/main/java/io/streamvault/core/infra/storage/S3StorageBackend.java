package io.streamvault.core.infra.storage;

import io.streamvault.core.application.storage.StorageBackend;
import jakarta.enterprise.context.ApplicationScoped;

import java.nio.file.Path;

@ApplicationScoped
@StorageBackendType("s3")
public class S3StorageBackend implements StorageBackend {

    // TODO: inject S3 client config (bucket, region, credentials) when implementing
    // Required config keys (to be added to application.properties when ready):
    //   streamvault.storage.s3.bucket
    //   streamvault.storage.s3.region
    //   streamvault.storage.s3.endpoint   (optional, for MinIO / custom endpoints)

    @Override
    public String store(Path tempFile, String originalFilename, String extension) {
        throw new UnsupportedOperationException(
                "S3 storage backend is not yet implemented. " +
                "Set streamvault.storage.backend=filesystem to use local storage.");
    }
}
