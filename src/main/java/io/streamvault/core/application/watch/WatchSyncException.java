package io.streamvault.core.application.watch;

public class WatchSyncException extends RuntimeException {
    public final WatchSyncError error;

    public WatchSyncException(WatchSyncError error) {
        super(error.toString());
        this.error = error;
    }
}
