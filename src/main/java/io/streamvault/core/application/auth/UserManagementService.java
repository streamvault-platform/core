package io.streamvault.core.application.auth;

import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import io.streamvault.core.domain.auth.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class UserManagementService {

    private static final Logger LOG = LoggerFactory.getLogger(UserManagementService.class);

    @Inject UserRepository users;

    @WithSession
    public Uni<List<User>> listUsers(int page, int size) {
        return users.findAll(page, size);
    }

    @WithSession
    public Uni<Long> countUsers() {
        return users.countAll();
    }

    public Uni<User> createUser(String username, String password, Role role) {
        return Panache.withTransaction(() ->
                users.findByUsername(username).flatMap(existing -> {
                    if (existing.isPresent()) {
                        return Uni.createFrom().failure(
                                new AuthException(new AuthError.UsernameAlreadyTaken()));
                    }
                    var user = new User();
                    user.username = username;
                    user.passwordHash = BcryptUtil.bcryptHash(password);
                    user.role = role;
                    return users.persist(user)
                            .invoke(u -> LOG.info("action=create_user userId={} username={} role={}", u.id, u.username, u.role));
                }));
    }

    public Uni<User> changeRole(UUID userId, Role newRole) {
        return Panache.withTransaction(() ->
                users.findUserById(userId).flatMap(opt -> {
                    if (opt.isEmpty()) {
                        return Uni.createFrom().failure(
                                new UserManagementException(new UserManagementError.UserNotFound(userId)));
                    }
                    var user = opt.get();
                    if (user.role == Role.ADMIN && newRole != Role.ADMIN) {
                        return users.countByRole(Role.ADMIN).flatMap(adminCount -> {
                            if (adminCount <= 1) {
                                return Uni.createFrom().failure(
                                        new UserManagementException(new UserManagementError.LastAdminProtected()));
                            }
                            user.role = newRole;
                            return users.update(user)
                                    .invoke(u -> LOG.info("action=change_role userId={} newRole={}", u.id, u.role));
                        });
                    }
                    user.role = newRole;
                    return users.update(user)
                            .invoke(u -> LOG.info("action=change_role userId={} newRole={}", u.id, u.role));
                }));
    }

    public Uni<Void> deleteUser(UUID userId) {
        return Panache.withTransaction(() ->
                users.findUserById(userId).flatMap(opt -> {
                    if (opt.isEmpty()) {
                        return Uni.createFrom().failure(
                                new UserManagementException(new UserManagementError.UserNotFound(userId)));
                    }
                    var user = opt.get();
                    if (user.role == Role.ADMIN) {
                        return users.countByRole(Role.ADMIN).flatMap(adminCount -> {
                            if (adminCount <= 1) {
                                return Uni.createFrom().failure(
                                        new UserManagementException(new UserManagementError.LastAdminProtected()));
                            }
                            return doDelete(userId);
                        });
                    }
                    return doDelete(userId);
                }));
    }

    private Uni<Void> doDelete(UUID userId) {
        return users.deleteById(userId)
                .invoke(ignored -> LOG.info("action=delete_user userId={}", userId))
                .replaceWithVoid();
    }
}
