package io.streamvault.core.api.playback;

import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import io.streamvault.core.api.playback.dto.PlaybackStateResponse;
import io.streamvault.core.application.playback.PlaybackService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.UUID;

@Path("/playback")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Playback", description = "Playback state and resume position")
public class PlaybackResource {

    @Inject PlaybackService playbackService;
    @Inject JsonWebToken jwt;

    @GET
    @Path("/state")
    @Operation(summary = "Get playback state",
            description = "Returns the current track and position for the authenticated user. Use on app open to resume playback.")
    @APIResponse(responseCode = "200", description = "Current playback state")
    @APIResponse(responseCode = "204", description = "No playback state yet")
    @APIResponse(responseCode = "401", description = "Missing or invalid JWT")
    public Uni<Response> getState() {
        UUID userId = UUID.fromString(jwt.getSubject());
        return playbackService.getState(userId)
                .map(opt -> opt
                        .<Response>map(s -> Response.ok(PlaybackStateResponse.from(s)).build())
                        .orElse(Response.noContent().build()));
    }
}
