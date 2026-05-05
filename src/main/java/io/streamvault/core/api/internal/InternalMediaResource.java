package io.streamvault.core.api.internal;

import io.smallrye.common.annotation.Blocking;
import io.streamvault.core.application.storage.InternalUrlSigner;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Internal-only endpoints used by the filesystem storage backend to serve
 * pre-signed download/upload URLs to the pipeline.
 *
 * Not routed through Envoy. Authenticated via HMAC signature in query params.
 * Only active when streamvault.storage.backend=filesystem.
 */
@Path("/internal/media")
public class InternalMediaResource {

    @Inject
    InternalUrlSigner signer;

    @GET
    @Path("/download")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Blocking
    @Operation(hidden = true)
    public Response download(
            @QueryParam("path") String path,
            @QueryParam("expires") long expires,
            @QueryParam("sig") String sig) throws IOException {

        if (!signer.verify(path, expires, sig))
            return Response.status(403).build();

        java.nio.file.Path file = java.nio.file.Path.of(path);
        if (!Files.exists(file))
            return Response.status(404).build();

        return Response.ok(Files.newInputStream(file))
                .header("Content-Length", Files.size(file))
                .build();
    }

    @PUT
    @Path("/upload")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Blocking
    @Operation(hidden = true)
    public Response upload(
            @QueryParam("path") String path,
            @QueryParam("expires") long expires,
            @QueryParam("sig") String sig,
            InputStream body) throws IOException {

        if (!signer.verify(path, expires, sig))
            return Response.status(403).build();

        java.nio.file.Path dest = java.nio.file.Path.of(path);
        Files.createDirectories(dest.getParent());
        Files.copy(body, dest, StandardCopyOption.REPLACE_EXISTING);

        return Response.noContent().build();
    }
}
