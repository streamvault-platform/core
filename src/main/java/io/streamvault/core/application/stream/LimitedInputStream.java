package io.streamvault.core.application.stream;

import java.io.IOException;
import java.io.InputStream;

class LimitedInputStream extends InputStream {

    private final InputStream delegate;
    private long remaining;

    LimitedInputStream(InputStream delegate, long limit) {
        this.delegate = delegate;
        this.remaining = limit;
    }

    @Override
    public int read() throws IOException {
        if (remaining <= 0) return -1;
        int b = delegate.read();
        if (b != -1) remaining--;
        return b;
    }

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
        if (remaining <= 0) return -1;
        int toRead = (int) Math.min(len, remaining);
        int read = delegate.read(buf, off, toRead);
        if (read > 0) remaining -= read;
        return read;
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
