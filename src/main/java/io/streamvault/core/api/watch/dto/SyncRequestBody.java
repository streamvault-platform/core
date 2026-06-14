package io.streamvault.core.api.watch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record SyncRequestBody(
        @NotBlank String deviceId,
        @NotNull @NotEmpty List<UUID> trackIds
) {}
