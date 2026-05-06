package io.streamvault.core.infra.storage;

import io.quarkus.test.common.QuarkusTestResource;
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
@QuarkusTestResource(value = S3TestResource.class, restrictToAnnotatedClass = true)
class S3StorageBackendIT {

    @Inject
    StorageBackend storage;

    @TempDir
    Path tempDir;

    @Test
    void store_uploadsFileAndReturnsObjectKey() throws IOException {
        Path file = tempFile("track.mp3", "audio content");

        String key = storage.store(file, "track.mp3", ".mp3");

        assertThat(key).startsWith("originals/");
        assertThat(key).endsWith(".mp3");
    }

    @Test
    void metadata_returnsCorrectSize() throws IOException {
        byte[] content = "some audio bytes".getBytes(StandardCharsets.UTF_8);
        Path file = tempFile("meta.flac", content);

        String key = storage.store(file, "meta.flac", ".flac");
        StoredFileMetadata meta = storage.metadata(key);

        assertThat(meta.size()).isEqualTo(content.length);
    }

    @Test
    void openFull_returnsStoredContent() throws IOException {
        byte[] content = "full stream content".getBytes(StandardCharsets.UTF_8);
        Path file = tempFile("full.ogg", content);

        String key = storage.store(file, "full.ogg", ".ogg");
        byte[] result = storage.openFull(key).readAllBytes();

        assertThat(result).isEqualTo(content);
    }

    @Test
    void openRange_returnsRequestedWindow() throws IOException {
        byte[] content = "0123456789ABCDEF".getBytes(StandardCharsets.UTF_8);
        Path file = tempFile("range.mp3", content);

        String key = storage.store(file, "range.mp3", ".mp3");
        byte[] slice = storage.openRange(key, 5, 5).readAllBytes();

        assertThat(new String(slice, StandardCharsets.UTF_8)).isEqualTo("56789");
    }

    @Test
    void presignDownload_returnsUrlContainingKey() throws IOException {
        Path file = tempFile("presign.mp3", "bytes");
        String key = storage.store(file, "presign.mp3", ".mp3");

        String url = storage.presignDownload(key, Duration.ofHours(1));

        assertThat(url).contains(key);
    }

    @Test
    void presignUpload_returnsUrlContainingTranscodedKey() {
        UUID trackId = UUID.randomUUID();
        String transcodedKey = storage.transcodedStoredPath(trackId);

        String url = storage.presignUpload(transcodedKey, Duration.ofHours(2));

        assertThat(url).contains(transcodedKey);
    }

    @Test
    void transcodedStoredPath_usesCorrectKeyFormat() {
        UUID id = UUID.randomUUID();
        assertThat(storage.transcodedStoredPath(id)).isEqualTo("transcoded/" + id + ".aac");
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
