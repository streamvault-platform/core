package io.streamvault.core.application.auth;

import io.streamvault.core.domain.auth.AuthError;

public class AuthException extends RuntimeException {

    private final AuthError error;

    public AuthException(AuthError error) {
        super(error.toString());
        this.error = error;
    }

    public AuthError error() {
        return error;
    }
}
