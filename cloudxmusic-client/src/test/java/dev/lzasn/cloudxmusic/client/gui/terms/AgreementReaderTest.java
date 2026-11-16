package dev.lzasn.cloudxmusic.client.gui.terms;

import dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class AgreementReaderTest {
    @Test void scrollingIsBoundedAndOnlyTheBottomUnlocksAgreement(){
        var reader=new AgreementReader();assertTrue(reader.maxScroll()>300);assertFalse(reader.atEnd());
        assertFalse(reader.scroll(Double.NaN));assertFalse(reader.scroll(Double.POSITIVE_INFINITY));assertEquals(0,reader.offset());
        assertTrue(reader.scroll(-1));assertFalse(reader.atEnd());reader.scroll(-10000);assertTrue(reader.atEnd());
        assertEquals(reader.maxScroll(),reader.offset());reader.scroll(1);assertFalse(reader.atEnd());reader.reset();assertEquals(0,reader.offset());
    }
    @Test void unicodeAndParagraphBreaksSurviveWrappingWithActualFontWidths(){
        try(var c=new FrostCanvas(new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB),1)){
            String paragraph="三平台 Cookie WWW iii 𠮷野家 🎵 한국어 ".repeat(6);
            var lines=AgreementReader.wrap(c,paragraph,81);
            assertEquals(paragraph,String.join("",lines));
            for(String line:lines){assertFalse(Character.isHighSurrogate(line.charAt(line.length()-1)));assertTrue(c.width(line,13,false)<=81.01);}
            assertEquals(java.util.List.of("一","","二"),AgreementReader.wrap(c,"一\n\n二",300));
        }
    }
    @Test void bothThemesRenderTopBottomAndFixedButtonsWithoutClipping() throws Exception {
        Path out=Path.of("../outputs/terms-2.6.0-alpha.3/previews");Files.createDirectories(out);
        try{
            for(boolean dark:new boolean[]{false,true}){
                FrostCanvas.setDark(dark);var reader=new AgreementReader();
                assertTrue(AgreementPainter.TEXT.y()+AgreementPainter.TEXT.h()<AgreementPainter.REJECT.y());
                assertTrue(AgreementPainter.REJECT.x()+AgreementPainter.REJECT.w()<AgreementPainter.AGREE.x());
                for(boolean bottom:new boolean[]{false,true}){
                    if(bottom)reader.scroll(-10000);
                    var image=AgreementPainter.paint(reader,true,"",1);
                    assertEquals(AgreementPainter.WIDTH+2*AgreementPainter.PAD,image.getWidth());
                    assertEquals(AgreementPainter.HEIGHT+2*AgreementPainter.PAD,image.getHeight());
                    assertNotEquals(0,image.getRGB(AgreementPainter.PAD+40,AgreementPainter.PAD+160)>>>24);
                    ImageIO.write(image,"png",out.resolve((dark?"dark":"light")+"-agreement-"+(bottom?"bottom":"top")+".png").toFile());
                }
            }
        }finally{FrostCanvas.setDark(false);}
    }
}
