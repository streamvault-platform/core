package io.streamvault.core.infra.storage;

import io.streamvault.core.application.storage.StorageBackend;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class StorageBackendProducer {

    @ConfigProperty(name = "streamvault.storage.backend", defaultValue = "filesystem")
    String backend;

    @Inject
    @StorageBackendType("filesystem")
    StorageBackend filesystem;

    @Inject
    @StorageBackendType("s3")
    StorageBackend s3;

    @Produces
    @ApplicationScoped
    StorageBackend produce() {
        return switch (backend) {
            case "s3" -> s3;
            case "filesystem" -> filesystem;
            default -> throw new IllegalStateException(
                    "Unknown storage backend: '" + backend + "'. Valid values: filesystem, s3");
        };
    }
}
