package dev.lzasn.cloudxmusic.client.qqmusic;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QQSearchLayoutTest {
    @Test void searchTabsHaveClearSpaceBeforeSongHeaderAndRows() {
        assertEquals(234, QQSearchPainter.HEADER_TOP);
        assertEquals(264, QQSearchPainter.ROW_TOP);
        assertEquals(QQPlaylistSearchPainter.TAB_TOP, QQPlaylistSearchPainter.SONGS.y());
        assertEquals(QQPlaylistSearchPainter.TAB_TOP, QQPlaylistSearchPainter.PLAYLISTS.y());
        assertTrue(QQPlaylistSearchPainter.SONGS.y() + QQPlaylistSearchPainter.SONGS.h() <= QQSearchPainter.HEADER_TOP - 10);
        assertTrue(QQPlaylistSearchPainter.SONGS.y() >= 116 + 36 + 10);
        assertTrue(QQSearchPainter.HEADER_TOP + 14 <= QQSearchPainter.ROW_TOP - 10);
        assertEquals(QQSearchPainter.ROW_TOP, QQSearchPainter.rowBox(0, 0).y());
        assertTrue(QQSearchPainter.resultArea(300, QQSearchPainter.ROW_TOP));
        assertFalse(QQSearchPainter.resultArea(300, QQSearchPainter.ROW_TOP - 1));
    }

    @Test void searchRowHitTestingMatchesScrolledPaintRows() {
        var first = QQSearchPainter.rowBox(4, 3);
        assertEquals(4, QQSearchPainter.rowAt(first.cx(), first.cy(), 3, 12));
        assertEquals(-1, QQSearchPainter.rowAt(first.x() - 1, first.cy(), 3, 12));
        assertEquals(-1, QQSearchPainter.rowAt(first.cx(), QQSearchPainter.ROW_TOP + QQSearchPainter.VISIBLE_ROWS * QQSearchPainter.ROW_H, 3, 12));
    }
}
