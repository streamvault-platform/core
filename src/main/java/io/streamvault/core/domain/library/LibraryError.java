package io.streamvault.core.domain.library;

public sealed interface LibraryError {
    record UnsupportedFileType(String filename) implements LibraryError {}
    record StorageError(String message) implements LibraryError {}
    record TrackNotFound() implements LibraryError {}
    record AlbumNotFound() implements LibraryError {}
    record AlreadyInLibrary() implements LibraryError {}
    record NotInLibrary() implements LibraryError {}
}
