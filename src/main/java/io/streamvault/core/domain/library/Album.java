package io.streamvault.core.domain.library;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "albums")
public class Album {

    @Id
    @GeneratedValue
    public UUID id;

    @Column(nullable = false, length = 255)
    public String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artist_id")
    public Artist artist;

    public Integer year;

    @Column(name = "artwork_path", columnDefinition = "TEXT")
    public String artworkPath;

    @Column(name = "created_at", nullable = false, updatable = false)
    public OffsetDateTime createdAt = OffsetDateTime.now();
}
