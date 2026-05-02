package io.streamvault.core.infra.auth;

import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import io.streamvault.core.domain.auth.User;
import io.streamvault.core.domain.auth.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;

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
    public Uni<User> persist(User user) {
        return persistAndFlush(user);
    }
}
