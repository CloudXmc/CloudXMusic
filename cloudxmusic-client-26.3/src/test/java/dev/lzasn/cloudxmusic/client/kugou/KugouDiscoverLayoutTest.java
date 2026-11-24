package dev.lzasn.cloudxmusic.client.kugou;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class KugouDiscoverLayoutTest {
    @Test void statusTextLeavesAtLeastFifteenPixelsBeforeTheListHeader() {
        assertTrue(KugouSearchPainter.statusHeaderGap() >= KugouSearchPainter.MIN_HEADER_GAP,
                "status line and list header must not overlap");
    }
}
