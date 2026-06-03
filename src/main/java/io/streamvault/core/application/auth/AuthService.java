package io.streamvault.core.application.auth;

import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import io.streamvault.core.domain.auth.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.UUID;

@ApplicationScoped
public class AuthService {

    private static final Logger LOG = LoggerFactory.getLogger(AuthService.class);

    @Inject
    UserRepository users;
    @Inject
    RefreshTokenRepository refreshTokens;
    @Inject
    TokenService tokenService;
    @Inject
    InviteService inviteService;

    @ConfigProperty(name = "streamvault.open-registration", defaultValue = "false")
    boolean openRegistration;

    @WithSession
    public Uni<Boolean> isConfigured() {
        return users.hasAdminAccount();
    }

    public Uni<TokenResponse> register(String username, String password, String inviteToken) {
        return Panache.withTransaction(() -> users.hasAdminAccount().flatMap(configured -> {
            if (!configured) {
                // First user — always ADMIN, no invite needed
                return createUserEntity(username, password, Role.ADMIN).flatMap(this::issueTokenPair);
            }
            if (openRegistration) {
                return createUserEntity(username, password, Role.USER).flatMap(this::issueTokenPair);
            }
            // Closed registration — require a valid invite
            if (inviteToken == null || inviteToken.isBlank()) {
                return Uni.createFrom().<TokenResponse>failure(new AuthException(new AuthError.RegistrationClosed()));
            }
            return inviteService.findByToken(inviteToken).flatMap(opt -> {
                if (opt.isEmpty() || !opt.get().isValid()) {
                    return Uni.createFrom().<TokenResponse>failure(new AuthException(new AuthError.InvalidInvite()));
                }
                var invite = opt.get();
                return createUserEntity(username, password, Role.USER).flatMap(u -> {
                    invite.usedAt = OffsetDateTime.now();
                    invite.usedById = u.id;
                    return inviteService.update(invite).flatMap(ignored -> issueTokenPair(u));
                });
            });
        }));
    }

    private Uni<User> createUserEntity(String username, String password, Role role) {
        return users.findByUsername(username).flatMap(existing -> {
            if (existing.isPresent()) {
                return Uni.createFrom().<User>failure(new AuthException(new AuthError.UsernameAlreadyTaken()));
            }
            var user = new User();
            user.username = username;
            user.passwordHash = BcryptUtil.bcryptHash(password);
            user.role = role;
            return users.persist(user)
                    .invoke(u -> LOG.info("action=register userId={} username={} role={}", u.id, u.username, u.role));
        });
    }

    public Uni<TokenResponse> login(String username, String password) {
        return Panache.withTransaction(() -> users.findByUsername(username).flatMap(opt -> {
            if (opt.isEmpty() || !BcryptUtil.matches(password, opt.get().passwordHash)) {
                LOG.warn("action=login result=rejected username={}", username);
                return Uni.createFrom().failure(
                        new AuthException(new AuthError.InvalidCredentials()));
            }
            var user = opt.get();
            return refreshTokens.deleteAllForUser(user.id)
                    .flatMap(ignored -> issueTokenPair(user));
        }));
    }

    public Uni<TokenResponse> refresh(String rawToken) {
        return Panache.withTransaction(() -> {
            String hash = tokenService.hashToken(rawToken);
            return refreshTokens.findByTokenHash(hash).flatMap(opt -> {
                if (opt.isEmpty()) {
                    LOG.warn("action=token_refresh result=rejected reason=not_found");
                    return Uni.createFrom().failure(
                            new AuthException(new AuthError.TokenNotFound()));
                }
                var rt = opt.get();
                if (rt.expiresAt.isBefore(OffsetDateTime.now())) {
                    LOG.warn("action=token_refresh result=rejected reason=expired userId={}", rt.user.id);
                    return refreshTokens.deleteById(rt.id)
                            .flatMap(ignored -> Uni.createFrom().failure(
                                    new AuthException(new AuthError.TokenExpired())));
                }
                return refreshTokens.deleteById(rt.id)
                        .flatMap(ignored -> issueTokenPair(rt.user));
            });
        });
    }

    public Uni<Void> logout(UUID userId) {
        return Panache.withTransaction(() -> refreshTokens.deleteAllForUser(userId).replaceWithVoid());
    }

    private Uni<TokenResponse> issueTokenPair(User user) {
        String rawRefresh = tokenService.generateRefreshToken();
        var rt = new RefreshToken();
        rt.user = user;
        rt.tokenHash = tokenService.hashToken(rawRefresh);
        rt.expiresAt = tokenService.refreshTokenExpiry();
        return refreshTokens.persist(rt)
                .map(saved -> new TokenResponse(
                        tokenService.generateAccessToken(user),
                        rawRefresh));
    }
}
