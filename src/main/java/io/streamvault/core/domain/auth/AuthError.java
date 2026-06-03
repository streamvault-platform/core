package io.streamvault.core.domain.auth;

public sealed interface AuthError
        permits AuthError.UsernameAlreadyTaken,
                AuthError.InvalidCredentials,
                AuthError.TokenExpired,
                AuthError.TokenNotFound,
                AuthError.AlreadyConfigured,
                AuthError.RegistrationClosed,
                AuthError.InvalidInvite {

    record UsernameAlreadyTaken() implements AuthError {}
    record InvalidCredentials() implements AuthError {}
    record TokenExpired() implements AuthError {}
    record TokenNotFound() implements AuthError {}
    record AlreadyConfigured() implements AuthError {}
    record RegistrationClosed() implements AuthError {}
    record InvalidInvite() implements AuthError {}
}
