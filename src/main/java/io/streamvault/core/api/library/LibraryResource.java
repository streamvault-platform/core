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
import java.util.UUID;

import jakarta.ws.rs.NotFoundException;

@Path("/library")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Library — Browse", description = "Browse the shared catalog of tracks, artists and albums")
public class LibraryResource {

    @Inject TrackRepository tracks;
    @Inject ArtistRepository artists;
    @Inject AlbumRepository albums;

    @GET
    @Path("/artists/{id}")
    @Operation(summary = "Get artist by ID")
    @APIResponse(responseCode = "200", description = "Artist")
    @APIResponse(responseCode = "401", description = "Missing or invalid JWT")
    @APIResponse(responseCode = "404", description = "Artist not found")
    public Uni<ArtistResponse> getArtist(@PathParam("id") UUID id) {
        return Panache.withTransaction(() -> artists.findArtistById(id))
                .map(opt -> opt.map(ArtistResponse::from).orElseThrow(NotFoundException::new));
    }

    @GET
    @Path("/albums/{id}")
    @Operation(summary = "Get album by ID")
    @APIResponse(responseCode = "200", description = "Album")
    @APIResponse(responseCode = "401", description = "Missing or invalid JWT")
    @APIResponse(responseCode = "404", description = "Album not found")
    public Uni<AlbumResponse> getAlbum(@PathParam("id") UUID id) {
        return Panache.withTransaction(() -> albums.findAlbumById(id))
                .map(opt -> opt.map(AlbumResponse::from).orElseThrow(NotFoundException::new));
    }

    @GET
    @Path("/tracks/{id}")
    @Operation(summary = "Get track by ID")
    @APIResponse(responseCode = "200", description = "Track")
    @APIResponse(responseCode = "401", description = "Missing or invalid JWT")
    @APIResponse(responseCode = "404", description = "Track not found")
    public Uni<TrackResponse> getTrack(@PathParam("id") UUID id) {
        return Panache.withTransaction(() -> tracks.findTrackByIdWithDetails(id))
                .map(opt -> opt.map(TrackResponse::from).orElseThrow(NotFoundException::new));
    }

    @GET
    @Path("/tracks")
    @Operation(summary = "List or search tracks")
    @APIResponse(responseCode = "200", description = "Page of tracks")
    @APIResponse(responseCode = "401", description = "Missing or invalid JWT")
    public Uni<List<TrackResponse>> listTracks(
            @Parameter(description = "Full-text search query (tsvector + trigram fuzzy)") @QueryParam("q") String q,
            @Parameter(description = "Filter by album UUID") @QueryParam("albumId") UUID albumId,
            @Parameter(description = "Filter by artist UUID") @QueryParam("artistId") UUID artistId,
            @Parameter(description = "Zero-based page index") @QueryParam("page") @DefaultValue("0") int page,
            @Parameter(description = "Page size (max 200)") @QueryParam("size") @DefaultValue("20") int size) {

        if (q != null && !q.isBlank()) {
            return tracks.search(q.strip(), page, size)
                    .map(list -> list.stream().map(TrackResponse::from).toList());
        }
        if (albumId != null) {
            return Panache.withTransaction(() -> tracks.listByAlbum(albumId, page, size))
                    .map(list -> list.stream().map(TrackResponse::from).toList());
        }
        if (artistId != null) {
            return Panache.withTransaction(() -> tracks.listByArtist(artistId, page, size))
                    .map(list -> list.stream().map(TrackResponse::from).toList());
        }
        return Panache.withTransaction(() -> tracks.listAll(page, size))
                .map(list -> list.stream().map(TrackResponse::from).toList());
    }

    @GET
    @Path("/artists")
    @Operation(summary = "List or search artists")
    @APIResponse(responseCode = "200", description = "Page of artists")
    @APIResponse(responseCode = "401", description = "Missing or invalid JWT")
    public Uni<List<ArtistResponse>> listArtists(
            @Parameter(description = "Full-text search query (tsvector + trigram fuzzy)") @QueryParam("q") String q,
            @Parameter(description = "Zero-based page index") @QueryParam("page") @DefaultValue("0") int page,
            @Parameter(description = "Page size (max 200)") @QueryParam("size") @DefaultValue("20") int size) {

        if (q != null && !q.isBlank()) {
            return artists.search(q.strip(), page, size)
                    .map(list -> list.stream().map(ArtistResponse::from).toList());
        }
        return Panache.withTransaction(() -> artists.listAll(page, size))
                .map(list -> list.stream().map(ArtistResponse::from).toList());
    }

    @GET
    @Path("/albums")
    @Operation(summary = "List or search albums")
    @APIResponse(responseCode = "200", description = "Page of albums")
    @APIResponse(responseCode = "401", description = "Missing or invalid JWT")
    public Uni<List<AlbumResponse>> listAlbums(
            @Parameter(description = "Full-text search query (tsvector + trigram fuzzy)") @QueryParam("q") String q,
            @Parameter(description = "Filter by artist UUID") @QueryParam("artistId") UUID artistId,
            @Parameter(description = "Zero-based page index") @QueryParam("page") @DefaultValue("0") int page,
            @Parameter(description = "Page size (max 200)") @QueryParam("size") @DefaultValue("20") int size) {

        if (q != null && !q.isBlank()) {
            return albums.search(q.strip(), page, size)
                    .map(list -> list.stream().map(AlbumResponse::from).toList());
        }
        if (artistId != null) {
            return Panache.withTransaction(() -> albums.listByArtist(artistId, page, size))
                    .map(list -> list.stream().map(AlbumResponse::from).toList());
        }
        return Panache.withTransaction(() -> albums.listAll(page, size))
                .map(list -> list.stream().map(AlbumResponse::from).toList());
    }
}
