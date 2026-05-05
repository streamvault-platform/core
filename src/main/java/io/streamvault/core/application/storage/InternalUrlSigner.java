package io.streamvault.core.application.storage;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

@ApplicationScoped
public class InternalUrlSigner {

    @ConfigProperty(name = "streamvault.internal.base-url", defaultValue = "http://localhost:8080")
    String baseUrl;

    @ConfigProperty(name = "streamvault.internal.signing-secret", defaultValue = "dev-signing-secret-change-in-prod")
    String signingSecret;

    public String signedDownloadUrl(String storedPath, Duration expiry) {
        return signedUrl("download", storedPath, expiry);
    }

    public String signedUploadUrl(String storedPath, Duration expiry) {
        return signedUrl("upload", storedPath, expiry);
    }

    public boolean verify(String storedPath, long expiresAt, String sig) {
        if (Instant.now().getEpochSecond() > expiresAt) return false;
        return hmac(storedPath + "|" + expiresAt).equals(sig);
    }

    private String signedUrl(String operation, String storedPath, Duration expiry) {
        long expiresAt = Instant.now().plus(expiry).getEpochSecond();
        String sig = hmac(storedPath + "|" + expiresAt);
        return baseUrl + "/api/internal/media/" + operation
                + "?path=" + URLEncoder.encode(storedPath, StandardCharsets.UTF_8)
                + "&expires=" + expiresAt
                + "&sig=" + sig;
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("HMAC signing failed", e);
        }
    }
}
