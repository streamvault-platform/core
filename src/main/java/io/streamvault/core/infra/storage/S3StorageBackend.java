package io.streamvault.core.infra.storage;

import io.streamvault.core.application.library.LibraryException;
import io.streamvault.core.application.storage.StorageBackend;
import io.streamvault.core.application.storage.StoredFileMetadata;
import io.streamvault.core.domain.library.LibraryError;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@StorageBackendType("s3")
public class S3StorageBackend implements StorageBackend {

    @ConfigProperty(name = "streamvault.s3.endpoint")
    Optional<String> endpoint;

    @ConfigProperty(name = "streamvault.s3.access-key")
    Optional<String> accessKey;

    @ConfigProperty(name = "streamvault.s3.secret-key")
    Optional<String> secretKey;

    @ConfigProperty(name = "streamvault.s3.bucket")
    Optional<String> bucket;

    @ConfigProperty(name = "streamvault.s3.region", defaultValue = "us-east-1")
    String region;

    private S3Client s3;
    private S3Presigner presigner;
    private String bucketName;

    @PostConstruct
    void init() {
        if (endpoint.isEmpty()) return;

        var credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey.orElseThrow(), secretKey.orElseThrow()));

        var s3Config = S3Configuration.builder().pathStyleAccessEnabled(true).build();
        var endpointUri = URI.create(endpoint.get());
        bucketName = bucket.orElseThrow();

        s3 = S3Client.builder()
                .endpointOverride(endpointUri)
                .credentialsProvider(credentials)
                .region(Region.of(region))
                .serviceConfiguration(s3Config)
                .httpClient(UrlConnectionHttpClient.create())
                .build();

        presigner = S3Presigner.builder()
                .endpointOverride(endpointUri)
                .credentialsProvider(credentials)
                .region(Region.of(region))
                .serviceConfiguration(s3Config)
                .build();
    }

    @Override
    public String store(Path tempFile, String originalFilename, String extension) {
        requireConfigured();
        String key = "originals/" + UUID.randomUUID() + extension;
        try {
            s3.putObject(
                    PutObjectRequest.builder().bucket(bucketName).key(key).build(),
                    RequestBody.fromFile(tempFile));
            return key;
        } catch (Exception e) {
            throw new LibraryException(new LibraryError.StorageError(e.getMessage()));
        }
    }

    @Override
    public StoredFileMetadata metadata(String storedPath) throws IOException {
        requireConfigured();
        try {
            HeadObjectResponse head = s3.headObject(
                    HeadObjectRequest.builder().bucket(bucketName).key(storedPath).build());
            return new StoredFileMetadata(head.contentLength(), head.lastModified());
        } catch (NoSuchKeyException e) {
            throw new FileNotFoundException("Object not found: " + storedPath);
        }
    }

    @Override
    public InputStream openFull(String storedPath) throws IOException {
        requireConfigured();
        try {
            return s3.getObject(
                    GetObjectRequest.builder().bucket(bucketName).key(storedPath).build());
        } catch (NoSuchKeyException e) {
            throw new FileNotFoundException("Object not found: " + storedPath);
        }
    }

    @Override
    public InputStream openRange(String storedPath, long offset, long length) throws IOException {
        requireConfigured();
        String range = "bytes=" + offset + "-" + (offset + length - 1);
        try {
            return s3.getObject(
                    GetObjectRequest.builder()
                            .bucket(bucketName).key(storedPath).range(range)
                            .build());
        } catch (NoSuchKeyException e) {
            throw new FileNotFoundException("Object not found: " + storedPath);
        }
    }

    @Override
    public String presignDownload(String storedPath, Duration expiry) {
        requireConfigured();
        PresignedGetObjectRequest presigned = presigner.presignGetObject(r -> r
                .signatureDuration(expiry)
                .getObjectRequest(g -> g.bucket(bucketName).key(storedPath)));
        return presigned.url().toString();
    }

    @Override
    public String presignUpload(String storedPath, Duration expiry) {
        requireConfigured();
        PresignedPutObjectRequest presigned = presigner.presignPutObject(r -> r
                .signatureDuration(expiry)
                .putObjectRequest(p -> p.bucket(bucketName).key(storedPath)));
        return presigned.url().toString();
    }

    @Override
    public String transcodedStoredPath(UUID trackId) {
        return "transcoded/" + trackId + ".aac";
    }

    private void requireConfigured() {
        if (s3 == null) throw new IllegalStateException(
                "S3 storage backend is not configured. Set STREAMVAULT_S3_ENDPOINT and related env vars.");
    }
}
