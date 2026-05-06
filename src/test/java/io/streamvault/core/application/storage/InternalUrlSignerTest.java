package io.streamvault.core.application.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InternalUrlSignerTest {

    private InternalUrlSigner signer;

    @BeforeEach
    void setUp() throws Exception {
        signer = new InternalUrlSigner();
        set(signer, "baseUrl", "http://localhost:8081");
        set(signer, "signingSecret", "test-secret");
    }

    @Test
    void signedDownloadUrl_containsAllRequiredParams() {
        String url = signer.signedDownloadUrl("/media/track.mp3", Duration.ofHours(1));

        assertThat(url).startsWith("http://localhost:8081/api/internal/media/download");
        assertThat(url).contains("path=");
        assertThat(url).contains("expires=");
        assertThat(url).contains("sig=");
    }

    @Test
    void signedUploadUrl_pointsToUploadEndpoint() {
        String url = signer.signedUploadUrl("/media/transcoded/track.aac", Duration.ofHours(2));

        assertThat(url).contains("/api/internal/media/upload");
        assertThat(url).contains("sig=");
    }

    @Test
    void verify_acceptsValidSignature() {
        String url = signer.signedDownloadUrl("/media/track.mp3", Duration.ofHours(1));
        var q = parseQuery(URI.create(url).getRawQuery());

        assertThat(signer.verify(q.get("path"), Long.parseLong(q.get("expires")), q.get("sig"))).isTrue();
    }

    @Test
    void verify_rejectsTamperedSignature() {
        String url = signer.signedDownloadUrl("/media/track.mp3", Duration.ofHours(1));
        var q = parseQuery(URI.create(url).getRawQuery());

        assertThat(signer.verify(q.get("path"), Long.parseLong(q.get("expires")), "deadbeef")).isFalse();
    }

    @Test
    void verify_rejectsDifferentPath() {
        String url = signer.signedDownloadUrl("/media/track.mp3", Duration.ofHours(1));
        var q = parseQuery(URI.create(url).getRawQuery());

        assertThat(signer.verify("/media/other.mp3", Long.parseLong(q.get("expires")), q.get("sig"))).isFalse();
    }

    @Test
    void verify_rejectsExpiredToken() {
        long pastEpoch = 1L; // well before any valid expiry
        assertThat(signer.verify("/media/track.mp3", pastEpoch, "anysig")).isFalse();
    }

    @Test
    void verify_rejectsSignatureFromDifferentSecret() throws Exception {
        InternalUrlSigner other = new InternalUrlSigner();
        set(other, "baseUrl", "http://localhost:8081");
        set(other, "signingSecret", "other-secret");

        String url = signer.signedDownloadUrl("/media/track.mp3", Duration.ofHours(1));
        var q = parseQuery(URI.create(url).getRawQuery());

        assertThat(other.verify(q.get("path"), Long.parseLong(q.get("expires")), q.get("sig"))).isFalse();
    }

    @Test
    void downloadAndUpload_urlsPointToDistinctEndpoints() {
        String download = signer.signedDownloadUrl("/media/track.mp3", Duration.ofHours(1));
        String upload = signer.signedUploadUrl("/media/track.mp3", Duration.ofHours(1));

        assertThat(download).contains("/download");
        assertThat(upload).contains("/upload");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static void set(Object target, String field, String value) throws Exception {
        Field f = InternalUrlSigner.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> map = new HashMap<>();
        for (String part : rawQuery.split("&")) {
            String[] kv = part.split("=", 2);
            map.put(kv[0], kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
        }
        return map;
    }
}
