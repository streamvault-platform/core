package io.streamvault.core.application.auth;

import io.streamvault.core.domain.auth.UserManagementError;

public class UserManagementException extends RuntimeException {

    private final UserManagementError error;

    public UserManagementException(UserManagementError error) {
        super(error.toString());
        this.error = error;
    }

    public UserManagementError error() {
        return error;
    }
}
