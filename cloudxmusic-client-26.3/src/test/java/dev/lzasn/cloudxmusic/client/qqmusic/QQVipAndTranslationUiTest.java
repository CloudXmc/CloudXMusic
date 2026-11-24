package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.gui.design.*;
import dev.lzasn.cloudxmusic.client.omni.*;
import dev.lzasn.cloudxmusic.client.state.LyricMode;
import org.junit.jupiter.api.Test;
import java.awt.*;
import java.awt.image.BufferedImage;
import static dev.lzasn.cloudxmusic.client.qqmusic.QQSessionVerifierTest.json;
import static org.junit.jupiter.api.Assertions.*;

class QQVipAndTranslationUiTest {
    @Test void metadataVipIsRetainedAlongsideSearchVipAndRejectsMalformedFlags()throws Exception{
        var info=QQTrackMetadata.parseInfo("Song1",json("{\"track_info\":{\"mid\":\"Song1\",\"interval\":210,\"file\":{\"media_mid\":\"Media1\"},\"album\":{\"mid\":\"Album1\"},\"pay\":{\"play\":1}}}"));
        assertTrue(info.vip());var song=new UnifiedSong("qq","Song1","歌名","歌手","专辑");
        assertTrue(SongView.from(song,info.durationSeconds(),info.coverUrl(),info.vip()).vip());
        assertTrue(SongView.from(new UnifiedSong("qq","Song2","","","",true),0,"").vip());
        for(String body:new String[]{"{\"pay\":1}","{\"vip\":[]}","{\"pay\":{\"play\":-1}}","{\"vip\":\"oops\"}"})
            assertThrows(java.io.IOException.class,()->QQVip.parse(json(body)));
    }
    @Test void badgeReadsCurrentThemeAccentForBorderAndDoesNotOverflowLongTitle(){
        for(boolean dark:new boolean[]{false,true}){
            FrostCanvas.setDark(dark);Color accent=FrostCanvas.PINK;
            BufferedImage image=new BufferedImage(300,40,BufferedImage.TYPE_INT_ARGB);
            try(FrostCanvas c=new FrostCanvas(image,1)){QQVipBadge.title(c,"一首非常非常非常非常非常非常长的歌名",5,24,14,true,FrostCanvas.INK,240,true);}
            int matching=0;
            for(int y=0;y<40;y++)for(int x=0;x<300;x++){
                int argb=image.getRGB(x,y);
                if((argb>>>24)>100&&(argb&0xffffff)==(accent.getRGB()&0xffffff))matching++;
                if(x>246)assertEquals(0,argb>>>24,"VIP 不可挤入专辑或按钮区域");
            }
            assertTrue(matching>10,"主题强调色应实际用于徽章边框或文字");
        }
        FrostCanvas.setDark(false);
    }
    @Test void translationButtonUsesSafeGeometryAndTogglesSharedPreference(){
        assertTrue(QQTranslationControl.BUTTON.contains(1026,534));
        assertFalse(MusicPainter.playbackBarRegion().contains(1026,534));
        assertEquals(LyricMode.ORIGINAL,QQTranslationControl.next(LyricMode.BILINGUAL));
        assertEquals(LyricMode.BILINGUAL,QQTranslationControl.next(LyricMode.ORIGINAL));
    }
}
