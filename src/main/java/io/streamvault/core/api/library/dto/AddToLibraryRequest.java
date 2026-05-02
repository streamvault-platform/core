package io.streamvault.core.api.library.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddToLibraryRequest(@NotNull UUID trackId) {}
