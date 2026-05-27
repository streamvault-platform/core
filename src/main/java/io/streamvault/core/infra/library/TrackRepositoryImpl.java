package io.streamvault.core.infra.library;

import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import io.streamvault.core.domain.library.Album;
import io.streamvault.core.domain.library.Artist;
import io.streamvault.core.domain.library.Track;
import io.streamvault.core.domain.library.TrackRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TrackRepositoryImpl implements TrackRepository, PanacheRepositoryBase<Track, UUID> {

    @Inject
    Mutiny.SessionFactory sf;

    private static final String SEARCH_SQL = """
            SELECT t.id, t.title, t.file_path,
                   t.artist_id, ar.name AS artist_name,
                   t.album_id, al.title AS album_title,
                   t.track_number, t.disc_number, t.duration_ms,
                   t.genre, t.year, t.mime_type
            FROM tracks t
            LEFT JOIN artists ar ON t.artist_id = ar.id
            LEFT JOIN albums  al ON t.album_id  = al.id
            WHERE t.search_vector @@ websearch_to_tsquery('simple', :q)
               OR word_similarity(:q, t.title) > 0.3
            ORDER BY (
                ts_rank_cd(t.search_vector, websearch_to_tsquery('simple', :q)) +
                word_similarity(:q, t.title)
            ) DESC
            """;

    @Override
    public Uni<Optional<Track>> findTrackById(UUID id) {
        return find("id", id).firstResult().map(Optional::ofNullable);
    }

    @Override
    public Uni<Optional<Track>> findTrackByIdWithDetails(UUID id) {
        return find("SELECT t FROM Track t LEFT JOIN FETCH t.artist LEFT JOIN FETCH t.album WHERE t.id = ?1", id)
                .firstResult().map(Optional::ofNullable);
    }

    @Override
    public Uni<Optional<Track>> findByFilePath(String filePath) {
        return find("filePath", filePath).firstResult().map(Optional::ofNullable);
    }

    @Override
    public Uni<List<Track>> listAll(int page, int size) {
        return find("SELECT t FROM Track t LEFT JOIN FETCH t.artist LEFT JOIN FETCH t.album")
                .page(page, size).list();
    }

    @Override
    public Uni<List<Track>> listByAlbum(UUID albumId, int page, int size) {
        return find("SELECT t FROM Track t LEFT JOIN FETCH t.artist LEFT JOIN FETCH t.album WHERE t.album.id = ?1", albumId)
                .page(page, size).list();
    }

    @Override
    public Uni<List<Track>> listByArtist(UUID artistId, int page, int size) {
        return find(
                "SELECT t FROM Track t JOIN FETCH t.artist a LEFT JOIN FETCH t.album " +
                "WHERE a.id = ?1 AND t.album IS NULL",
                artistId
        ).page(page, size).list();
    }

    @Override
    public Uni<List<Track>> search(String q, int page, int size) {
        return sf.withSession(session ->
                session.createNativeQuery(SEARCH_SQL)
                        .setParameter("q", q)
                        .setFirstResult(page * size)
                        .setMaxResults(size)
                        .getResultList()
        ).map(rows -> rows.stream().map(row -> toTrack((Object[]) row)).toList());
    }

    private static Track toTrack(Object[] r) {
        Track track = new Track();
        track.id = (UUID) r[0];
        track.title = (String) r[1];
        track.filePath = (String) r[2];
        track.trackNumber = (Integer) r[7];
        track.discNumber = (Integer) r[8];
        track.durationMs = (Integer) r[9];
        track.genre = (String) r[10];
        track.year = (Integer) r[11];
        track.mimeType = (String) r[12];
        if (r[3] != null) {
            Artist artist = new Artist();
            artist.id = (UUID) r[3];
            artist.name = (String) r[4];
            track.artist = artist;
        }
        if (r[5] != null) {
            Album album = new Album();
            album.id = (UUID) r[5];
            album.title = (String) r[6];
            track.album = album;
        }
        return track;
    }

    @Override
    public Uni<Long> countAll() {
        return count();
    }

    @Override
    public Uni<Track> persist(Track track) {
        return persistAndFlush(track);
    }

    @Override
    public Uni<Track> update(Track track) {
        return persistAndFlush(track);
    }

    @Override
    public Uni<Void> delete(UUID id) {
        return delete("id = ?1", id).replaceWithVoid();
    }
}
