package io.streamvault.core.domain.auth;

import io.smallrye.mutiny.Uni;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InviteRepository {
    Uni<Optional<InviteLink>> findByToken(String token);
    Uni<Optional<InviteLink>> findInviteById(UUID id);
    Uni<List<InviteLink>> findActive();
    Uni<InviteLink> persist(InviteLink invite);
    Uni<InviteLink> update(InviteLink invite);
    Uni<Boolean> deleteById(UUID id);
    Uni<Long> deleteExpiredAndUsed();
}
