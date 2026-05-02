package io.streamvault.core.domain.auth;

public sealed interface AuthError
        permits AuthError.FirstAdminAlreadyExists,
                AuthError.InvalidCredentials,
                AuthError.TokenExpired,
                AuthError.TokenNotFound {

    record FirstAdminAlreadyExists() implements AuthError {}
    record InvalidCredentials() implements AuthError {}
    record TokenExpired() implements AuthError {}
    record TokenNotFound() implements AuthError {}
}
