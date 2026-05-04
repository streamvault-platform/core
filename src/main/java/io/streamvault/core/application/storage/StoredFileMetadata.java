package io.streamvault.core.application.storage;

import java.time.Instant;

public record StoredFileMetadata(long size, Instant lastModified) {}
