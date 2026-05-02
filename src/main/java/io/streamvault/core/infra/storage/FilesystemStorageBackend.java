package io.streamvault.core.infra.storage;

import io.streamvault.core.application.library.LibraryException;
import io.streamvault.core.application.storage.StorageBackend;
import io.streamvault.core.domain.library.LibraryError;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@ApplicationScoped
@StorageBackendType("filesystem")
public class FilesystemStorageBackend implements StorageBackend {

    @ConfigProperty(name = "streamvault.media.path")
    String mediaPath;

    @Override
    public String store(Path tempFile, String originalFilename, String extension) {
        Path dest = Path.of(mediaPath).resolve(UUID.randomUUID() + extension);
        try {
            Files.createDirectories(dest.getParent());
            Files.move(tempFile, dest);
            return dest.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new LibraryException(new LibraryError.StorageError(e.getMessage()));
        }
    }
}
