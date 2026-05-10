package io.streamvault.core.api.setup;

import io.smallrye.mutiny.Uni;
import io.streamvault.core.api.auth.dto.SetupStatusResponse;
import io.streamvault.core.application.auth.AuthService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/setup")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Setup", description = "Server configuration and first-run status")
public class SetupResource {

    @Inject
    AuthService authService;

    @GET
    @Path("/status")
    @Operation(
            summary = "Setup status",
            description = "Returns whether the initial admin account has been created. "
                    + "Use this to decide whether to show the setup screen or the login screen.")
    @APIResponse(responseCode = "200", description = "Setup status returned")
    public Uni<Response> status() {
        return authService.isConfigured()
                .map(configured -> Response.ok(new SetupStatusResponse(configured)).build());
    }
}
