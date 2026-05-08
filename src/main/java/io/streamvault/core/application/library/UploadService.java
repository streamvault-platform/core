package io.streamvault.core.application.library;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.streamvault.core.application.pipeline.MediaEventPublisher;
import io.streamvault.core.application.pipeline.event.TrackUploadedEvent;
import io.streamvault.core.application.storage.StorageBackend;
import io.streamvault.core.domain.library.*;
import io.vertx.mutiny.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

@ApplicationScoped
public class UploadService {

    private static final Logger LOG = LoggerFactory.getLogger(UploadService.class);

    @Inject
    Vertx vertx;
    @Inject
    StorageBackend storage;
    @Inject
    MediaEventPublisher eventPublisher;
    @Inject
    TrackRepository tracks;

    public Uni<List<Track>> processUploads(List<FileUpload> uploads) {
        return Multi.createFrom().iterable(uploads)
                .onItem().transformToUniAndConcatenate(this::processOne)
                .collect().asList();
    }

    private Uni<Track> processOne(FileUpload upload) {
        String filename = upload.fileName();
        String ext = extensionOf(filename).toLowerCase();

        if (SupportedMediaType.fromExtension(ext).isEmpty()) {
            return Uni.createFrom().failure(
                    new LibraryException(new LibraryError.UnsupportedFileType(filename)));
        }

        return vertx.executeBlocking(() -> storeFile(upload, ext))
                .flatMap(meta -> Panache.withTransaction(() -> upsertTrack(meta)))
                .call(track -> {
                    LOG.info("action=track_uploaded trackId={} filename={} mimeType={}", track.id, upload.fileName(), track.mimeType);
                    String downloadUrl = storage.presignDownload(track.filePath, Duration.ofDays(7));
                    String transcodedPath = storage.transcodedStoredPath(track.id);
                    String uploadUrl = storage.presignUpload(transcodedPath, Duration.ofDays(7));
                    return eventPublisher.publishTrackUploaded(new TrackUploadedEvent(
                            track.id, track.filePath, track.mimeType, upload.fileName(),
                            downloadUrl, uploadUrl, transcodedPath));
                });
    }

    private TrackMetadata storeFile(FileUpload upload, String ext) throws Exception {
        Path tempFile = upload.uploadedFile();
        long size = Files.size(tempFile);
        String storedPath = storage.store(tempFile, upload.fileName(), ext);
        return new TrackMetadata(
                storedPath,
                SupportedMediaType.fromExtension(ext).map(SupportedMediaType::mimeType).orElse("application/octet-stream"),
                size,
                stripExtension(upload.fileName()));
    }

    private Uni<Track> upsertTrack(TrackMetadata meta) {
        return tracks.findByFilePath(meta.filePath()).flatMap(opt -> {
            Track t = opt.orElseGet(Track::new);
            t.filePath = meta.filePath();
            t.title = meta.title();
            t.mimeType = meta.mimeType();
            t.fileSize = meta.fileSize();
            t.updatedAt = OffsetDateTime.now();
            return opt.isPresent() ? tracks.update(t) : tracks.persist(t);
        });
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }

    private String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(0, dot) : filename;
    }

}
