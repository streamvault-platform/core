package io.streamvault.core.api.library;

import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import io.streamvault.core.api.common.ErrorResponse;
import io.streamvault.core.api.library.dto.AddToLibraryRequest;
import io.streamvault.core.api.library.dto.UserLibraryTrackResponse;
import io.streamvault.core.application.library.LibraryException;
import io.streamvault.core.application.library.UserLibraryService;
import io.streamvault.core.domain.library.LibraryError;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.util.List;
import java.util.UUID;

@Path("/library/my")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Library — Personal", description = "Manage the authenticated user's personal track library")
public class UserLibraryResource {

    private static final Logger LOG = Logger.getLogger(UserLibraryResource.class);

    @Inject UserLibraryService userLibraryService;
    @Inject JsonWebToken jwt;

    @GET
    @Operation(summary = "List my library", description = "Returns a paginated list of tracks the authenticated user has added to their personal library.")
    @APIResponse(responseCode = "200", description = "Page of personal library entries")
    @APIResponse(responseCode = "401", description = "Missing or invalid JWT")
    public Uni<List<UserLibraryTrackResponse>> list(
            @Parameter(description = "Zero-based page index") @QueryParam("page") @DefaultValue("0") int page,
            @Parameter(description = "Page size (max 200)") @QueryParam("size") @DefaultValue("50") int size) {
        UUID userId = currentUserId();
        return userLibraryService.listLibrary(userId, page, size)
                .map(entries -> entries.stream().map(UserLibraryTrackResponse::from).toList());
    }

    @POST
    @Operation(summary = "Add track to my library", description = "Adds a track from the shared catalog to the authenticated user's personal library. Returns 409 if already present.")
    @APIResponse(responseCode = "201", description = "Track added")
    @APIResponse(responseCode = "404", description = "Track not found in catalog")
    @APIResponse(responseCode = "409", description = "Track already in library")
    @APIResponse(responseCode = "401", description = "Missing or invalid JWT")
    public Uni<Response> add(@Valid AddToLibraryRequest req) {
        UUID userId = currentUserId();
        return userLibraryService.addTrack(userId, req.trackId())
                .map(entry -> Response.status(201).entity(UserLibraryTrackResponse.from(entry)).build());
    }

    @DELETE
    @Path("/{trackId}")
    @Operation(summary = "Remove track from my library", description = "Removes a track from the authenticated user's personal library. The track remains in the shared catalog.")
    @APIResponse(responseCode = "204", description = "Track removed")
    @APIResponse(responseCode = "404", description = "Track not in library")
    @APIResponse(responseCode = "401", description = "Missing or invalid JWT")
    public Uni<Response> remove(@Parameter(description = "ID of the track to remove") @PathParam("trackId") UUID trackId) {
        UUID userId = currentUserId();
        return userLibraryService.removeTrack(userId, trackId)
                .map(ignored -> Response.noContent().build());
    }

    @ServerExceptionMapper
    public Response handleLibraryException(LibraryException e) {
        return switch (e.error) {
            case LibraryError.TrackNotFound x ->
                    Response.status(404).entity(new ErrorResponse("TRACK_NOT_FOUND", "Track not found")).build();
            case LibraryError.AlreadyInLibrary x ->
                    Response.status(409).entity(new ErrorResponse("ALREADY_IN_LIBRARY", "Track already in your library")).build();
            case LibraryError.NotInLibrary x ->
                    Response.status(404).entity(new ErrorResponse("NOT_IN_LIBRARY", "Track not in your library")).build();
            case LibraryError.UnsupportedFileType x ->
                    Response.status(422).entity(new ErrorResponse("UNSUPPORTED_FILE_TYPE", "Unsupported file type")).build();
            case LibraryError.StorageError x -> {
                LOG.errorf("library storage failed: %s", x.message());
                yield Response.status(500).entity(new ErrorResponse("STORAGE_ERROR", "Storage error")).build();
            }
        };
    }

    private UUID currentUserId() {
        return UUID.fromString(jwt.getSubject());
    }
}
