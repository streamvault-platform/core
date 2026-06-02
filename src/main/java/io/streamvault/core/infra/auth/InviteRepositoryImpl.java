package io.streamvault.core.infra.auth;

import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import io.streamvault.core.domain.auth.InviteLink;
import io.streamvault.core.domain.auth.InviteRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class InviteRepositoryImpl implements InviteRepository, PanacheRepositoryBase<InviteLink, UUID> {

    @Override
    public Uni<Optional<InviteLink>> findByToken(String token) {
        return find("token", token).firstResult().map(Optional::ofNullable);
    }

    @Override
    public Uni<Optional<InviteLink>> findInviteById(UUID id) {
        return find("id", id).firstResult().map(Optional::ofNullable);
    }

    @Override
    public Uni<List<InviteLink>> findActive() {
        return find("usedAt IS NULL AND (expiresAt IS NULL OR expiresAt > ?1)", OffsetDateTime.now()).list();
    }

    @Override
    public Uni<InviteLink> persist(InviteLink invite) {
        return persistAndFlush(invite);
    }

    @Override
    public Uni<InviteLink> update(InviteLink invite) {
        return getSession().flatMap(s -> s.merge(invite));
    }

    @Override
    public Uni<Boolean> deleteById(UUID id) {
        return delete("id", id).map(count -> count > 0);
    }

    @Override
    public Uni<Long> deleteExpiredAndUsed() {
        OffsetDateTime graceCutoff = OffsetDateTime.now().minusDays(7);
        return delete(
                "usedAt IS NOT NULL AND usedAt < ?1 OR (expiresAt IS NOT NULL AND expiresAt < ?1)",
                graceCutoff);
    }
}
