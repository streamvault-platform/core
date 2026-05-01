package io.streamvault.core.api;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/health")
public class HealthResource {

    @ConfigProperty(name = "streamvault.version", defaultValue = "dev")
    String version;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public HealthResponse get() {
        return new HealthResponse("UP", version);
    }

    public record HealthResponse(String status, String version) {
    }
}
