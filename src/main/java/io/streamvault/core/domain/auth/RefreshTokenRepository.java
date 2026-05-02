package io.streamvault.core.domain.auth;

import io.smallrye.mutiny.Uni;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {
    Uni<Optional<RefreshToken>> findByTokenHash(String tokenHash);
    Uni<Long> deleteAllForUser(UUID userId);
    Uni<RefreshToken> persist(RefreshToken token);
    Uni<Boolean> deleteById(UUID id);
}
