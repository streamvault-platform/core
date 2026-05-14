package io.streamvault.core.api.playlist;

import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import io.streamvault.core.api.common.ErrorResponse;
import io.streamvault.core.api.playlist.dto.*;
import io.streamvault.core.application.playlist.PlaylistException;
import io.streamvault.core.application.playlist.PlaylistService;
import io.streamvault.core.domain.playlist.PlaylistError;
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
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.util.List;
import java.util.UUID;

@Path("/playlists")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Playlists", description = "Manage user playlists")
public class PlaylistResource {

    @Inject PlaylistService playlistService;
    @Inject JsonWebToken jwt;

    @GET
    @Operation(summary = "List playlists", description = "Returns a list of the authenticated user's playlists.")
    @APIResponse(responseCode = "200", description = "List of playlists")
    @APIResponse(responseCode = "401", description = "Missing or invalid JWT")
    public Uni<List<PlaylistSummaryResponse>> list() {
        UUID userId = currentUserId();
        return playlistService.list(userId)
                .map(playlists -> playlists.stream().map(PlaylistSummaryResponse::from).toList());
    }

    @GET
    @Path("/public")
    @Operation(summary = "List public playlists", description = "Returns all public playlists visible to authenticated users.")
    @APIResponse(responseCode = "200", description = "List of public playlists")
    @APIResponse(responseCode = "401", description = "Missing or invalid JWT")
    public Uni<List<PlaylistSummaryResponse>> listPublic() {
        return playlistService.listPublic()
                .map(playlists -> playlists.stream().map(PlaylistSummaryResponse::from).toList());
    }

