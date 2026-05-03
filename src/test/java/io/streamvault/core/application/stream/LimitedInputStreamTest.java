package io.streamvault.core.application.stream;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class LimitedInputStreamTest {

    private static byte[] bytes(int... values) {
        byte[] b = new byte[values.length];
        for (int i = 0; i < values.length; i++) b[i] = (byte) values[i];
        return b;
    }

    // ── single-byte read ─────────────────────────────────────────────────────

    @Test
    void read_returnsExactlyLimitBytes_singleByteApi() throws IOException {
        InputStream source = new ByteArrayInputStream(bytes(1, 2, 3, 4, 5));
        try (LimitedInputStream limited = new LimitedInputStream(source, 3)) {
            assertThat(limited.read()).isEqualTo(1);
            assertThat(limited.read()).isEqualTo(2);
            assertThat(limited.read()).isEqualTo(3);
            assertThat(limited.read()).isEqualTo(-1);
        }
    }

    // ── bulk read ────────────────────────────────────────────────────────────

    @Test
    void read_bulkFillsBuffer_upToLimit() throws IOException {
        byte[] source = new byte[100];
        for (int i = 0; i < 100; i++) source[i] = (byte) i;

        try (LimitedInputStream limited = new LimitedInputStream(new ByteArrayInputStream(source), 40)) {
            byte[] buf = new byte[100];
            int totalRead = 0;
            int n;
            while ((n = limited.read(buf, totalRead, buf.length - totalRead)) != -1) {
                totalRead += n;
            }
            assertThat(totalRead).isEqualTo(40);
            for (int i = 0; i < 40; i++) assertThat(buf[i]).isEqualTo((byte) i);
        }
    }

    @Test
    void read_returnsMinusOneImmediately_whenLimitIsZero() throws IOException {
        try (LimitedInputStream limited = new LimitedInputStream(new ByteArrayInputStream(bytes(1, 2, 3)), 0)) {
            assertThat(limited.read()).isEqualTo(-1);
            assertThat(limited.read(new byte[4], 0, 4)).isEqualTo(-1);
        }
    }

    @Test
    void read_doesNotReadBeyondUnderlying_whenSourceIsShorterThanLimit() throws IOException {
        InputStream source = new ByteArrayInputStream(bytes(10, 20));
        try (LimitedInputStream limited = new LimitedInputStream(source, 100)) {
            byte[] buf = new byte[10];
            int n = limited.read(buf, 0, buf.length);
            assertThat(n).isEqualTo(2);
            assertThat(limited.read()).isEqualTo(-1);
        }
    }

    // ── close ────────────────────────────────────────────────────────────────

    @Test
    void close_delegatesToUnderlyingStream() throws IOException {
        AtomicBoolean closed = new AtomicBoolean(false);
        InputStream source = new ByteArrayInputStream(bytes(1, 2, 3)) {
            @Override public void close() throws IOException { super.close(); closed.set(true); }
        };

        new LimitedInputStream(source, 2).close();

        assertThat(closed.get()).isTrue();
    }
}
