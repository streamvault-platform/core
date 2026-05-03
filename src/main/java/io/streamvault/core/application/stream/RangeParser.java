package io.streamvault.core.application.stream;

class RangeParser {

    private RangeParser() {}

    /**
     * Parses an HTTP Range header against the known file size.
     * Returns null for a missing/blank header (caller should serve the full file)
     * or for a syntactically invalid / out-of-bounds range (caller should return 416).
     * Distinguishing the two cases is the caller's responsibility via a prior null-check on the header.
     */
    static RangeSpec parse(String header, long fileSize) {
        if (header == null || header.isBlank()) return null;
        if (!header.startsWith("bytes=")) return null;
        String spec = header.substring(6).trim();
        int dash = spec.indexOf('-');
        if (dash < 0) return null;
        try {
            String startStr = spec.substring(0, dash).trim();
            String endStr = spec.substring(dash + 1).trim();
            long start, end;
            if (startStr.isEmpty()) {
                // suffix range: bytes=-500 → last 500 bytes
                long suffix = Long.parseLong(endStr);
                start = Math.max(0, fileSize - suffix);
                end = fileSize - 1;
            } else {
                start = Long.parseLong(startStr);
                end = endStr.isEmpty() ? fileSize - 1 : Long.parseLong(endStr);
            }
            // clamp an over-large end to the last byte
            if (end >= fileSize) end = fileSize - 1;
            if (start < 0 || start >= fileSize || end < start) return null;
            return new RangeSpec(start, end);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