    @POST
    @Operation(summary = "Create playlist", description = "Creates a new private playlist for the authenticated user.")
    @APIResponse(responseCode = "201", description = "Playlist created")
    @APIResponse(responseCode = "401", description = "Missing or invalid JWT")
    public Uni<Response> create(@Valid CreatePlaylistRequest req) {
        UUID userId = currentUserId();
        return playlistService.create(userId, currentUsername(), req.name())
                .map(playlist -> Response.status(201).entity(PlaylistSummaryResponse.from(playlist)).build());
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get playlist", description = "Returns the details of a playlist. Public playlists are visible to all authenticated users.")
    @APIResponse(responseCode = "200", description = "Playlist details")
    @APIResponse(responseCode = "401", description = "Missing or invalid JWT")
    @APIResponse(responseCode = "403", description = "Playlist is private and belongs to another user")
    @APIResponse(responseCode = "404", description = "Playlist not found")
    public Uni<PlaylistDetailResponse> get(
            @Parameter(description = "ID of the playlist") @PathParam("id") UUID id) {
        UUID userId = currentUserId();
        return playlistService.get(userId, id).map(PlaylistDetailResponse::from);
    }

    @PATCH
    @Path("/{id}")
    @Operation(summary = "Rename playlist", description = "Renames an existing playlist.")
    @APIResponse(responseCode = "200", description = "Playlist renamed")
    @APIResponse(responseCode = "401", description = "Missing or invalid JWT")
    @APIResponse(responseCode = "403", description = "Playlist belongs to another user")
    @APIResponse(responseCode = "404", description = "Playlist not found")
    public Uni<PlaylistSummaryResponse> rename(
            @Parameter(description = "ID of the playlist") @PathParam("id") UUID id,
            @Valid RenamePlaylistRequest req) {
        UUID userId = currentUserId();
        return playlistService.rename(userId, id, req.name())
                .map(PlaylistSummaryResponse::from);
    }

    @PATCH
    @Path("/{id}/visibility")
    @Operation(summary = "Set playlist visibility", description = "Makes a playlist public or private. Only the owner can change visibility.")
    @APIResponse(responseCode = "204", description = "Visibility updated")
    @APIResponse(responseCode = "401", description = "Missing or invalid JWT")
    @APIResponse(responseCode = "403", description = "Playlist belongs to another user")
    @APIResponse(responseCode = "404", description = "Playlist not found")
    public Uni<Response> setVisibility(
            @Parameter(description = "ID of the playlist") @PathParam("id") UUID id,
            @Valid SetVisibilityRequest req) {
        UUID userId = currentUserId();
        return playlistService.setVisibility(userId, id, req.isPublic())
                .map(v -> Response.noContent().build());
    }

    @POST
    @Path("/{id}/copy")
    @Consumes(MediaType.WILDCARD)
    @Operation(summary = "Copy playlist", description = "Creates a private copy of a public playlist in the authenticated user's library.")
    @APIResponse(responseCode = "201", description = "Playlist copied — new playlist returned")
    @APIResponse(responseCode = "401", description = "Missing or invalid JWT")
    @APIResponse(responseCode = "403", description = "Playlist is private and belongs to another user")
    @APIResponse(responseCode = "404", description = "Playlist not found")
    public Uni<Response> copy(
            @Parameter(description = "ID of the playlist to copy") @PathParam("id") UUID id) {
        UUID userId = currentUserId();
        return playlistService.copy(userId, currentUsername(), id)
                .map(playlist -> Response.status(201).entity(PlaylistSummaryResponse.from(playlist)).build());
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete playlist", description = "Deletes a playlist and all its contents.")
    @APIResponse(responseCode = "204", description = "Playlist deleted")
    @APIResponse(responseCode = "401", description = "Missing or invalid JWT")
    @APIResponse(responseCode = "403", description = "Playlist belongs to another user")
    @APIResponse(responseCode = "404", description = "Playlist not found")
    public Uni<Response> delete(
            @Parameter(description = "ID of the playlist") @PathParam("id") UUID id) {
        UUID userId = currentUserId();
        return playlistService.delete(userId, id)
                .map(v -> Response.noContent().build());
    }

    @POST
    @Path("/{id}/tracks")
    @Operation(summary = "Add track to playlist", description = "Adds a track to a playlist.")
    @APIResponse(responseCode = "201", description = "Track added")
    @APIResponse(responseCode = "401", description = "Missing or invalid JWT")
    @APIResponse(responseCode = "403", description = "Playlist belongs to another user")
    @APIResponse(responseCode = "404", description = "Playlist or track not found")
    @APIResponse(responseCode = "409", description = "Track already in playlist")
    public Uni<Response> addTrack(
            @Parameter(description = "ID of the playlist") @PathParam("id") UUID id,
            @Valid AddPlaylistTrackRequest req) {
        UUID userId = currentUserId();
        return playlistService.addTrack(userId, id, req.trackId())
                .map(pt -> Response.status(201).entity(PlaylistTrackResponse.from(pt)).build());
    }

    @DELETE
    @Path("/{id}/tracks/{trackId}")
    @Operation(summary = "Remove track from playlist", description = "Removes a track from a playlist.")
    @APIResponse(responseCode = "204", description = "Track removed")
    @APIResponse(responseCode = "401", description = "Missing or invalid JWT")
    @APIResponse(responseCode = "403", description = "Playlist belongs to another user")
    @APIResponse(responseCode = "404", description = "Playlist or track not found")
    public Uni<Response> removeTrack(
            @Parameter(description = "ID of the playlist") @PathParam("id") UUID id,
            @Parameter(description = "ID of the track") @PathParam("trackId") UUID trackId) {
        UUID userId = currentUserId();
        return playlistService.removeTrack(userId, id, trackId)
                .map(v -> Response.noContent().build());
    }

    @PUT
    @Path("/{id}/tracks/reorder")
    @Operation(summary = "Reorder tracks in playlist", description = "Changes the order of tracks in a playlist.")
    @APIResponse(responseCode = "204", description = "Tracks reordered")
    @APIResponse(responseCode = "401", description = "Missing or invalid JWT")
    @APIResponse(responseCode = "403", description = "Playlist belongs to another user")
    @APIResponse(responseCode = "404", description = "Playlist not found")
    public Uni<Response> reorder(
            @Parameter(description = "ID of the playlist") @PathParam("id") UUID id,
            @Valid ReorderRequest req) {
        UUID userId = currentUserId();
        return playlistService.reorder(userId, id, req.tracks())
                .map(v -> Response.noContent().build());
    }

    @ServerExceptionMapper
    public Response handlePlaylistException(PlaylistException e) {
        return switch (e.error) {
            case PlaylistError.PlaylistNotFound x ->
                    Response.status(404).entity(new ErrorResponse("PLAYLIST_NOT_FOUND", "Playlist not found")).build();
            case PlaylistError.TrackNotFound x ->
                    Response.status(404).entity(new ErrorResponse("TRACK_NOT_FOUND", "Track not found")).build();
            case PlaylistError.TrackAlreadyInPlaylist x ->
                    Response.status(409).entity(new ErrorResponse("TRACK_ALREADY_IN_PLAYLIST", "Track already in playlist")).build();
            case PlaylistError.TrackNotInPlaylist x ->
                    Response.status(404).entity(new ErrorResponse("TRACK_NOT_IN_PLAYLIST", "Track not in playlist")).build();
            case PlaylistError.Forbidden x ->
                    Response.status(403).entity(new ErrorResponse("FORBIDDEN", "Forbidden")).build();
        };
    }

    private UUID currentUserId() {
        return UUID.fromString(jwt.getSubject());
    }

    private String currentUsername() {
        return jwt.getName();
    }
}
