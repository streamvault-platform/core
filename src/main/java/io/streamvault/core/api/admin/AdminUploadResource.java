package io.streamvault.core.api.admin;

import io.smallrye.mutiny.Uni;
import io.streamvault.core.api.admin.dto.UploadForm;
import io.streamvault.core.api.admin.dto.UploadResponse;
import io.streamvault.core.api.common.ErrorResponse;
import io.streamvault.core.application.library.LibraryException;
import io.streamvault.core.application.library.UploadService;
import io.streamvault.core.domain.library.LibraryError;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.util.List;

@Path("/admin/upload")
@RolesAllowed("ADMIN")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Admin — Upload", description = "Admin-only endpoints for ingesting audio files into the library")
public class AdminUploadResource {

    @Inject
    UploadService uploadService;

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "Upload audio files", description = "Upload one or more audio files (MP3, FLAC, OGG, AAC/M4A). Metadata is extracted automatically from ID3 tags.")
    @APIResponse(responseCode = "201", description = "All files ingested successfully",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = UploadResponse.class)))
    @APIResponse(responseCode = "400", description = "No files provided")
    @APIResponse(responseCode = "401", description = "Missing or invalid JWT")
    @APIResponse(responseCode = "403", description = "Caller is not an ADMIN")
    @APIResponse(responseCode = "422", description = "One or more files have an unsupported format")
    public Uni<Response> upload(UploadForm form) {
        if (form.files == null || form.files.isEmpty()) {
            return Uni.createFrom().item(
                    Response.status(400)
                            .entity(new ErrorResponse("NO_FILES", "At least one file is required"))
                            .build());
        }
        return uploadService.processUploads(form.files)
                .map(tracks -> {
                    List<UploadResponse> body = tracks.stream().map(UploadResponse::from).toList();
                    return Response.status(201).entity(body).build();
                });
    }

    @ServerExceptionMapper
    public Response handleLibraryException(LibraryException e) {
        return switch (e.error) {
            case LibraryError.UnsupportedFileType x ->
                    Response.status(422).entity(new ErrorResponse("UNSUPPORTED_FILE_TYPE",
                            "Unsupported file type: " + x.filename())).build();
            case LibraryError.StorageError x ->
                    Response.status(500).entity(new ErrorResponse("STORAGE_ERROR",
                            "Failed to store file: " + x.message())).build();
            case LibraryError.TrackNotFound x ->
                    Response.status(404).entity(new ErrorResponse("TRACK_NOT_FOUND", "Track not found")).build();
            case LibraryError.AlreadyInLibrary x ->
                    Response.status(409).entity(new ErrorResponse("ALREADY_IN_LIBRARY", "Track already in library")).build();
            case LibraryError.NotInLibrary x ->
                    Response.status(404).entity(new ErrorResponse("NOT_IN_LIBRARY", "Track not in library")).build();
        };
    }
}
