package io.streamvault.core.application.auth;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import io.streamvault.core.domain.auth.InviteLink;
import io.streamvault.core.domain.auth.InviteRepository;
import io.streamvault.core.domain.auth.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class InviteService {

    private static final Logger LOG = LoggerFactory.getLogger(InviteService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    @Inject
    InviteRepository invites;

    @Inject
    UserRepository users;

    @ConfigProperty(name = "streamvault.invite.default-expiry-days", defaultValue = "7")
    int defaultExpiryDays;

    public Uni<InviteLink> createInvite(UUID createdByUserId, Integer expiresInDays) {
        return Panache.withTransaction(() ->
                users.findUserById(createdByUserId).flatMap(opt -> {
                    var invite = new InviteLink();
                    invite.token = generateToken();
                    invite.createdBy = opt.orElseThrow();
                    int days = expiresInDays != null ? expiresInDays : defaultExpiryDays;
                    invite.expiresAt = days == 0 ? null : OffsetDateTime.now().plusDays(days);
                    return invites.persist(invite)
                            .invoke(i -> LOG.info("action=invite_created id={} expiresAt={}", i.id, i.expiresAt));
                }));
    }

    @WithSession
    public Uni<List<InviteLink>> listActive() {
        return invites.findActive();
    }

    public Uni<Void> revoke(UUID inviteId) {
        return Panache.withTransaction(() ->
                invites.deleteById(inviteId)
                        .invoke(deleted -> {
                            if (deleted) LOG.info("action=invite_revoked id={}", inviteId);
                            else LOG.warn("action=invite_revoke_not_found id={}", inviteId);
                        })
                        .replaceWithVoid());
    }

    public Uni<InviteLink> update(InviteLink invite) {
        return Panache.withTransaction(() -> invites.update(invite));
    }

    @WithSession
    public Uni<Boolean> isTokenValid(String token) {
        return invites.findByToken(token).map(opt ->
                opt.map(InviteLink::isValid).orElse(false));
    }

    @WithSession
    public Uni<Optional<InviteLink>> findByToken(String token) {
        return invites.findByToken(token);
    }

    @Scheduled(every = "24h", delayed = "10m")
    void cleanupStale() {
        invites.deleteExpiredAndUsed()
                .subscribe().with(
                        count -> { if (count > 0) LOG.info("action=invite_cleanup deleted={}", count); },
                        err -> LOG.error("action=invite_cleanup_failed", err));
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
