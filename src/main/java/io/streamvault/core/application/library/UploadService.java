package io.streamvault.core.application.library;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.streamvault.core.application.storage.StorageBackend;
import io.streamvault.core.domain.library.*;
import io.vertx.mutiny.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.File;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

@ApplicationScoped
public class UploadService {

    private static final Logger LOG = Logger.getLogger(UploadService.class);

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            ".mp3", ".flac", ".ogg", ".aac", ".m4a");

    @Inject
    Vertx vertx;
    @Inject
    StorageBackend storage;
    @Inject
    ArtistRepository artists;
    @Inject
    AlbumRepository albums;
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

        if (!SUPPORTED_EXTENSIONS.contains(ext)) {
            return Uni.createFrom().failure(
                    new LibraryException(new LibraryError.UnsupportedFileType(filename)));
        }

        return vertx.executeBlocking(() -> storeAndExtract(upload, ext))
                .flatMap(metadata -> Panache.withTransaction(() -> upsertTrack(metadata)));
    }

    private TrackMetadata storeAndExtract(FileUpload upload, String ext) {
        String storedPath = storage.store(upload.uploadedFile(), upload.fileName(), ext);
        return extractMetadata(Path.of(storedPath), upload.fileName());
    }

    private TrackMetadata extractMetadata(Path filePath, String originalFilename) {
        java.util.logging.Logger.getLogger("org.jaudiotagger").setLevel(Level.OFF);
        File file = filePath.toFile();
        String mimeType = mimeTypeFor(filePath);

        try {
            var audioFile = AudioFileIO.read(file);
            var tag = audioFile.getTag();
            var header = audioFile.getAudioHeader();

            String title = tag != null ? blankToNull(tag.getFirst(FieldKey.TITLE)) : null;
            String artist = tag != null ? blankToNull(tag.getFirst(FieldKey.ARTIST)) : null;
            String album = tag != null ? blankToNull(tag.getFirst(FieldKey.ALBUM)) : null;
            String genre = tag != null ? blankToNull(tag.getFirst(FieldKey.GENRE)) : null;
            Integer year = parseIntOrNull(tag != null ? tag.getFirst(FieldKey.YEAR) : null);
            Integer trackNum = parseIntOrNull(tag != null ? tag.getFirst(FieldKey.TRACK) : null);
            Integer discNum = parseIntOrNull(tag != null ? tag.getFirst(FieldKey.DISC_NO) : null);
            int durationMs = header != null ? header.getTrackLength() * 1000 : 0;

            return new TrackMetadata(
                    filePath.toString(), mimeType, file.length(),
                    title != null ? title : stripExtension(originalFilename),
                    artist, album, year, trackNum, discNum, durationMs, genre);
        } catch (Exception e) {
            LOG.debugf("Could not read tags from %s: %s", filePath, e.getMessage());
            return new TrackMetadata(
                    filePath.toString(), mimeType, file.length(),
                    stripExtension(originalFilename),
                    null, null, null, null, null, 0, null);
        }
    }

    private Uni<Track> upsertTrack(TrackMetadata meta) {
        return resolveArtist(meta.artist())
                .flatMap(artist -> resolveAlbum(meta.album(), artist)
                        .flatMap(album -> upsertTrackEntity(meta, artist, album)));
    }

    private Uni<Artist> resolveArtist(String name) {
        if (name == null)
            return Uni.createFrom().nullItem();
        return artists.findByName(name).flatMap(opt -> {
            if (opt.isPresent())
                return Uni.createFrom().item(opt.get());
            var a = new Artist();
            a.name = name;
            return artists.persist(a);
        });
    }

    private Uni<Album> resolveAlbum(String title, Artist artist) {
        if (title == null || artist == null)
            return Uni.createFrom().nullItem();
        return albums.findByTitleAndArtist(title, artist.id).flatMap(opt -> {
            if (opt.isPresent())
                return Uni.createFrom().item(opt.get());
            var a = new Album();
            a.title = title;
            a.artist = artist;
            return albums.persist(a);
        });
    }

    private Uni<Track> upsertTrackEntity(TrackMetadata meta, Artist artist, Album album) {
        return tracks.findByFilePath(meta.filePath()).flatMap(opt -> {
            Track t = opt.orElseGet(Track::new);
            t.filePath = meta.filePath();
            t.title = meta.title();
            t.artist = artist;
            t.album = album;
            t.trackNumber = meta.trackNumber();
            t.discNumber = meta.discNumber();
            t.durationMs = meta.durationMs();
            t.genre = meta.genre();
            t.year = meta.year();
            t.fileSize = meta.fileSize();
            t.mimeType = meta.mimeType();
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

    private String mimeTypeFor(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".mp3"))
            return "audio/mpeg";
        if (name.endsWith(".flac"))
            return "audio/flac";
        if (name.endsWith(".ogg"))
            return "audio/ogg";
        if (name.endsWith(".aac") || name.endsWith(".m4a"))
            return "audio/aac";
        return "application/octet-stream";
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private Integer parseIntOrNull(String s) {
        if (s == null || s.isBlank())
            return null;
        try {
            String cleaned = s.contains("/") ? s.substring(0, s.indexOf('/')) : s;
            return Integer.parseInt(cleaned.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
