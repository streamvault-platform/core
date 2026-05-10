package io.streamvault.core.infra.library;

import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import io.streamvault.core.domain.library.Artist;
import io.streamvault.core.domain.library.ArtistRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ArtistRepositoryImpl implements ArtistRepository, PanacheRepositoryBase<Artist, UUID> {

    @Inject
    Mutiny.SessionFactory sf;

    private static final String SEARCH_SQL = """
            SELECT id, name, created_at
            FROM artists
            WHERE search_vector @@ websearch_to_tsquery('simple', :q)
               OR word_similarity(:q, name) > 0.3
            ORDER BY (
                ts_rank_cd(search_vector, websearch_to_tsquery('simple', :q)) +
                word_similarity(:q, name)
            ) DESC
            """;

    @Override
    public Uni<Optional<Artist>> findArtistById(UUID id) {
        return find("id", id).firstResult().map(Optional::ofNullable);
    }

    @Override
    public Uni<Optional<Artist>> findByName(String name) {
        return find("name", name).firstResult().map(Optional::ofNullable);
    }

    @Override
    public Uni<List<Artist>> listAll(int page, int size) {
        return findAll().page(page, size).list();
    }

    @Override
    public Uni<List<Artist>> search(String q, int page, int size) {
        return sf.withSession(session ->
                session.createNativeQuery(SEARCH_SQL, Artist.class)
                        .setParameter("q", q)
                        .setFirstResult(page * size)
                        .setMaxResults(size)
                        .getResultList()
        );
    }

    @Override
    public Uni<Long> countAll() {
        return count();
    }

    @Override
    public Uni<Artist> persist(Artist artist) {
        return persistAndFlush(artist);
    }
}
