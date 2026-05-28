package io.streamvault.core.api.library;

import io.smallrye.mutiny.Uni;
import io.streamvault.core.api.common.ErrorResponse;
import io.streamvault.core.api.library.dto.TrackResponse;
import io.streamvault.core.api.library.dto.UpdateTrackMetadataRequest;
import io.streamvault.core.application.library.LibraryException;
import io.streamvault.core.application.library.TrackManagementService;
import io.streamvault.core.domain.library.LibraryError;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.util.UUID;

@Path("/tracks/{id}")
@RolesAllowed({"ADMIN", "ARTIST"})
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Library — Track Management", description = "Edit and delete tracks (ADMIN or owning ARTIST)")
public class TrackManagementResource {

    @Inject TrackManagementService trackManagement;
    @Inject JsonWebToken jwt;

    @PATCH
    @Path("/metadata")
    @Operation(summary = "Update track metadata")
    @APIResponse(responseCode = "200", description = "Updated track")
    @APIResponse(responseCode = "403", description = "Not the owner")
    @APIResponse(responseCode = "404", description = "Track not found")
    public Uni<Response> updateMetadata(
            @Parameter(description = "Track ID") @PathParam("id") UUID id,
            UpdateTrackMetadataRequest req) {
        UUID callerId = UUID.fromString(jwt.getSubject());
        String callerRole = jwt.getGroups().stream().findFirst().orElse("USER");
        return trackManagement.updateMetadata(
                id, req.title(), req.genre(), req.year(),
                req.trackNumber(), req.discNumber(), callerId, callerRole)
                .map(track -> Response.ok(TrackResponse.from(track)).build());
    }

    @DELETE
    @Operation(summary = "Delete a track and its stored files")
    @APIResponse(responseCode = "204", description = "Deleted")
    @APIResponse(responseCode = "403", description = "Not the owner")
    @APIResponse(responseCode = "404", description = "Track not found")
    public Uni<Response> deleteTrack(
            @Parameter(description = "Track ID") @PathParam("id") UUID id) {
        UUID callerId = UUID.fromString(jwt.getSubject());
        String callerRole = jwt.getGroups().stream().findFirst().orElse("USER");
        return trackManagement.deleteTrack(id, callerId, callerRole)
                .map(ignored -> Response.noContent().build());
    }

    @ServerExceptionMapper
    public Response handleLibraryException(LibraryException e) {
        return switch (e.error) {
            case LibraryError.TrackNotFound x ->
                    Response.status(404).entity(new ErrorResponse("TRACK_NOT_FOUND", "Track not found")).build();
            case LibraryError.Forbidden x ->
                    Response.status(403).entity(new ErrorResponse("FORBIDDEN", "Access denied — not the owner")).build();
            default ->
                    Response.status(500).entity(new ErrorResponse("INTERNAL_ERROR", "Unexpected error")).build();
        };
    }
}
