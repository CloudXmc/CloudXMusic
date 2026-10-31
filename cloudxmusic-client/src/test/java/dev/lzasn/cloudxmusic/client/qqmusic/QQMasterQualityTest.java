package dev.lzasn.cloudxmusic.client.qqmusic;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.lzasn.cloudxmusic.client.qqmusic.QQPlaybackResolverTest.*;
import static dev.lzasn.cloudxmusic.client.qqmusic.QQSessionVerifierTest.reply;

class QQMasterQualityTest {
    @Test void canonicalMasterMediaRequiresMatchingSongAndServerMid()throws Exception{
        var data=com.google.gson.JsonParser.parseString("{\"sip\":[\"https://isure.stream.qqmusic.qq.com/\"],\"midurlinfo\":[{\"songmid\":\"Song123\",\"filename\":\"AI00Media456.flac\",\"mid\":\"Master789\",\"purl\":\"AI00Master789.flac?vkey=opaque\",\"result\":0}]}").getAsJsonObject();
        assertEquals(1,QQPlaybackResolver.candidates(data,"AI00Media456.flac","Song123",true).size());
        data.getAsJsonArray("midurlinfo").get(0).getAsJsonObject().addProperty("songmid","OtherSong");
        assertThrows(java.io.IOException.class,()->QQPlaybackResolver.candidates(data,"AI00Media456.flac","Song123",true));
    }
    @Test void badgesFollowMeasuredLabelWithEightPixelGapAndKeepCheckmarkClear(){
        for(double scale:new double[]{1,1.25,2})try(var c=new dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas(new java.awt.image.BufferedImage(1000,500,2),scale)){
            for(var q:new QQQuality[]{QQQuality.MASTER,QQQuality.SQ,QQQuality.HQ})for(boolean selected:new boolean[]{false,true}){
                var row=QQQualityMenu.row(q.ordinal());var badge=QQQualityMenu.badge(c,q,selected);
                assertEquals(row.x()+14+c.width(q.menuLabel(),12,selected)+8,badge.getX(),.01);
                assertTrue(badge.getMaxX()<row.x()+row.w()-36);
            }
        }
    }
    @Test void qualityCheckmarkStaysAtTheRowsFixedRightPosition(){
        for(int width:new int[]{132,332})for(int i=0;i<4;i++){
            var row=dev.lzasn.cloudxmusic.client.gui.design.MusicPainter.qualityRow(i,4,width);
            assertEquals(row.x()+row.w()-18,dev.lzasn.cloudxmusic.client.gui.design.MusicPainter.qualityCheckmarkCenterX(row));
        }
    }
    @Test void qualityMenuRendersFourRowsWithBadgesInBothThemes()throws Exception{
        var out=java.nio.file.Path.of("../outputs/integration/previews");java.nio.file.Files.createDirectories(out);
        try{
            for(boolean dark:new boolean[]{false,true}){
                dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas.setDark(dark);
                var image=QQQualityMenu.paint(QQQuality.STANDARD.ordinal(),-1,2);
                assertEquals(664,image.getWidth());assertEquals(296,image.getHeight());
                javax.imageio.ImageIO.write(image,"png",out.resolve("quality-"+(dark?"dark":"light")+".png").toFile());
            }
        }finally{dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas.setDark(false);}
    }

    @Test void fourExplicitLevelsAndChineseBarLabels(){
        assertEquals(4,QQQuality.values().length);
        assertEquals(List.of(QQQuality.MASTER,QQQuality.SQ,QQQuality.HQ,QQQuality.STANDARD),QQQualityPolicy.chain(QQQuality.MASTER));
        assertEquals(List.of(QQQuality.HQ,QQQuality.STANDARD),QQQualityPolicy.chain(QQQuality.HQ));
        assertEquals(List.of("臻品母带","SQ 无损","HQ 高品质","标准品质"),Arrays.stream(QQQuality.values()).map(QQQuality::barLabel).toList());
    }
    @Test void unavailableMasterFallsBackWithoutMislabelingFlacAsMaster()throws Exception{
        List<String> files=new ArrayList<>();
        var resolver=resolver((r,c,t)->{
            if(method(r).equals("get_song_detail_yqq"))return metadata();
            files.add(filename(r));return filename(r).startsWith("AI00")?reply(22006,"{}"):link(filename(r));
        },(uri,flac,t)->{assertTrue(flac);return uri;});
        var result=resolver.resolveLink(SONG,QQQuality.MASTER,info->{});
        assertEquals(QQQuality.SQ,result.actualQuality());assertEquals(List.of("AI00Media456.flac","AI00Media456.flac","F000Media456.flac"),files);
    }
    @Test void unsupportedMasterFormatFallsBackBeforePlayback()throws Exception{
        var resolver=resolver((r,c,t)->method(r).equals("get_song_detail_yqq")?metadata():link(filename(r)),
                (uri,flac,t)->uri.getPath().contains("AI00")?null:uri);
        assertEquals(QQQuality.SQ,resolver.resolveLink(SONG,QQQuality.MASTER,info->{}).actualQuality());
    }
    @Test void streamInfoRejectsUnsupportedChannelCountAndRate()throws Exception{
        byte[] valid;try(var in=getClass().getResourceAsStream("/qq-audio/tone24.flac")){valid=in.readNBytes(42);}
        assertTrue(QQAudioUrlVerifier.decodableFlac(valid));
        byte[] multichannel=valid.clone();multichannel[20]=(byte)((multichannel[20]&0xf1)|14);
        assertFalse(QQAudioUrlVerifier.decodableFlac(multichannel));
        assertFalse(QQAudioUrlVerifier.decodableFlac(new byte[]{'f','L','a','C'}));
    }
}
