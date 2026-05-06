package io.streamvault.core.infra.storage;

import io.quarkus.test.junit.QuarkusTest;
import io.streamvault.core.application.storage.StorageBackend;
import io.streamvault.core.application.storage.StoredFileMetadata;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class FilesystemStorageBackendIT {

    @Inject
    @StorageBackendType("filesystem")
    StorageBackend filesystem;

    @TempDir
    Path tempDir;

    @Test
    void store_movesFileAndReturnsAbsolutePath() throws IOException {
        Path src = tempFile("track.mp3", "audio data");

        String storedPath = filesystem.store(src, "track.mp3", ".mp3");

        assertThat(storedPath).endsWith(".mp3");
        assertThat(Files.exists(Path.of(storedPath))).isTrue();
        assertThat(Files.exists(src)).isFalse(); // moved, not copied
    }

    @Test
    void metadata_returnsCorrectSize() throws IOException {
        byte[] content = new byte[128];
        Path src = tempFile("sized.flac", content);

        String storedPath = filesystem.store(src, "sized.flac", ".flac");
        StoredFileMetadata meta = filesystem.metadata(storedPath);

        assertThat(meta.size()).isEqualTo(128L);
        assertThat(meta.lastModified()).isNotNull();
    }

    @Test
    void openFull_returnsStoredBytes() throws IOException {
        byte[] content = "full content bytes".getBytes(StandardCharsets.UTF_8);
        Path src = tempFile("full.ogg", content);

        String storedPath = filesystem.store(src, "full.ogg", ".ogg");
        byte[] result = filesystem.openFull(storedPath).readAllBytes();

        assertThat(result).isEqualTo(content);
    }

    @Test
    void openRange_returnsRequestedWindow() throws IOException {
        byte[] content = "0123456789ABCDEF".getBytes(StandardCharsets.UTF_8);
        Path src = tempFile("range.mp3", content);

        String storedPath = filesystem.store(src, "range.mp3", ".mp3");
        byte[] slice = filesystem.openRange(storedPath, 3, 4).readAllBytes();

        assertThat(new String(slice, StandardCharsets.UTF_8)).isEqualTo("3456");
    }

    @Test
    void presignDownload_returnsSignedInternalUrl() {
        String url = filesystem.presignDownload("/media/track.mp3", Duration.ofHours(1));

        assertThat(url).contains("/api/internal/media/download");
        assertThat(url).contains("path=");
        assertThat(url).contains("expires=");
        assertThat(url).contains("sig=");
    }

    @Test
    void presignUpload_returnsSignedInternalUrl() {
        String url = filesystem.presignUpload("/media/transcoded/track.aac", Duration.ofHours(2));

        assertThat(url).contains("/api/internal/media/upload");
        assertThat(url).contains("sig=");
    }

    @Test
    void transcodedStoredPath_isAbsolutePathUnderMediaDir() {
        UUID id = UUID.randomUUID();
        String path = filesystem.transcodedStoredPath(id);

        assertThat(Path.of(path)).isAbsolute();
        assertThat(path).contains("transcoded");
        assertThat(path).endsWith(id + ".aac");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Path tempFile(String name, String content) throws IOException {
        return tempFile(name, content.getBytes(StandardCharsets.UTF_8));
    }

    private Path tempFile(String name, byte[] content) throws IOException {
        Path f = tempDir.resolve(name);
        Files.write(f, content);
        return f;
    }
}
