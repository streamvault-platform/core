package io.streamvault.core.domain.library;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "artists")
public class Artist {

    @Id
    @GeneratedValue
    public UUID id;

    @Column(nullable = false, unique = true, length = 255)
    public String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    public OffsetDateTime createdAt = OffsetDateTime.now();
}
