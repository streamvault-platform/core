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

import java.util.List;

@Path("/library")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class TrackResource {

    @Inject TrackRepository tracks;
    @Inject ArtistRepository artists;
    @Inject AlbumRepository albums;

    @GET
    @Path("/tracks")
    public Uni<List<TrackResponse>> listTracks(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("50") int size) {
        return Panache.withTransaction(() -> tracks.listAll(page, size))
                .map(list -> list.stream().map(TrackResponse::from).toList());
    }

    @GET
    @Path("/artists")
    public Uni<List<ArtistResponse>> listArtists(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("50") int size) {
        return Panache.withTransaction(() -> artists.listAll(page, size))
                .map(list -> list.stream().map(ArtistResponse::from).toList());
    }

    @GET
    @Path("/albums")
    public Uni<List<AlbumResponse>> listAlbums(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("50") int size) {
        return Panache.withTransaction(() -> albums.listAll(page, size))
                .map(list -> list.stream().map(AlbumResponse::from).toList());
    }
}
