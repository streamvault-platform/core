package io.streamvault.core.application.stream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StreamingServiceFilenameTest {

    @Test
    void buildFilename_artistAndTitle_combinesWithDash() {
        assertThat(StreamingService.buildFilename("Comfortably Numb", "Pink Floyd", "audio/mpeg"))
                .isEqualTo("Pink Floyd - Comfortably Numb.mp3");
    }

    @Test
    void buildFilename_titleOnly_usesTitle() {
        assertThat(StreamingService.buildFilename("Blue", null, "audio/flac"))
                .isEqualTo("Blue.flac");
    }

    @Test
    void buildFilename_noTitleNoArtist_fallsBackToTrack() {
        assertThat(StreamingService.buildFilename(null, null, "audio/mpeg"))
                .isEqualTo("track.mp3");
    }

    @Test
    void buildFilename_blankTitle_treatedAsAbsent() {
        assertThat(StreamingService.buildFilename("  ", null, "audio/ogg"))
                .isEqualTo("track.ogg");
    }

    @Test
    void buildFilename_quotesAndBackslashes_sanitised() {
        assertThat(StreamingService.buildFilename("He said \"hello\"", "AC\\DC", "audio/mpeg"))
                .isEqualTo("AC_DC - He said _hello_.mp3");
    }

    @Test
    void buildFilename_unknownMimeType_noExtension() {
        assertThat(StreamingService.buildFilename("Track", "Artist", null))
                .isEqualTo("Artist - Track");
    }

    @Test
    void buildFilename_m4a_correctExtension() {
        assertThat(StreamingService.buildFilename("Song", "Band", "audio/mp4"))
                .isEqualTo("Band - Song.m4a");
    }
}
