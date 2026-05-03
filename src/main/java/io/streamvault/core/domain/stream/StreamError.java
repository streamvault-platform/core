package io.streamvault.core.domain.stream;

public sealed interface StreamError {
    record TrackNotFound() implements StreamError {}
    record FileNotFound(String path) implements StreamError {}
    record ReadError(String message) implements StreamError {}
}
