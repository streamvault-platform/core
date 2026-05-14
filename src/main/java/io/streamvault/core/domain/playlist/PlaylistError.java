package io.streamvault.core.domain.playlist;

public sealed interface PlaylistError {
    record PlaylistNotFound() implements PlaylistError {}
    record TrackNotFound() implements PlaylistError {}
    record TrackAlreadyInPlaylist() implements PlaylistError {}
    record TrackNotInPlaylist() implements PlaylistError {}
    record Forbidden() implements PlaylistError {}
}
