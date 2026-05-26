package io.streamvault.core.api.library;

import io.smallrye.mutiny.Uni;
import io.streamvault.core.api.common.ErrorResponse;
import io.streamvault.core.api.library.dto.AlbumResponse;
import io.streamvault.core.api.library.dto.CoverUploadForm;
import io.streamvault.core.application.library.AlbumCoverService;
import io.streamvault.core.application.library.LibraryException;
import io.streamvault.core.domain.library.LibraryError;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;
import io.quarkus.security.Authenticated;

import java.util.Set;
import java.util.UUID;

@Path("/albums/{id}/cover")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Library — Cover Art", description = "Upload and retrieve album cover art")
public class AlbumCoverResource {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".webp");

    @Inject
    AlbumCoverService coverService;

    @PUT
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed({"ADMIN", "ARTIST"})
    @Operation(summary = "Upload cover art for an album")
    @APIResponse(responseCode = "200", description = "Cover art stored, updated album returned")
    @APIResponse(responseCode = "400", description = "No file provided or unsupported image type")
    @APIResponse(responseCode = "401", description = "Missing or invalid JWT")
    @APIResponse(responseCode = "403", description = "Insufficient role")
    @APIResponse(responseCode = "404", description = "Album not found")
    public Uni<Response> uploadCover(@PathParam("id") UUID id, CoverUploadForm form) {
        if (form.file == null) {
            return Uni.createFrom().item(
                    Response.status(400).entity(new ErrorResponse("NO_FILE", "A file is required")).build());
        }
        String filename = form.file.fileName();
        String ext = filename != null && filename.contains(".")
                ? filename.substring(filename.lastIndexOf('.')).toLowerCase()
                : "";
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            return Uni.createFrom().item(
                    Response.status(400).entity(new ErrorResponse("UNSUPPORTED_IMAGE_TYPE",
                            "Supported types: jpg, jpeg, png, webp")).build());
        }
        return coverService.uploadCover(id, form.file.uploadedFile(), ext)
                .map(album -> Response.ok(AlbumResponse.from(album)).build());
    }

    @GET
    @Authenticated
    @Operation(summary = "Redirect to a time-limited cover art URL")
    @APIResponse(responseCode = "302", description = "Redirect to presigned cover URL")
    @APIResponse(responseCode = "401", description = "Missing or invalid JWT")
    @APIResponse(responseCode = "404", description = "Album not found or no cover uploaded")
    public Uni<Response> getCover(@PathParam("id") UUID id) {
        return coverService.getCoverPresignedUrl(id)
                .map(url -> Response.status(302).header("Location", url).build());
    }

    @ServerExceptionMapper
    public Response handleLibraryException(LibraryException e) {
        return switch (e.error) {
            case LibraryError.AlbumNotFound ignored ->
                    Response.status(404).entity(new ErrorResponse("ALBUM_NOT_FOUND", "Album not found")).build();
            case LibraryError.StorageError x ->
                    Response.status(500).entity(new ErrorResponse("STORAGE_ERROR", x.message())).build();
            default ->
                    Response.status(500).entity(new ErrorResponse("INTERNAL_ERROR", "Unexpected error")).build();
        };
    }
}
