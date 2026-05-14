package io.streamvault.core.application.playlist;

import io.streamvault.core.domain.playlist.PlaylistError;

public class PlaylistException extends RuntimeException {
    public final PlaylistError error;

    public PlaylistException(PlaylistError error) {
        super(error.toString());
        this.error = error;
    }
}
