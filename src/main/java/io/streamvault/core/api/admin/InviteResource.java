package io.streamvault.core.api.admin;

import io.smallrye.mutiny.Uni;
import io.streamvault.core.api.admin.dto.CreateInviteRequest;
import io.streamvault.core.api.admin.dto.InviteResponse;
import io.streamvault.core.application.auth.InviteService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.UUID;

@Path("/admin/invites")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
@Tag(name = "Admin - Invites", description = "Manage single-use invite links for new user registration")
public class InviteResource {

    @Inject
    InviteService inviteService;

    @Inject
    JsonWebToken jwt;

    @POST
    @Operation(summary = "Create invite", description = "Generate a single-use invite link. Pass expiresInDays=0 for a link that never expires; omit to use the server default.")
    @APIResponse(responseCode = "201", description = "Invite created")
    public Uni<Response> create(CreateInviteRequest req) {
        UUID callerId = UUID.fromString(jwt.getSubject());
        Integer days = req != null ? req.expiresInDays() : null;
        return inviteService.createInvite(callerId, days)
                .map(invite -> Response.status(201).entity(InviteResponse.from(invite)).build());
    }

    @GET
    @Operation(summary = "List active invites", description = "Returns all unused and non-expired invite links.")
    @APIResponse(responseCode = "200", description = "Active invites returned")
    public Uni<List<InviteResponse>> list() {
        return inviteService.listActive()
                .map(list -> list.stream().map(InviteResponse::from).toList());
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Revoke invite", description = "Deletes an invite link, preventing it from being used.")
    @APIResponse(responseCode = "204", description = "Invite revoked")
    @APIResponse(responseCode = "404", description = "Invite not found")
    public Uni<Response> revoke(@PathParam("id") UUID id) {
        return inviteService.revoke(id)
                .map(ignored -> Response.noContent().build());
    }
}
