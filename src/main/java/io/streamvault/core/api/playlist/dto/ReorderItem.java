package io.streamvault.core.api.playlist.dto;

import java.util.UUID;

public record ReorderItem(
        UUID trackId,
        int position
) {}
