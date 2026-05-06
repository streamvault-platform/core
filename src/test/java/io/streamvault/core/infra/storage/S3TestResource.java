package io.streamvault.core.infra.storage;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;
import java.util.Map;

public class S3TestResource implements QuarkusTestResourceLifecycleManager {

    static final String ACCESS_KEY = "rustfsadmin";
    static final String SECRET_KEY = "rustfsadmin";
    static final String BUCKET = "streamvault-test";

    private GenericContainer<?> rustfs;

    @Override
    public Map<String, String> start() {
        // Prevent AWS SDK from reading potentially malformed ~/.aws/credentials or ~/.aws/config
        System.setProperty("aws.configFile", "/dev/null");
        System.setProperty("aws.sharedCredentialsFile", "/dev/null");

        rustfs = new GenericContainer<>(DockerImageName.parse("rustfs/rustfs:1.0.0-beta.1"))
                .withEnv("RUSTFS_ACCESS_KEY", ACCESS_KEY)
                .withEnv("RUSTFS_SECRET_KEY", SECRET_KEY)
                .withEnv("RUSTFS_VOLUMES", "/data")
                .withEnv("RUSTFS_ADDRESS", ":9000")
                .withExposedPorts(9000)
                .waitingFor(Wait.forListeningPort());
        rustfs.start();

        String endpoint = "http://" + rustfs.getHost() + ":" + rustfs.getMappedPort(9000);
        createBucket(endpoint);

        return Map.of(
                "streamvault.storage.backend", "s3",
                "streamvault.s3.endpoint", endpoint,
                "streamvault.s3.access-key", ACCESS_KEY,
                "streamvault.s3.secret-key", SECRET_KEY,
                "streamvault.s3.bucket", BUCKET,
                "streamvault.s3.region", "us-east-1");
    }

    @Override
    public void stop() {
        if (rustfs != null) rustfs.stop();
    }

    private static void createBucket(String endpoint) {
        var creds = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY));
        var s3Config = S3Configuration.builder().pathStyleAccessEnabled(true).build();

        try (var s3 = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(creds)
                .region(Region.US_EAST_1)
                .serviceConfiguration(s3Config)
                .httpClient(UrlConnectionHttpClient.create())
                .build()) {
            s3.createBucket(b -> b.bucket(BUCKET));
        }
    }
}
