package io.streamvault.core.infra.storage;

import io.streamvault.core.application.library.LibraryException;
import io.streamvault.core.application.storage.InternalUrlSigner;
import io.streamvault.core.application.storage.StorageBackend;
import io.streamvault.core.application.storage.StoredFileMetadata;
import io.streamvault.core.domain.library.LibraryError;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.UUID;

@ApplicationScoped
@StorageBackendType("filesystem")
public class FilesystemStorageBackend implements StorageBackend {

    @ConfigProperty(name = "streamvault.media.path")
    String mediaPath;

    @Inject
    InternalUrlSigner signer;

    @Override
    public String store(Path tempFile, String originalFilename, String extension) {
        Path dest = Path.of(mediaPath).resolve("originals").resolve(UUID.randomUUID() + extension);
        try {
            Files.createDirectories(dest.getParent());
            Files.move(tempFile, dest);
            return dest.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new LibraryException(new LibraryError.StorageError(e.getMessage()));
        }
    }

    @Override
    public StoredFileMetadata metadata(String storedPath) throws IOException {
        Path file = Path.of(storedPath);
        if (!Files.exists(file)) {
            throw new FileNotFoundException("File not found: " + storedPath);
        }
        return new StoredFileMetadata(
                Files.size(file),
                Files.getLastModifiedTime(file).toInstant());
    }

    @Override
    public InputStream openFull(String storedPath) throws IOException {
        return Files.newInputStream(Path.of(storedPath));
    }

    @Override
    public InputStream openRange(String storedPath, long offset, long length) throws IOException {
        FileChannel channel = FileChannel.open(Path.of(storedPath), StandardOpenOption.READ);
        channel.position(offset);
        return new LimitedInputStream(Channels.newInputStream(channel), length);
    }

    @Override
    public String presignDownload(String storedPath, Duration expiry) {
        return signer.signedDownloadUrl(storedPath, expiry);
    }

    @Override
    public String presignUpload(String storedPath, Duration expiry) {
        return signer.signedUploadUrl(storedPath, expiry);
    }

    @Override
    public String transcodedStoredPath(UUID trackId) {
        return Path.of(mediaPath).resolve("transcoded").resolve(trackId + ".aac")
                .toAbsolutePath().toString();
    }

    @Override
    public String storeCoverArt(Path tempFile, UUID albumId, String extension) {
        Path dest = Path.of(mediaPath).resolve("covers").resolve(albumId + extension);
        try {
            Files.createDirectories(dest.getParent());
            Files.move(tempFile, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return dest.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new LibraryException(new LibraryError.StorageError(e.getMessage()));
        }
    }

    @Override
    public void delete(String storedPath) {
        try {
            Files.deleteIfExists(Path.of(storedPath));
        } catch (IOException ignored) {
        }
    }
}
