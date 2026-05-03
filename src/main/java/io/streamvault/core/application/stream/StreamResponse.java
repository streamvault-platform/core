package io.streamvault.core.application.stream;

import java.io.InputStream;

public sealed interface StreamResponse {

    record FullFile(InputStream content, String mimeType, long fileSize) implements StreamResponse {}

    record PartialFile(InputStream content, String mimeType, long fileSize, long start, long end)
            implements StreamResponse {
        public long length() { return end - start + 1; }
    }

    record InvalidRange(long fileSize) implements StreamResponse {}
}
