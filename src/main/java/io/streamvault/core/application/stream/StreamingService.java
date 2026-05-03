package io.streamvault.core.application.stream;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.streamvault.core.domain.library.TrackRepository;
import io.streamvault.core.domain.stream.StreamError;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

@ApplicationScoped
public class StreamingService {

    @Inject
    TrackRepository tracks;

    public Uni<StreamResponse> serve(UUID trackId, String rangeHeader) {
        return Panache.withTransaction(() -> tracks.findTrackById(trackId))
                .map(opt -> opt.orElseThrow(() -> new StreamException(new StreamError.TrackNotFound())))
                .emitOn(Infrastructure.getDefaultWorkerPool())
                .map(track -> openFile(track.filePath, track.mimeType, track.fileSize, rangeHeader));
    }

    private StreamResponse openFile(String filePath, String mimeType, Long storedSize, String rangeHeader) {
        java.nio.file.Path file = java.nio.file.Path.of(filePath);

        if (!Files.exists(file)) {
            throw new StreamException(new StreamError.FileNotFound(filePath));
        }

        String mime = mimeType != null ? mimeType : "application/octet-stream";
        long fileSize;
        try {
            fileSize = (storedSize != null && storedSize > 0) ? storedSize : Files.size(file);
        } catch (IOException e) {
            throw new StreamException(new StreamError.ReadError(e.getMessage()));
        }

        if (rangeHeader == null || rangeHeader.isBlank()) {
            try {
                return new StreamResponse.FullFile(Files.newInputStream(file), mime, fileSize);
            } catch (IOException e) {
                throw new StreamException(new StreamError.ReadError(e.getMessage()));
            }
        }

        RangeSpec range = RangeParser.parse(rangeHeader, fileSize);
        if (range == null) {
            return new StreamResponse.InvalidRange(fileSize);
        }

        try {
            InputStream partial = openRange(file, range.start(), range.length());
            return new StreamResponse.PartialFile(partial, mime, fileSize, range.start(), range.end());
        } catch (IOException e) {
            throw new StreamException(new StreamError.ReadError(e.getMessage()));
        }
    }

    private InputStream openRange(java.nio.file.Path file, long start, long length) throws IOException {
        FileChannel channel = FileChannel.open(file, StandardOpenOption.READ);
        channel.position(start);
        return new LimitedInputStream(Channels.newInputStream(channel), length);
    }
}
