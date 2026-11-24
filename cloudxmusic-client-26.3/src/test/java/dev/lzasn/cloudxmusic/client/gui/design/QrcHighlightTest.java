package dev.lzasn.cloudxmusic.client.gui.design;
import dev.lzasn.cloudxmusic.client.omni.TimedLyrics;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class QrcHighlightTest {
    @Test void widthFollowsPcmClockWithGapsPauseSeekAndUnicode(){
        var line=new TimedLyrics.Line(1000,1800,"😀你好",List.of(new TimedLyrics.Word(1000,400,"😀"),new TimedLyrics.Word(1800,1000,"你好")));
        var highlight=new QrcHighlight(line,19,true);assertEquals(0,highlight.widthAt(0));float first=highlight.widthAt(1400),end=highlight.widthAt(2800);
        assertTrue(first>0);assertEquals(first/2,highlight.widthAt(1200),.01);assertEquals(first,highlight.widthAt(1700));
        assertEquals((end+first)/2,highlight.widthAt(2300),.01);assertEquals(end,highlight.widthAt(9999));assertEquals(0,highlight.widthAt(0));
    }
}
