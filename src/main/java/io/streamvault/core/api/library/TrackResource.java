package io.streamvault.core.api.library;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import io.streamvault.core.api.library.dto.AlbumResponse;
import io.streamvault.core.api.library.dto.ArtistResponse;
import io.streamvault.core.api.library.dto.TrackResponse;
import io.streamvault.core.domain.library.AlbumRepository;
import io.streamvault.core.domain.library.ArtistRepository;
import io.streamvault.core.domain.library.TrackRepository;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/library")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Library — Browse", description = "Browse the shared catalog of tracks, artists and albums")
public class TrackResource {

    @Inject TrackRepository tracks;
    @Inject ArtistRepository artists;
    @Inject AlbumRepository albums;

    @GET
    @Path("/tracks")
    @Operation(summary = "List all tracks", description = "Returns a paginated list of all tracks in the shared catalog.")
    @APIResponse(responseCode = "200", description = "Page of tracks")
    @APIResponse(responseCode = "401", description = "Missing or invalid JWT")
    public Uni<List<TrackResponse>> listTracks(
            @Parameter(description = "Zero-based page index") @QueryParam("page") @DefaultValue("0") int page,
            @Parameter(description = "Page size (max 200)") @QueryParam("size") @DefaultValue("50") int size) {
        return Panache.withTransaction(() -> tracks.listAll(page, size))
                .map(list -> list.stream().map(TrackResponse::from).toList());
    }

    @GET
    @Path("/artists")
    @Operation(summary = "List all artists", description = "Returns a paginated list of all artists in the shared catalog.")
    @APIResponse(responseCode = "200", description = "Page of artists")
    @APIResponse(responseCode = "401", description = "Missing or invalid JWT")
    public Uni<List<ArtistResponse>> listArtists(
            @Parameter(description = "Zero-based page index") @QueryParam("page") @DefaultValue("0") int page,
            @Parameter(description = "Page size (max 200)") @QueryParam("size") @DefaultValue("50") int size) {
        return Panache.withTransaction(() -> artists.listAll(page, size))
                .map(list -> list.stream().map(ArtistResponse::from).toList());
    }

    @GET
    @Path("/albums")
    @Operation(summary = "List all albums", description = "Returns a paginated list of all albums in the shared catalog.")
    @APIResponse(responseCode = "200", description = "Page of albums")
    @APIResponse(responseCode = "401", description = "Missing or invalid JWT")
    public Uni<List<AlbumResponse>> listAlbums(
            @Parameter(description = "Zero-based page index") @QueryParam("page") @DefaultValue("0") int page,
            @Parameter(description = "Page size (max 200)") @QueryParam("size") @DefaultValue("50") int size) {
        return Panache.withTransaction(() -> albums.listAll(page, size))
                .map(list -> list.stream().map(AlbumResponse::from).toList());
    }
}
