package io.streamvault.core.infra.auth;

import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import io.streamvault.core.domain.auth.RefreshToken;
import io.streamvault.core.domain.auth.RefreshTokenRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class RefreshTokenRepositoryImpl implements RefreshTokenRepository, PanacheRepositoryBase<RefreshToken, UUID> {

    @Override
    public Uni<Optional<RefreshToken>> findByTokenHash(String tokenHash) {
        return find("tokenHash", tokenHash).firstResult().map(Optional::ofNullable);
    }

    @Override
    public Uni<Long> deleteAllForUser(UUID userId) {
        return delete("user.id = ?1", userId);
    }

    @Override
    public Uni<RefreshToken> persist(RefreshToken token) {
        return persistAndFlush(token);
    }

    @Override
    public Uni<Boolean> deleteById(UUID id) {
        return delete("id = ?1", id).map(count -> count > 0);
    }
}
