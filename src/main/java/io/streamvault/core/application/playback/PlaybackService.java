package io.streamvault.core.application.playback;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.value.SetArgs;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import io.streamvault.core.domain.playback.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PlaybackService {

    private static final Logger log = Logger.getLogger(PlaybackService.class);
    private static final String DIRTY_SET = "sv:playback:dirty";
    private static final String POS_KEY_PREFIX = "sv:playback:pos:";
    private static final long POS_TTL_SECONDS = 7200;

    @Inject PlaybackStateRepository playbackStates;
    @Inject TrackPositionRepository trackPositions;
    @Inject ReactiveRedisDataSource redis;
    @Inject ObjectMapper objectMapper;
    @Inject ScrobbleEventPublisher scrobblePublisher;

    public Uni<Void> handleEvent(UUID userId, PlaybackEvent event) {
        return switch (event) {
            case PlaybackEvent.Play e      -> persistToDb(userId, e.trackId(), e.positionMs(), true)
                                                .flatMap(v -> bufferPosition(userId, e.trackId(), e.positionMs()))
                                                .flatMap(v -> publishScrobble(userId, e.trackId(), e.positionMs()));
            case PlaybackEvent.Heartbeat e -> bufferPosition(userId, e.trackId(), e.positionMs());
            case PlaybackEvent.Seek e      -> bufferPosition(userId, e.trackId(), e.positionMs());
            case PlaybackEvent.Pause e     -> flushToDb(userId, e.trackId(), e.positionMs());
        };
    }

    public Uni<Optional<PlaybackState>> getState(UUID userId) {
        return Panache.withTransaction(() -> playbackStates.findByUserId(userId));
    }

    private Uni<Void> publishScrobble(UUID userId, UUID trackId, long positionMs) {
        return scrobblePublisher.publish(new ScrobbleEvent(userId, trackId, positionMs, Instant.now()))
                .onFailure().invoke(e -> log.warnf("action=scrobble_publish_failed userId=%s trackId=%s error=%s",
                        userId, trackId, e.getMessage()))
                .onFailure().recoverWithNull();
    }

    // Buffer latest position in Redis; mark user dirty for background flush
    private Uni<Void> bufferPosition(UUID userId, UUID trackId, long positionMs) {
        String key = POS_KEY_PREFIX + userId;
        String value;
        try {
            value = objectMapper.writeValueAsString(new PositionBuffer(trackId.toString(), positionMs));
        } catch (JsonProcessingException e) {
            return Uni.createFrom().failure(e);
        }
        return redis.value(String.class)
                .set(key, value, new SetArgs().ex(Duration.ofSeconds(POS_TTL_SECONDS)))
                .flatMap(v -> redis.set(String.class).sadd(DIRTY_SET, userId.toString()))
                .replaceWithVoid();
    }

    // Flush Redis buffer to Postgres on PAUSE, then clear entry
    private Uni<Void> flushToDb(UUID userId, UUID trackId, long positionMs) {
        return persistToDb(userId, trackId, positionMs, false)
                .flatMap(v -> redis.value(String.class).getdel(POS_KEY_PREFIX + userId))
                .flatMap(v -> redis.set(String.class).srem(DIRTY_SET, userId.toString()))
                .replaceWithVoid();
    }

    // Every 15s: flush all dirty users' buffered positions to Postgres
    @Scheduled(every = "15s")
    void flushDirtyPositions() {
        redis.set(String.class).smembers(DIRTY_SET)
                .flatMap(userIds -> Uni.join().all(
                        userIds.stream()
                                .map(userIdStr -> flushDirtyUser(UUID.fromString(userIdStr)))
                                .toList()
                ).andFailFast())
                .subscribe().with(
                        v -> {},
                        e -> log.warnf("action=flush_dirty_positions error=%s", e.getMessage())
                );
    }

    private Uni<Void> flushDirtyUser(UUID userId) {
        return redis.value(String.class).getdel(POS_KEY_PREFIX + userId)
                .flatMap(json -> {
                    if (json == null) {
                        return redis.set(String.class).srem(DIRTY_SET, userId.toString()).replaceWithVoid();
                    }
                    PositionBuffer buf;
                    try {
                        buf = objectMapper.readValue(json, PositionBuffer.class);
                    } catch (JsonProcessingException e) {
                        return Uni.createFrom().failure(e);
                    }
                    return persistToDb(userId, UUID.fromString(buf.trackId()), buf.positionMs(), true)
                            .flatMap(v -> redis.set(String.class).srem(DIRTY_SET, userId.toString()))
                            .replaceWithVoid();
                });
    }

    private Uni<Void> persistToDb(UUID userId, UUID trackId, long positionMs, boolean isPlaying) {
        return Panache.withTransaction(() -> {
            PlaybackState state = new PlaybackState();
            state.userId = userId;
            state.trackId = trackId;
            state.positionMs = positionMs;
            state.isPlaying = isPlaying;
            state.updatedAt = OffsetDateTime.now();

            return playbackStates.save(state)
                    .flatMap(ignored -> updateTrackPosition(userId, trackId, positionMs))
                    .replaceWithVoid();
        });
    }

    private Uni<Void> updateTrackPosition(UUID userId, UUID trackId, long positionMs) {
        return trackPositions.findByUserAndTrack(userId, trackId)
                .flatMap(opt -> {
                    TrackPosition pos = opt.orElseGet(TrackPosition::new);
                    pos.userId = userId;
                    pos.trackId = trackId;
                    pos.positionMs = positionMs;
                    pos.updatedAt = OffsetDateTime.now();
                    return trackPositions.save(pos);
                })
                .replaceWithVoid();
    }

    record PositionBuffer(String trackId, long positionMs) {}
}
