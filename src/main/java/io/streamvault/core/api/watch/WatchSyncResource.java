package io.streamvault.core.api.watch;

import io.smallrye.mutiny.Uni;
import io.streamvault.core.api.common.ErrorResponse;
import io.streamvault.core.api.watch.dto.SyncRequestBody;
import io.streamvault.core.application.watch.WatchSyncError;
import io.streamvault.core.application.watch.WatchSyncException;
import io.streamvault.core.application.watch.WatchSyncService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.util.UUID;

@Path("/sync")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("**")
@Tag(name = "Watch Sync", description = "Request and track watch sync operations")
public class WatchSyncResource {

    @Inject WatchSyncService watchSyncService;
    @Inject JsonWebToken jwt;

    @POST
    @Path("/request")
    @Operation(summary = "Request watch sync", description = "Request a set of tracks to be packaged for offline watch playback. Returns immediately; sync completes asynchronously via watch.sync-requested Kafka event.")
    @APIResponse(responseCode = "202", description = "Sync request accepted")
    @APIResponse(responseCode = "404", description = "One or more track IDs not found")
    @APIResponse(responseCode = "422", description = "One or more tracks not yet transcoded")
    public Uni<Response> requestSync(@Valid SyncRequestBody body) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return watchSyncService.requestSync(userId, body.deviceId(), body.trackIds())
                .map(resp -> Response.accepted(resp).build());
    }

    @GET
    @Path("/status/{syncRequestId}")
    @Operation(summary = "Get sync status", description = "Returns the current status of a sync request. Manifest is populated once status is READY.")
    @APIResponse(responseCode = "200", description = "Sync status returned")
    @APIResponse(responseCode = "403", description = "Sync request belongs to a different user")
    @APIResponse(responseCode = "404", description = "Sync request not found")
    public Uni<Response> getStatus(@PathParam("syncRequestId") UUID syncRequestId) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return watchSyncService.getStatus(userId, syncRequestId)
                .map(resp -> Response.ok(resp).build());
    }

    @ServerExceptionMapper
    public Response mapWatchSyncException(WatchSyncException ex) {
        return switch (ex.error) {
            case WatchSyncError.TrackNotFound e ->
                    Response.status(404).entity(new ErrorResponse("TRACK_NOT_FOUND", "Track(s) not found: " + e.missingIds())).build();
            case WatchSyncError.TrackNotTranscoded e ->
                    Response.status(422).entity(new ErrorResponse("TRACK_NOT_TRANSCODED", "Track(s) not yet transcoded: " + e.ids())).build();
            case WatchSyncError.SyncRequestNotFound e ->
                    Response.status(404).entity(new ErrorResponse("SYNC_REQUEST_NOT_FOUND", "Sync request not found: " + e.syncRequestId())).build();
            case WatchSyncError.Forbidden e ->
                    Response.status(403).entity(new ErrorResponse("FORBIDDEN", "Forbidden")).build();
        };
    }
}
