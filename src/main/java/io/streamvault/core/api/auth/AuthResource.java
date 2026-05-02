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
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.util.UUID;

@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject AuthService authService;
    @Inject JsonWebToken jwt;

    @POST
    @Path("/register")
    public Uni<Response> register(@Valid RegisterRequest req) {
        return authService.registerFirstAdmin(req.username(), req.password())
                .map(token -> Response.status(201).entity(token).build());
    }

    @POST
    @Path("/login")
    public Uni<Response> login(@Valid LoginRequest req) {
        return authService.login(req.username(), req.password())
                .map(token -> Response.ok(token).build());
    }

    @POST
    @Path("/refresh")
    public Uni<Response> refresh(@Valid RefreshRequest req) {
        return authService.refresh(req.refreshToken())
                .map(token -> Response.ok(token).build());
    }

    @POST
    @Path("/logout")
    @Authenticated
    @Consumes(MediaType.WILDCARD)
    public Uni<Response> logout() {
        UUID userId = UUID.fromString(jwt.getSubject());
        return authService.logout(userId)
                .map(ignored -> Response.noContent().build());
    }

    @ServerExceptionMapper
    public Response handleAuthException(AuthException e) {
        return switch (e.error()) {
            case AuthError.FirstAdminAlreadyExists x ->
                    Response.status(409).entity(new ErrorResponse("ADMIN_EXISTS", "Admin user already registered")).build();
            case AuthError.InvalidCredentials x ->
                    Response.status(401).entity(new ErrorResponse("INVALID_CREDENTIALS", "Invalid username or password")).build();
            case AuthError.TokenExpired x ->
                    Response.status(401).entity(new ErrorResponse("TOKEN_EXPIRED", "Refresh token has expired")).build();
            case AuthError.TokenNotFound x ->
                    Response.status(401).entity(new ErrorResponse("TOKEN_NOT_FOUND", "Invalid refresh token")).build();
        };
    }
}
