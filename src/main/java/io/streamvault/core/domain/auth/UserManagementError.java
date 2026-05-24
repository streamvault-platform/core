package io.streamvault.core.domain.auth;

import java.util.UUID;

public sealed interface UserManagementError
        permits UserManagementError.UserNotFound,
                UserManagementError.LastAdminProtected {

    record UserNotFound(UUID userId) implements UserManagementError {}
    record LastAdminProtected() implements UserManagementError {}
}
