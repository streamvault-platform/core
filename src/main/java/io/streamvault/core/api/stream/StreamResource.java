package io.streamvault.core.api.stream;

import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import io.streamvault.core.api.common.ErrorResponse;
import io.streamvault.core.application.stream.StreamException;
import io.streamvault.core.application.stream.StreamResponse;
import io.streamvault.core.application.stream.StreamingService;
import io.streamvault.core.domain.stream.StreamError;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.util.UUID;

@Path("/stream")
@Authenticated
@Tag(name = "Streaming", description = "Stream audio files with full and partial content support")
public class StreamResource {

    @Inject
    StreamingService streamingService;

    @HEAD
    @Path("/{trackId}")
    @Produces(MediaType.WILDCARD)
    @Operation(summary = "Probe a track", description = "Returns headers only — no body. Use to get Content-Length and ETag before starting playback.")
    @APIResponse(responseCode = "200", description = "Track exists, headers returned")
    @APIResponse(responseCode = "401", description = "Missing or invalid JWT")
    @APIResponse(responseCode = "404", description = "Track or file not found")
    public Uni<Response> head(
            @Parameter(description = "Track UUID") @PathParam("trackId") UUID trackId) {

        return streamingService.probe(trackId).map(meta ->
                Response.ok()
                        .type(meta.mimeType())
                        .header("Content-Length", meta.fileSize())
                        .header("Accept-Ranges", "bytes")
                        .header("ETag", meta.etag())
                        .header("Content-Disposition", disposition(meta.filename()))
                        .build());
    }

    @GET
    @Path("/{trackId}")
    @Produces(MediaType.WILDCARD)
    @Operation(summary = "Stream a track",
            description = "Streams the original audio file. Supports HTTP Range requests for seek operations.")
    @APIResponse(responseCode = "200", description = "Full audio content")
    @APIResponse(responseCode = "206", description = "Partial audio content (range request)")
    @APIResponse(responseCode = "401", description = "Missing or invalid JWT")
    @APIResponse(responseCode = "404", description = "Track or file not found")
    @APIResponse(responseCode = "416", description = "Range not satisfiable")
    public Uni<Response> stream(
            @Parameter(description = "Track UUID") @PathParam("trackId") UUID trackId,
            @Parameter(description = "HTTP Range header, e.g. bytes=0-1023") @HeaderParam("Range") String rangeHeader,
            @Parameter(description = "Conditional range — ETag from a previous response") @HeaderParam("If-Range") String ifRangeHeader) {

        return streamingService.serve(trackId, rangeHeader, ifRangeHeader).map(this::toResponse);
    }

    private Response toResponse(StreamResponse result) {
        return switch (result) {
            case StreamResponse.FullFile r -> Response.ok(r.content())
                    .type(r.mimeType())
                    .header("Content-Length", r.fileSize())
                    .header("Accept-Ranges", "bytes")
                    .header("ETag", r.etag())
                    .header("Content-Disposition", disposition(r.filename()))
                    .build();
            case StreamResponse.PartialFile r -> Response.status(206)
                    .entity(r.content())
                    .type(r.mimeType())
                    .header("Accept-Ranges", "bytes")
                    .header("Content-Range", "bytes " + r.start() + "-" + r.end() + "/" + r.fileSize())
                    .header("Content-Length", r.length())
                    .header("ETag", r.etag())
                    .header("Content-Disposition", disposition(r.filename()))
                    .build();
            case StreamResponse.InvalidRange r -> Response.status(416)
                    .header("Content-Range", "bytes */" + r.fileSize())
                    .build();
        };
    }

    private static String disposition(String filename) {
        return "inline; filename=\"" + filename + "\"";
    }

    @ServerExceptionMapper
    public Response handleStreamException(StreamException ex) {
        return switch (ex.error) {
            case StreamError.TrackNotFound e ->
                    Response.status(404)
                            .entity(new ErrorResponse("TRACK_NOT_FOUND", "Track not found"))
                            .type(MediaType.APPLICATION_JSON)
                            .build();
            case StreamError.FileNotFound e ->
                    Response.status(404)
                            .entity(new ErrorResponse("FILE_NOT_FOUND", "Audio file not found on server"))
                            .type(MediaType.APPLICATION_JSON)
                            .build();
            case StreamError.ReadError e ->
                    Response.status(500)
                            .entity(new ErrorResponse("READ_ERROR", "Failed to read audio file"))
                            .type(MediaType.APPLICATION_JSON)
                            .build();
        };
    }
}
