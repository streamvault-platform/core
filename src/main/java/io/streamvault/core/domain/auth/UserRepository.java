package io.streamvault.core.domain.auth;

import io.smallrye.mutiny.Uni;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    Uni<Optional<User>> findByUsername(String username);
    Uni<Optional<User>> findUserById(UUID id);
    Uni<List<User>> findAll(int page, int size);
    Uni<Long> countAll();
    Uni<Boolean> hasAdminAccount();
    Uni<Long> countByRole(Role role);
    Uni<User> persist(User user);
    Uni<User> update(User user);
    Uni<Boolean> deleteById(UUID id);
}
