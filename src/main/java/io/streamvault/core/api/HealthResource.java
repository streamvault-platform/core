package io.streamvault.core.api;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class HealthResource {

    @ConfigProperty(name = "streamvault.version", defaultValue = "dev")
    String version;

    void init(@Observes Router router) {
        router.get("/health").handler(ctx -> ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject.of("status", "UP", "version", version).encode()));
    }
}
