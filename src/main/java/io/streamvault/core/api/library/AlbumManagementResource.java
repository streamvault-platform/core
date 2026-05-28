package io.streamvault.core.api.library;

import io.smallrye.mutiny.Uni;
import io.streamvault.core.api.common.ErrorResponse;
import io.streamvault.core.api.library.dto.AlbumResponse;
import io.streamvault.core.api.library.dto.UpdateAlbumMetadataRequest;
import io.streamvault.core.application.library.AlbumManagementService;
import io.streamvault.core.application.library.LibraryException;
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

@Path("/albums/{id}")
@RolesAllowed({"ADMIN", "ARTIST"})
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Library — Album Management", description = "Edit and delete albums (ADMIN or owning ARTIST)")
public class AlbumManagementResource {

    @Inject AlbumManagementService albumManagement;
    @Inject JsonWebToken jwt;

    @PATCH
    @Path("/metadata")
    @Operation(summary = "Update album metadata")
    @APIResponse(responseCode = "200", description = "Updated album")
    @APIResponse(responseCode = "403", description = "Not the owner")
    @APIResponse(responseCode = "404", description = "Album not found")
    public Uni<Response> updateMetadata(
            @Parameter(description = "Album ID") @PathParam("id") UUID id,
            UpdateAlbumMetadataRequest req) {
        UUID callerId = UUID.fromString(jwt.getSubject());
        String callerRole = jwt.getGroups().stream().findFirst().orElse("USER");
        return albumManagement.updateMetadata(id, req.title(), req.year(), callerId, callerRole)
                .map(album -> Response.ok(AlbumResponse.from(album)).build());
    }

    @DELETE
    @Operation(summary = "Delete an album (tracks keep existing but lose album association)")
    @APIResponse(responseCode = "204", description = "Deleted")
    @APIResponse(responseCode = "403", description = "Not the owner")
    @APIResponse(responseCode = "404", description = "Album not found")
    public Uni<Response> deleteAlbum(
            @Parameter(description = "Album ID") @PathParam("id") UUID id) {
        UUID callerId = UUID.fromString(jwt.getSubject());
        String callerRole = jwt.getGroups().stream().findFirst().orElse("USER");
        return albumManagement.deleteAlbum(id, callerId, callerRole)
                .map(ignored -> Response.noContent().build());
    }

    @ServerExceptionMapper
    public Response handleLibraryException(LibraryException e) {
        return switch (e.error) {
            case LibraryError.AlbumNotFound x ->
                    Response.status(404).entity(new ErrorResponse("ALBUM_NOT_FOUND", "Album not found")).build();
            case LibraryError.Forbidden x ->
                    Response.status(403).entity(new ErrorResponse("FORBIDDEN", "Access denied — not the owner")).build();
            default ->
                    Response.status(500).entity(new ErrorResponse("INTERNAL_ERROR", "Unexpected error")).build();
        };
    }
}
