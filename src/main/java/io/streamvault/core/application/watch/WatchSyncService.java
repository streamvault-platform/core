package io.streamvault.core.application.watch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import io.streamvault.core.application.storage.StorageBackend;
import io.streamvault.core.domain.library.TrackRepository;
import io.streamvault.core.domain.watch.WatchSyncRequest;
import io.streamvault.core.domain.watch.WatchSyncRepository;
import io.streamvault.core.domain.watch.WatchSyncStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class WatchSyncService {

    private static final Logger LOG = Logger.getLogger(WatchSyncService.class);
    private static final Duration DOWNLOAD_URL_EXPIRY = Duration.ofHours(2);

    @Inject TrackRepository tracks;
    @Inject WatchSyncRepository syncRequests;
    @Inject WatchSyncEventPublisher eventPublisher;
    @Inject WatchSyncNotifier notifier;
    @Inject StorageBackend storage;
    @Inject ObjectMapper objectMapper;

    public Uni<SyncStatusResponse> requestSync(UUID userId, String deviceId, List<UUID> trackIds) {
        return Panache.withTransaction(() ->
            tracks.findTracksByIds(trackIds).flatMap(found -> {
                var missingIds = trackIds.stream()
                        .filter(id -> found.stream().noneMatch(t -> t.id.equals(id)))
                        .toList();
                if (!missingIds.isEmpty()) {
                    return Uni.createFrom().failure(new WatchSyncException(new WatchSyncError.TrackNotFound(missingIds)));
                }
                var notTranscoded = found.stream().filter(t -> t.transcodedPath == null).map(t -> t.id).toList();
                if (!notTranscoded.isEmpty()) {
                    return Uni.createFrom().failure(new WatchSyncException(new WatchSyncError.TrackNotTranscoded(notTranscoded)));
                }

                var trackInfos = found.stream()
                        .map(t -> new WatchSyncRequestedEvent.TrackInfo(
                                t.id,
                                storage.presignDownload(t.transcodedPath, DOWNLOAD_URL_EXPIRY)))
                        .toList();

                var request = new WatchSyncRequest();
                request.userId = userId;
                request.deviceId = deviceId;
                request.status = WatchSyncStatus.PENDING;
                request.trackIds = toJson(trackIds);

                return syncRequests.persist(request)
                        .flatMap(saved -> {
                            var event = new WatchSyncRequestedEvent(saved.id, userId, deviceId, trackInfos);
                            return eventPublisher.publishWatchSyncRequested(event)
                                    .map(v -> toStatusResponse(saved, trackIds.size()));
                        });
            })
        );
    }

    public Uni<SyncStatusResponse> getStatus(UUID userId, UUID syncRequestId) {
        return Panache.withTransaction(() ->
            syncRequests.findByIdOptional(syncRequestId).map(opt -> {
                var req = opt.orElseThrow(() ->
                        new WatchSyncException(new WatchSyncError.SyncRequestNotFound(syncRequestId)));
                if (!req.userId.equals(userId)) {
                    throw new WatchSyncException(new WatchSyncError.Forbidden());
                }
                List<UUID> ids = fromJson(req.trackIds);
                return toStatusResponse(req, ids.size());
            })
        );
    }

    public Uni<Void> handleSyncReady(WatchSyncReadyEvent event) {
        return Panache.withTransaction(() ->
            syncRequests.findByIdOptional(event.syncRequestId()).flatMap(opt -> {
                if (opt.isEmpty()) {
                    LOG.warnf("action=watch_sync_ready result=ignored reason=request_not_found syncRequestId=%s", event.syncRequestId());
                    return Uni.createFrom().voidItem();
                }
                WatchSyncRequest req = opt.get();
                req.status = WatchSyncStatus.READY;
                req.manifest = toJson(event.manifest());
                req.updatedAt = OffsetDateTime.now();
                LOG.infof("action=watch_sync_ready_stored syncRequestId=%s deviceId=%s", req.id, req.deviceId);
                return syncRequests.update(req)
                        .invoke(saved -> notifier.notifyDevice(saved.userId, saved.deviceId, event))
                        .replaceWithVoid();
            })
        );
    }

    private SyncStatusResponse toStatusResponse(WatchSyncRequest req, int trackCount) {
        List<WatchSyncReadyEvent.ManifestEntry> manifest = null;
        if (req.status == WatchSyncStatus.READY && req.manifest != null) {
            manifest = fromJsonManifest(req.manifest);
        }
        return new SyncStatusResponse(req.id, req.status, req.deviceId, trackCount, manifest);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON serialization failed", e);
        }
    }

    private List<UUID> fromJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON deserialization failed", e);
        }
    }

    private List<WatchSyncReadyEvent.ManifestEntry> fromJsonManifest(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON deserialization failed", e);
        }
    }

    public record SyncStatusResponse(
            UUID syncRequestId,
            WatchSyncStatus status,
            String deviceId,
            int trackCount,
            List<WatchSyncReadyEvent.ManifestEntry> manifest
    ) {}
}
