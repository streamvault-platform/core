package io.streamvault.core.application.stream;

import io.streamvault.core.domain.stream.StreamError;

public class StreamException extends RuntimeException {
    public final StreamError error;

    public StreamException(StreamError error) {
        super(error.toString());
        this.error = error;
    }
}
