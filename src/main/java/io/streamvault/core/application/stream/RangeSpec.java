package io.streamvault.core.application.stream;

record RangeSpec(long start, long end) {
    long length() { return end - start + 1; }
}
