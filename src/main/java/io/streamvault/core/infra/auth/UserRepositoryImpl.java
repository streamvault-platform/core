package io.streamvault.core.infra.auth;

import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import io.streamvault.core.domain.auth.Role;
import io.streamvault.core.domain.auth.User;
import io.streamvault.core.domain.auth.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserRepositoryImpl implements UserRepository, PanacheRepositoryBase<User, UUID> {

    @Override
    public Uni<Optional<User>> findByUsername(String username) {
        return find("username", username).firstResult().map(Optional::ofNullable);
    }

    @Override
    public Uni<Long> countAll() {
        return count();
    }

    @Override
    public Uni<Boolean> hasAdminAccount() {
        return count("role", Role.ADMIN).map(c -> c > 0);
    }

    @Override
    public Uni<Optional<User>> findUserById(UUID id) {
        return find("id", id).firstResult().map(Optional::ofNullable);
    }

    @Override
    public Uni<List<User>> findAll(int page, int size) {
        return findAll().page(page, size).list();
    }

    @Override
    public Uni<Long> countByRole(Role role) {
        return count("role", role);
    }

    @Override
    public Uni<User> persist(User user) {
        return persistAndFlush(user);
    }

    @Override
    public Uni<User> update(User user) {
        return getSession().flatMap(s -> s.merge(user));
    }

    @Override
    public Uni<Boolean> deleteById(UUID id) {
        return delete("id", id).map(count -> count > 0);
    }
}
