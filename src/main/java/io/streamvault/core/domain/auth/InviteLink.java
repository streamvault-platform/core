package io.streamvault.core.domain.auth;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "invite_links")
public class InviteLink {

    @Id
    @GeneratedValue
    public UUID id;

    @Column(nullable = false, unique = true, length = 64)
    public String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    public User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    public OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "used_at")
    public OffsetDateTime usedAt;

    @Column(name = "used_by")
    public UUID usedById;

    @Column(name = "expires_at")
    public OffsetDateTime expiresAt;

    public boolean isExpired() {
        return expiresAt != null && OffsetDateTime.now().isAfter(expiresAt);
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isValid() {
        return !isUsed() && !isExpired();
    }
}
