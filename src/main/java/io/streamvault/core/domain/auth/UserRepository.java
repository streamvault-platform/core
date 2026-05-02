package io.streamvault.core.domain.auth;

import io.smallrye.mutiny.Uni;
import java.util.Optional;

public interface UserRepository {
    Uni<Optional<User>> findByUsername(String username);
    Uni<Long> countAll();
    Uni<User> persist(User user);
}
