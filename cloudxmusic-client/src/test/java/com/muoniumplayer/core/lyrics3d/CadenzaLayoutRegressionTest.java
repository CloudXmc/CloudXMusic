package com.muoniumplayer.core.lyrics3d;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CadenzaLayoutRegressionTest {
    @Test
    void everyTokenUsesItsOwnStartXWithoutTimedLyrics() {
        String text = "What does it matter";
        WorldLyricsState.SpawnedLine line = new WorldLyricsState.SpawnedLine(
                List.of(text), 0L, 3_000L, 0, 0, 0, 0, 0, null);
        WorldLyricsState.PixelWidthOf width = ignored -> 10f;

        WorldLyricsState.LyricGlyphs glyphs =
                WorldLyricsState.buildGlyphs(line, 4_000L, 1f, true, false, width, true);

        int nonWhitespace = (int) text.chars().filter(c -> !Character.isWhitespace(c)).count();
        assertEquals(nonWhitespace, glyphs.glyphs.size());
        assertTrue(line.cadenzaTokens != null && !line.cadenzaTokens.isEmpty());

        int n = 0;
        for (WorldLyricsState.LyricGlyph glyph : glyphs.glyphs) {
            CadenzaMotion.Token token = tokenAt(line.cadenzaTokens, n);
            float expectedX = (float) (token.homeX - token.width * .5)
                    + (n - token.charOffset) * 10f;
            assertEquals(expectedX, glyph.x, 0.001f,
                    "character " + n + " must stay inside its own token");
            n++;
        }
    }

    private static CadenzaMotion.Token tokenAt(List<CadenzaMotion.Token> tokens, int index) {
        for (CadenzaMotion.Token token : tokens) {
            if (index >= token.charOffset && index < token.charOffset + token.charCount) {
                return token;
            }
        }
        throw new AssertionError("No token for character " + index);
    }
}
