package io.streamvault.core.api.admin;

import io.smallrye.mutiny.Uni;
import io.streamvault.core.api.admin.dto.ChangeRoleRequest;
import io.streamvault.core.api.admin.dto.CreateUserRequest;
import io.streamvault.core.api.admin.dto.UserResponse;
import io.streamvault.core.api.common.ErrorResponse;
import io.streamvault.core.application.auth.AuthException;
import io.streamvault.core.application.auth.UserManagementException;
import io.streamvault.core.application.auth.UserManagementService;
import io.streamvault.core.domain.auth.AuthError;
import io.streamvault.core.domain.auth.UserManagementError;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.util.UUID;

@Path("/admin/users")
@RolesAllowed("ADMIN")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Admin — Users", description = "User management: create, list, change role, delete")
public class AdminUserResource {

    @Inject UserManagementService userManagement;

    @GET
    @Operation(summary = "List users")
    @APIResponse(responseCode = "200", description = "Paginated user list")
    public Uni<Response> list(
            @Parameter(description = "Zero-based page number") @QueryParam("page") @DefaultValue("0") int page,
            @Parameter(description = "Page size") @QueryParam("size") @DefaultValue("20") int size) {
        return userManagement.listUsers(page, size)
                .map(users -> users.stream().map(UserResponse::from).toList())
                .flatMap(list -> userManagement.countUsers()
                        .map(total -> Response.ok(list)
                                .header("X-Total-Count", total)
                                .build()));
    }

    @POST
    @Operation(summary = "Create user")
    @APIResponse(responseCode = "201", description = "User created")
    @APIResponse(responseCode = "409", description = "Username already taken")
    public Uni<Response> create(@Valid CreateUserRequest req) {
        return userManagement.createUser(req.username(), req.password(), req.role())
                .map(user -> Response.status(201).entity(UserResponse.from(user)).build());
    }

    @PATCH
    @Path("/{id}/role")
    @Operation(summary = "Change user role")
    @APIResponse(responseCode = "200", description = "Role updated")
    @APIResponse(responseCode = "404", description = "User not found")
    @APIResponse(responseCode = "409", description = "Cannot demote the last admin")
    public Uni<Response> changeRole(
            @Parameter(description = "User ID") @PathParam("id") UUID id,
            @Valid ChangeRoleRequest req) {
        return userManagement.changeRole(id, req.role())
                .map(user -> Response.ok(UserResponse.from(user)).build());
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete user")
    @APIResponse(responseCode = "204", description = "User deleted")
    @APIResponse(responseCode = "404", description = "User not found")
    @APIResponse(responseCode = "409", description = "Cannot delete the last admin")
    public Uni<Response> delete(@Parameter(description = "User ID") @PathParam("id") UUID id) {
        return userManagement.deleteUser(id)
                .map(ignored -> Response.noContent().build());
    }

    @ServerExceptionMapper
    public Response handleUserManagementException(UserManagementException e) {
        return switch (e.error()) {
            case UserManagementError.UserNotFound x ->
                    Response.status(404).entity(new ErrorResponse("USER_NOT_FOUND", "User " + x.userId() + " not found")).build();
            case UserManagementError.LastAdminProtected x ->
                    Response.status(409).entity(new ErrorResponse("LAST_ADMIN", "Cannot modify or delete the last admin account")).build();
        };
    }

    @ServerExceptionMapper
    public Response handleAuthException(AuthException e) {
        return switch (e.error()) {
            case AuthError.UsernameAlreadyTaken x ->
                    Response.status(409).entity(new ErrorResponse("USERNAME_TAKEN", "Username already taken")).build();
            default ->
                    Response.status(500).entity(new ErrorResponse("INTERNAL_ERROR", "Unexpected error")).build();
        };
    }
}
