package dev.lzasn.cloudxmusic.client.gui.design;

import dev.lzasn.cloudxmusic.client.omni.*;
import dev.lzasn.cloudxmusic.client.state.PlaybackMode;
import org.junit.jupiter.api.Test;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;
import javax.imageio.ImageIO;
import static dev.lzasn.cloudxmusic.client.gui.design.MusicLayout.*;
import static org.junit.jupiter.api.Assertions.*;

class RoomVipPainterTest {
    @Test void roomVipBadgesUseThemeColorAndStayClearOfDurationForShortAndLongTitles()throws Exception{
        Path out=Path.of("../outputs/integration/previews");Files.createDirectories(out);
        try{
            for(boolean dark:new boolean[]{false,true})for(String title:List.of("房间测试曲","很长的歌曲名称用于验证VIP徽章始终留在标题宽度以内且不会覆盖右侧时长")){
                FrostCanvas.setDark(dark);
                BufferedImage vip=render(title,true),plain=render(title,false);
                assertBadge(vip,plain,title,468,170,20,300);
                var q=roomQueueBox();int baseline=q.y()+50+20;
                assertBadge(vip,plain,title,q.x()+86,baseline,12,268);
                for(int y=baseline-14;y<baseline+14;y++)for(int x=q.x()+q.w()-64;x<q.x()+q.w()-20;x++)
                    assertEquals(plain.getRGB(x+PAD,y+PAD),vip.getRGB(x+PAD,y+PAD),"VIP 徽章不能覆盖时长");
                ImageIO.write(vip,"png",out.resolve("room-vip-"+(dark?"dark":"light")+(title.length()>10?"-long":"")+".png").toFile());
            }
        }finally{FrostCanvas.setDark(false);}
    }
    @Test void missingCoverImmediatelyUsesPlaceholderAndNeverAffectsVip(){
        var requests=new java.util.ArrayList<String>();
        var view=view("房间测试曲",true);var image=MusicPainter.paint(view,new MusicLayout(MusicView.Page.ROOM,0,1),1,url->{requests.add(url);return null;});
        assertNotNull(image);assertTrue(requests.contains("https://y.gtimg.cn/test.jpg"));
        assertTrue(view.room().nowVip());
    }
    private static BufferedImage render(String title,boolean vip){
        return MusicPainter.paint(view(title,vip),new MusicLayout(MusicView.Page.ROOM,0,1),1,url->null);
    }
    private static MusicView view(String title,boolean vip){
        var song=new SongView(MusicSource.QQ,"VIP1",title,"测试歌手","",180,"https://y.gtimg.cn/test.jpg",vip);
        var room=new MusicView.RoomView(true,"ABC123","QQ 音乐","已同步",title,"测试歌手",100,180,true,false,
                List.of(new MusicView.RoomMember("ying","版权受限",false,MusicView.MemberTone.UNAVAILABLE)),
                List.of(new MusicView.RoomTrack(title,"测试歌手","03:00",song.coverUrl(),"VIP1",vip)),List.of(),0,0,"列表循环",20,0,"VIP1");
        return new MusicView(MusicView.Page.ROOM,song,List.of(),List.of(),List.of(),100,true,true,"ying","",false,false,"","HQ","双语",true,
                PlaybackMode.REPEAT_LIST,false,"2.5.0-alpha.3","",0,-1,.8f,"",FrostCanvas.dark,1,1,.5f,.5f,"H",0,0,room);
    }
    private static void assertBadge(BufferedImage vip,BufferedImage plain,String title,int x,int baseline,int size,int max){
        int bx;
        try(var c=new FrostCanvas(new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB),1)){
            bx=(int)Math.floor(x+c.width(c.fit(title,size,true,max-40),size,true)+8);
        }
        assertTrue(bx+32<=x+max,"长标题必须给徽章预留空间");
        assertTrue(accentPixels(vip,bx,baseline-12)>15,"徽章必须使用当前主题强调色");
        assertEquals(0,accentPixels(plain,bx,baseline-12),"普通歌曲不应显示 VIP");
    }
    private static int accentPixels(BufferedImage image,int x,int y){
        int count=0;Color target=FrostCanvas.PINK;
        for(int iy=y;iy<y+17;iy++)for(int ix=x;ix<x+33;ix++){
            Color color=new Color(image.getRGB(ix+PAD,iy+PAD),true);
            if(Math.abs(color.getRed()-target.getRed())<8&&Math.abs(color.getGreen()-target.getGreen())<8&&Math.abs(color.getBlue()-target.getBlue())<8)count++;
        }
        return count;
    }
}
