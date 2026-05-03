package io.streamvault.core.application.library;

import io.streamvault.core.domain.library.LibraryError;

public class LibraryException extends RuntimeException {
    public final LibraryError error;

    public LibraryException(LibraryError error) {
        super(error.toString());
        this.error = error;
    }
}
