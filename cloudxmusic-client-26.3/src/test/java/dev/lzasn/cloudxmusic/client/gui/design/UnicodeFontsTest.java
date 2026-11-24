package dev.lzasn.cloudxmusic.client.gui.design;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class UnicodeFontsTest {
    @Test void koreanJapaneseAndChineseGlyphsAreReallyPresent(){
        for(int cp:"안녕하세요 곡사랑 日本語カタカナひらがな 音乐".codePoints().toArray())
            assertTrue(UiFonts.font(cp,19,true).canDisplay(cp),"missing U+"+Integer.toHexString(cp));
    }
}
