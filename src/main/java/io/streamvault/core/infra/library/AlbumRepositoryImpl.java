package io.streamvault.core.infra.library;

import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import io.streamvault.core.domain.library.Album;
import io.streamvault.core.domain.library.AlbumRepository;
import io.streamvault.core.domain.library.Artist;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AlbumRepositoryImpl implements AlbumRepository, PanacheRepositoryBase<Album, UUID> {

    @Inject
    Mutiny.SessionFactory sf;

    private static final String SEARCH_SQL = """
            SELECT a.id, a.title, a.artist_id, ar.name AS artist_name, a.year
            FROM albums a
            LEFT JOIN artists ar ON a.artist_id = ar.id
            WHERE a.search_vector @@ websearch_to_tsquery('simple', :q)
               OR word_similarity(:q, a.title) > 0.3
            ORDER BY (
                ts_rank_cd(a.search_vector, websearch_to_tsquery('simple', :q)) +
                word_similarity(:q, a.title)
            ) DESC
            """;

    @Override
    public Uni<Optional<Album>> findAlbumById(UUID id) {
        return find("SELECT a FROM Album a LEFT JOIN FETCH a.artist WHERE a.id = ?1", id)
                .firstResult().map(Optional::ofNullable);
    }

    @Override
    public Uni<Optional<Album>> findByTitleAndArtist(String title, UUID artistId) {
        return find("title = ?1 and artist.id = ?2", title, artistId)
                .firstResult()
                .map(Optional::ofNullable);
    }

    @Override
    public Uni<List<Album>> listAll(int page, int size) {
        return find("SELECT a FROM Album a LEFT JOIN FETCH a.artist")
                .page(page, size).list();
    }

    @Override
    public Uni<List<Album>> listByArtist(UUID artistId, int page, int size) {
        return find("SELECT a FROM Album a LEFT JOIN FETCH a.artist WHERE a.artist.id = ?1", artistId)
                .page(page, size).list();
    }

    @Override
    public Uni<List<Album>> search(String q, int page, int size) {
        return sf.withSession(session ->
                session.createNativeQuery(SEARCH_SQL)
                        .setParameter("q", q)
                        .setFirstResult(page * size)
                        .setMaxResults(size)
                        .getResultList()
        ).map(rows -> rows.stream().map(row -> toAlbum((Object[]) row)).toList());
    }

    private static Album toAlbum(Object[] r) {
        Album album = new Album();
        album.id = (UUID) r[0];
        album.title = (String) r[1];
        album.year = (Integer) r[4];
        if (r[2] != null) {
            Artist artist = new Artist();
            artist.id = (UUID) r[2];
            artist.name = (String) r[3];
            album.artist = artist;
        }
        return album;
    }

    @Override
    public Uni<Long> countAll() {
        return count();
    }

    @Override
    public Uni<Album> persist(Album album) {
        return persistAndFlush(album);
    }

    @Override
    public Uni<Album> update(Album album) {
        return sf.withTransaction(session -> session.merge(album));
    }

    @Override
    public Uni<Void> delete(UUID id) {
        return delete("id = ?1", id).replaceWithVoid();
    }
}
