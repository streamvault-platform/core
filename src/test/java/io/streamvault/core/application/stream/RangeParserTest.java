package io.streamvault.core.application.stream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RangeParserTest {

    private static final long FILE_SIZE = 1000L;

    // ── null / blank header → no range (serve full file) ────────────────────

    @Test
    void parse_nullHeader_returnsNull() {
        assertThat(RangeParser.parse(null, FILE_SIZE)).isNull();
    }

    @Test
    void parse_blankHeader_returnsNull() {
        assertThat(RangeParser.parse("  ", FILE_SIZE)).isNull();
    }

    // ── well-formed ranges ───────────────────────────────────────────────────

    @Test
    void parse_closedRange_returnsCorrectSpec() {
        RangeSpec r = RangeParser.parse("bytes=0-99", FILE_SIZE);
        assertThat(r).isNotNull();
        assertThat(r.start()).isEqualTo(0);
        assertThat(r.end()).isEqualTo(99);
        assertThat(r.length()).isEqualTo(100);
    }

    @Test
    void parse_openEndedRange_extendsToLastByte() {
        RangeSpec r = RangeParser.parse("bytes=500-", FILE_SIZE);
        assertThat(r).isNotNull();
        assertThat(r.start()).isEqualTo(500);
        assertThat(r.end()).isEqualTo(999);
        assertThat(r.length()).isEqualTo(500);
    }

    @Test
    void parse_suffixRange_returnsLastNBytes() {
        RangeSpec r = RangeParser.parse("bytes=-200", FILE_SIZE);
        assertThat(r).isNotNull();
        assertThat(r.start()).isEqualTo(800);
        assertThat(r.end()).isEqualTo(999);
        assertThat(r.length()).isEqualTo(200);
    }

    @Test
    void parse_overLargeEnd_clampsToLastByte() {
        RangeSpec r = RangeParser.parse("bytes=0-9999", FILE_SIZE);
        assertThat(r).isNotNull();
        assertThat(r.end()).isEqualTo(999);
        assertThat(r.length()).isEqualTo(1000);
    }

    @Test
    void parse_lastByteRange_works() {
        RangeSpec r = RangeParser.parse("bytes=999-999", FILE_SIZE);
        assertThat(r).isNotNull();
        assertThat(r.start()).isEqualTo(999);
        assertThat(r.end()).isEqualTo(999);
        assertThat(r.length()).isEqualTo(1);
    }

    @Test
    void parse_suffixLargerThanFile_clampsToStart() {
        RangeSpec r = RangeParser.parse("bytes=-5000", FILE_SIZE);
        assertThat(r).isNotNull();
        assertThat(r.start()).isEqualTo(0);
        assertThat(r.end()).isEqualTo(999);
    }

    // ── invalid / unsatisfiable ranges → null (caller returns 416) ──────────

    @Test
    void parse_wrongUnit_returnsNull() {
        assertThat(RangeParser.parse("items=0-9", FILE_SIZE)).isNull();
    }

    @Test
    void parse_noDash_returnsNull() {
        assertThat(RangeParser.parse("bytes=0", FILE_SIZE)).isNull();
    }

    @Test
    void parse_startBeyondFileSize_returnsNull() {
        assertThat(RangeParser.parse("bytes=1000-1099", FILE_SIZE)).isNull();
    }

    @Test
    void parse_startAfterEnd_returnsNull() {
        assertThat(RangeParser.parse("bytes=500-100", FILE_SIZE)).isNull();
    }

    @Test
    void parse_nonNumericValues_returnsNull() {
        assertThat(RangeParser.parse("bytes=abc-def", FILE_SIZE)).isNull();
    }
}
