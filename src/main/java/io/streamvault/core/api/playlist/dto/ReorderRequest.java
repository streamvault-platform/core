package io.streamvault.core.api.playlist.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ReorderRequest(
        @NotNull
        @NotEmpty
        List<ReorderItem> tracks
) {}
