package io.streamvault.core.domain.library;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tracks")
public class Track {

    @Id
    @GeneratedValue
    public UUID id;

    @Column(name = "file_path", nullable = false, unique = true, columnDefinition = "TEXT")
    public String filePath;

    @Column(length = 255)
    public String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artist_id")
    public Artist artist;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "album_id")
    public Album album;

    @Column(name = "track_number")
    public Integer trackNumber;

    @Column(name = "disc_number")
    public Integer discNumber;

    @Column(name = "duration_ms")
    public Integer durationMs;

    @Column(length = 100)
    public String genre;

    public Integer year;

    @Column(name = "file_size")
    public Long fileSize;

    @Column(name = "mime_type", length = 50)
    public String mimeType;

    @Column(name = "created_at", nullable = false, updatable = false)
    public OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt = OffsetDateTime.now();

    @Column(name = "transcoded_path", columnDefinition = "TEXT")
    public String transcodedPath;

    @Column(name = "transcoded_mime_type", length = 50)
    public String transcodedMimeType;
}
