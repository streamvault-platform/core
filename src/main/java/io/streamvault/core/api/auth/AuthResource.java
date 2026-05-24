package io.streamvault.core.api.auth;

import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import io.streamvault.core.api.auth.dto.LoginRequest;
import io.streamvault.core.api.auth.dto.RefreshRequest;
import io.streamvault.core.api.auth.dto.RegisterRequest;
import io.streamvault.core.api.common.ErrorResponse;
import io.streamvault.core.application.auth.AuthException;
import io.streamvault.core.application.auth.AuthService;
import io.streamvault.core.domain.auth.AuthError;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.util.UUID;

@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Auth", description = "Registration, login, token refresh and logout")
public class AuthResource {

    @Inject AuthService authService;
    @Inject JsonWebToken jwt;

    @POST
    @Path("/register")
    @Operation(summary = "Register", description = "Create the initial admin account on a fresh install. Returns 409 if the server is already configured or the username is taken.")
    @APIResponse(responseCode = "201", description = "Admin account created, tokens returned")
    @APIResponse(responseCode = "409", description = "Server already configured, or username already taken")
    public Uni<Response> register(@Valid RegisterRequest req) {
        return authService.register(req.username(), req.password())
                .map(token -> Response.status(201).entity(token).build());
    }

    @POST
    @Path("/login")
    @Operation(summary = "Login", description = "Authenticate with username and password. Returns a JWT access token and a refresh token.")
    @APIResponse(responseCode = "200", description = "Authenticated — access + refresh tokens returned")
    @APIResponse(responseCode = "401", description = "Invalid credentials")
    public Uni<Response> login(@Valid LoginRequest req) {
        return authService.login(req.username(), req.password())
                .map(token -> Response.ok(token).build());
    }

    @POST
    @Path("/refresh")
    @Operation(summary = "Refresh tokens", description = "Exchange a valid refresh token for a new access token + rotated refresh token.")
    @APIResponse(responseCode = "200", description = "New token pair returned")
    @APIResponse(responseCode = "401", description = "Refresh token not found or expired")
    public Uni<Response> refresh(@Valid RefreshRequest req) {
        return authService.refresh(req.refreshToken())
                .map(token -> Response.ok(token).build());
    }

    @POST
    @Path("/logout")
    @Authenticated
    @Consumes(MediaType.WILDCARD)
    @Operation(summary = "Logout", description = "Revokes all refresh tokens for the authenticated user.")
    @APIResponse(responseCode = "204", description = "Logged out successfully")
    @APIResponse(responseCode = "401", description = "Missing or invalid JWT")
    public Uni<Response> logout() {
        UUID userId = UUID.fromString(jwt.getSubject());
        return authService.logout(userId)
                .map(ignored -> Response.noContent().build());
    }

    @ServerExceptionMapper
    public Response handleAuthException(AuthException e) {
        return switch (e.error()) {
            case AuthError.UsernameAlreadyTaken x ->
                    Response.status(409).entity(new ErrorResponse("USERNAME_TAKEN", "Username already taken")).build();
            case AuthError.AlreadyConfigured x ->
                    Response.status(409).entity(new ErrorResponse("SERVER_CONFIGURED", "Server already has an admin account — use the admin API to create users")).build();
            case AuthError.InvalidCredentials x ->
                    Response.status(401).entity(new ErrorResponse("INVALID_CREDENTIALS", "Invalid username or password")).build();
            case AuthError.TokenExpired x ->
                    Response.status(401).entity(new ErrorResponse("TOKEN_EXPIRED", "Refresh token has expired")).build();
            case AuthError.TokenNotFound x ->
                    Response.status(401).entity(new ErrorResponse("TOKEN_NOT_FOUND", "Invalid refresh token")).build();
        };
    }
}
