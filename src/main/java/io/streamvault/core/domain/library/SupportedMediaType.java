package io.streamvault.core.domain.library;

import java.util.Arrays;
import java.util.Optional;

public enum SupportedMediaType {

    MP3 ("audio/mpeg", ".mp3"),
    FLAC("audio/flac", ".flac"),
    OGG ("audio/ogg",  ".ogg"),
    AAC ("audio/aac",  ".m4a", ".aac");

    private final String mimeType;
    private final String[] extensions;

    SupportedMediaType(String mimeType, String... extensions) {
        this.mimeType = mimeType;
        this.extensions = extensions;
    }

    public String mimeType() {
        return mimeType;
    }

    public String primaryExtension() {
        return extensions[0];
    }

    public static Optional<SupportedMediaType> fromExtension(String ext) {
        String lower = ext.toLowerCase();
        return Arrays.stream(values())
                .filter(t -> Arrays.asList(t.extensions).contains(lower))
                .findFirst();
    }

    public static Optional<SupportedMediaType> fromMimeType(String mime) {
        if (mime == null) return Optional.empty();
        String lower = mime.toLowerCase();
        // normalize container aliases that map to AAC
        if (lower.equals("audio/mp4") || lower.equals("audio/x-m4a")) return Optional.of(AAC);
        return Arrays.stream(values())
                .filter(t -> t.mimeType.equals(lower))
                .findFirst();
    }
}
