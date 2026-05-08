package io.streamvault.core.application.stream;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.streamvault.core.application.storage.StorageBackend;
import io.streamvault.core.application.storage.StoredFileMetadata;
import io.streamvault.core.domain.library.SupportedMediaType;
import io.streamvault.core.domain.library.Track;
import io.streamvault.core.domain.library.TrackRepository;
import io.streamvault.core.domain.stream.StreamError;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@ApplicationScoped
public class StreamingService {

    @Inject
    TrackRepository tracks;

    @Inject
    StorageBackend storageBackend;

    public Uni<StreamResponse> serve(UUID trackId, String rangeHeader, String ifRangeHeader) {
        return Panache.withTransaction(() -> tracks.findTrackByIdWithDetails(trackId))
                .map(opt -> opt.orElseThrow(() -> new StreamException(new StreamError.TrackNotFound())))
                .map(StreamingService::toTrackInfo)
                .emitOn(Infrastructure.getDefaultWorkerPool())
                .map(info -> openFile(info, rangeHeader, ifRangeHeader));
    }

    public Uni<FileMetadata> probe(UUID trackId) {
        return Panache.withTransaction(() -> tracks.findTrackByIdWithDetails(trackId))
                .map(opt -> opt.orElseThrow(() -> new StreamException(new StreamError.TrackNotFound())))
                .map(StreamingService::toTrackInfo)
                .emitOn(Infrastructure.getDefaultWorkerPool())
                .map(this::buildMetadata);
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private record TrackInfo(String filePath, String mimeType, String title, String artistName) {}

    private static TrackInfo toTrackInfo(Track track) {
        return new TrackInfo(
                track.filePath,
                track.mimeType,
                track.title,
                track.artist != null ? track.artist.name : null);
    }

    private FileMetadata buildMetadata(TrackInfo info) {
        StoredFileMetadata meta = loadMetadata(info.filePath());
        return new FileMetadata(
                info.mimeType() != null ? info.mimeType() : "application/octet-stream",
                meta.size(),
                computeEtag(meta),
                buildFilename(info.title(), info.artistName(), info.mimeType()));
    }

    private StreamResponse openFile(TrackInfo info, String rangeHeader, String ifRangeHeader) {
        StoredFileMetadata meta = loadMetadata(info.filePath());
        String mime = info.mimeType() != null ? info.mimeType() : "application/octet-stream";
        long fileSize = meta.size();
        String etag = computeEtag(meta);
        String filename = buildFilename(info.title(), info.artistName(), info.mimeType());

        boolean serveRange = rangeHeader != null && !rangeHeader.isBlank();
        if (serveRange && ifRangeHeader != null && !ifRangeHeader.isBlank()) {
            serveRange = ifRangeHeader.equals(etag);
        }

        if (!serveRange) {
            try {
                InputStream content = storageBackend.openFull(info.filePath());
                return new StreamResponse.FullFile(content, mime, fileSize, etag, filename);
            } catch (IOException e) {
                throw new StreamException(new StreamError.ReadError(e.getMessage()));
            }
        }

        RangeSpec range = RangeParser.parse(rangeHeader, fileSize);
        if (range == null) {
            return new StreamResponse.InvalidRange(fileSize);
        }

        try {
            InputStream partial = storageBackend.openRange(info.filePath(), range.start(), range.length());
            return new StreamResponse.PartialFile(partial, mime, fileSize, range.start(), range.end(), etag, filename);
        } catch (IOException e) {
            throw new StreamException(new StreamError.ReadError(e.getMessage()));
        }
    }

    private StoredFileMetadata loadMetadata(String filePath) {
        try {
            return storageBackend.metadata(filePath);
        } catch (FileNotFoundException e) {
            throw new StreamException(new StreamError.FileNotFound(filePath));
        } catch (IOException e) {
            throw new StreamException(new StreamError.ReadError(e.getMessage()));
        }
    }

    private static String computeEtag(StoredFileMetadata meta) {
        return "\"" + meta.size() + "-" + meta.lastModified().toEpochMilli() + "\"";
    }

    static String buildFilename(String title, String artistName, String mimeType) {
        String ext = extensionFor(mimeType);
        String base;
        if (artistName != null && !artistName.isBlank() && title != null && !title.isBlank()) {
            base = artistName.trim() + " - " + title.trim();
        } else if (title != null && !title.isBlank()) {
            base = title.trim();
        } else {
            base = "track";
        }
        base = base.replaceAll("[\"\\\\]", "_");
        return base + ext;
    }

    private static String extensionFor(String mimeType) {
        return SupportedMediaType.fromMimeType(mimeType)
                .map(SupportedMediaType::primaryExtension)
                .orElse("");
    }
}
