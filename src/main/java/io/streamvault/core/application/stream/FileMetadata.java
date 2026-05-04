package io.streamvault.core.application.stream;

public record FileMetadata(String mimeType, long fileSize, String etag, String filename) {}
