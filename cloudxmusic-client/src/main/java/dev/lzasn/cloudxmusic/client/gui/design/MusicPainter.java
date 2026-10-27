package dev.lzasn.cloudxmusic.client.gui.design;

import dev.lzasn.cloudxmusic.client.state.Track;
import dev.lzasn.cloudxmusic.client.state.PlaybackMode;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Function;
import static dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas.*;
import static dev.lzasn.cloudxmusic.client.gui.design.MusicLayout.*;

/** 纯画布视图；业务操作由 Screen 适配器处理，可以离线生成同一份真实 UI。 */
public final class MusicPainter {
    private static final String[] TITLES = {"正在播放", "发现音乐", "搜索", "我的收藏", "播放队列", "设置与关于"};
    private static final String[] ICONS = {"play", "discover", "search", "heart", "list", "settings"};

    private MusicPainter() {}

    public static BufferedImage paint(MusicView v, MusicLayout layout, double scale, Function<String, BufferedImage> covers) {
        BufferedImage image = new BufferedImage((int) Math.ceil((WIDTH + PAD * 2) * scale),
                (int) Math.ceil((HEIGHT + PAD * 2) * scale), BufferedImage.TYPE_INT_ARGB);
        try (FrostCanvas c = new FrostCanvas(image, scale)) {
            c.g.translate(PAD, PAD);
            c.glass(0, 0, WIDTH, HEIGHT, 38, false);
            navigation(c, v, layout);
            c.text(TITLES[v.page().ordinal()], 292, 46, 21, true, INK, 600);
            c.text("为你的世界配一段旋律", 292, 69, 12, false, MUTED, 600);
            c.g.setColor(new Color(135, 90, 115, 30));
            c.g.drawLine(272, 92, 1070, 92);
            button(c, v, layout, "close", "close", false);
            switch (v.page()) {
                case NOW -> now(c, v, layout, covers);
                case SETTINGS -> { settings(c, v, layout); footer(c, v, layout, covers); }
                default -> { list(c, v, layout, covers); footer(c, v, layout, covers); }
            }
        }
        return image;
    }

    private static void navigation(FrostCanvas c, MusicView v, MusicLayout l) {
        c.text("CloudXmusic", 36, 46, 19, true, DEEP, 202);
        c.text("云朵音乐盒", 36, 73, 13, false, MUTED, 202);
        c.g.setColor(new Color(135, 90, 115, 30));
        c.g.drawLine(24, 92, 252, 92);
        c.text("音乐库", 36, 124, 12, false, MUTED, 190);
        for (int i = 0; i < 5; i++) {
            Box b = l.box("nav" + i);
            boolean on = i == v.page().ordinal();
            boolean hover = v.hover().equals("nav" + i);
            if (on || hover) {
                c.shadow(b.x(), b.y(), b.w(), b.h(), 19, 7, 4, new Color(160, 79, 113, on ? 22 : 12));
                c.round(b.x(), b.y(), b.w(), b.h(), 19, on ? new Color(248,218,231,235) : new Color(255,255,255,145));
            }
            Color color = on ? DEEP : hover ? INK : new Color(0x73606F);
            UiIcons.draw(c, ICONS[i], b.x()+20, b.cy(), 18, color);
            c.text(TITLES[i], b.x()+44, b.cy()+5, 14, on, color, 150);
        }
        c.g.setColor(new Color(135,90,115,30)); c.g.drawLine(24,370,252,370);
        c.text("账号", 36, 395, 12, false, MUTED, 190);
        Box account = l.box("account");
        if (v.hover().equals("account")) c.round(account.x(),account.y(),account.w(),account.h(),20,new Color(255,255,255,110));
        c.round(34, 419, 36, 36, 18, new Color(245,208,223));
        UiIcons.draw(c,"user",52,437,21,DEEP);
        c.text(v.loggedIn() ? v.nickname() : "访客模式", 82, 432, 14, true, INK, 151);
        c.text(v.loggedIn() ? "管理账号" : "扫码连接网易云音乐",82,453,11,false,MUTED,151);
        c.text("让音乐陪你探索世界",36,552,12,false,MUTED,202);
        c.text("H 开关界面 · Esc 返回",36,575,11,false,MUTED,202);
        Box b=l.box("settings"); c.card(b.x(),b.y(),b.w(),b.h(),21);
        UiIcons.draw(c,"settings",b.x()+20,b.cy(),19, v.page()==MusicView.Page.SETTINGS ? DEEP : MUTED);
        c.text("设置与关于", b.x()+44,b.cy()+5,13,false,INK,150);
    }

    private static void now(FrostCanvas c, MusicView v, MusicLayout l, Function<String,BufferedImage> covers) {
        cover(c,v.current(),292,141,246,32,covers);
        c.text(v.playing() ? "NOW PLAYING · 正在播放" : "NOW PLAYING · 已暂停",568,174,12,false,MUTED,470);
        c.text(v.current().title(),568,216,28,true,INK,480);
        c.text(v.current().artist(),568,246,15,false,MUTED,480);
        c.text(v.current().album(),568,270,12,false,MUTED,480);
        c.shadow(568,282,145,27,14,7,3,new Color(150,70,100,18));
        c.round(568,282,145,27,14,new Color(248,225,235,220));
        c.round(582,293,6,6,3,PINK);
        c.text(v.quality(),596,300,11,true,DEEP,108);
        progress(c,v,l.box("seek"),true);
        controls(c,v,l);
        c.center(v.status().isBlank() ? "音乐正在路上" : v.status(),807,417,11,false,MUTED,475);

        // 两张卡片严格使用相同的 y / h，左右间距固定为 16。
        c.card(292,439,371,190,28); c.card(679,439,371,190,28);
        c.text("歌词",312,470,14,true,INK,140);
        c.right("原文 + 翻译",641,470,11,MUTED);
        Shape clip=c.g.getClip(); c.g.clipRect(311,486,332,104);
        if (v.lyrics().isEmpty()) {
            c.center("让旋律陪伴你的世界",477,530,15,true,DEEP,320);
            c.center("这首歌暂无时间轴歌词",477,557,12,false,MUTED,320);
        } else {
            int y=505;
            for (MusicView.LyricRow line:v.lyrics()) {
                c.text(line.text(),312,y,line.active()?17:13,line.active(),line.active()?DEEP:MUTED,330);
                if (line.active() && !line.translation().isBlank()) {
                    c.text(line.translation(),312,y+22,11,false,MUTED,330);
                    y+=22;
                }
                y+=29;
            }
        }
        c.g.setClip(clip);
        c.g.setColor(new Color(140,100,120,24));c.g.drawLine(312,597,641,597);
        c.text("当前歌词 · "+Track.format((int)v.position()),312,616,11,false,MUTED,330);
        c.text("接下来播放",699,470,14,true,INK,180);
        c.right("查看全部",1028,470,11,v.hover().equals("allQueue")?DEEP:PINK);
        if (v.queue().isEmpty()) c.center("队列是空的，去发现喜欢的音乐",864,548,12,false,MUTED,320);
        for(int i=0;i<Math.min(3,v.queue().size());i++) {
            Track t=v.queue().get(i);Box b=l.box("queue"+i);
            if(i==0||v.hover().equals("queue"+i)) {
                c.shadow(b.x(),b.y(),b.w(),b.h(),18,7,3,new Color(160,85,115,15));
                c.round(b.x(),b.y(),b.w(),b.h(),18,new Color(251,230,239,220));
            }
            cover(c,t,699,b.y()+8,26,8,covers);
            c.text(t.title(),735,b.y()+17,13,true,INK,225);
            c.text(t.artist(),735,b.y()+33,11,false,MUTED,225);
            c.right(t.durationText(),1028,b.y()+25,11,MUTED);
        }
    }

    private static void list(FrostCanvas c,MusicView v,MusicLayout l,Function<String,BufferedImage> covers) {
        if(v.page()==MusicView.Page.SEARCH) {
            Box b=l.box("field");c.card(b.x(),b.y(),b.w(),b.h(),24);
            UiIcons.draw(c,"search",b.x()+24,b.cy(),19,PINK);
            c.text(v.search().isEmpty()?"搜索歌名 / 歌手":v.search(),b.x()+46,b.cy()+5,14,false,v.search().isEmpty()?MUTED:INK,b.w()-70);
            if(v.focused()&&v.cursor()) {
                String fitted=c.fit(v.search(),14,false,b.w()-70);
                int x=b.x()+46+(int)c.width(fitted,14,false);
                c.g.setColor(PINK);c.g.drawLine(x,b.y()+14,x,b.y()+33);
            }
            labelButton(c,v,l,"search","搜索");
        } else {
            c.text(switch(v.page()){case DISCOVER->"每日推荐";case FAVORITES->"我喜欢的音乐";default->"你的播放队列";},292,143,17,true,INK,620);
            labelButton(c,v,l,"refresh",v.page()==MusicView.Page.QUEUE?"清空队列":"刷新列表");
        }
        c.text("歌曲 / 歌手",360,194,11,false,MUTED,360);
        c.text("专辑",748,194,11,false,MUTED,180);
        c.right("时长",972,194,11,MUTED);
        if(v.tracks().isEmpty()) {
            c.card(292,220,758,330,28);
            UiIcons.draw(c,"note",671,335,50,PINK);
            c.center(v.status().isBlank()?"在这里发现下一首喜欢的歌":v.status(),671,402,15,true,INK,660);
            c.center(v.loggedIn()?"搜索歌名，或刷新每日推荐":"点击左侧账号，扫码登录网易云音乐",671,434,12,false,MUTED,660);
        }
        for(int i=0;i<v.tracks().size();i++) {
            Track t=v.tracks().get(i);Box b=l.box("row"+i);
            boolean on=t.equals(v.current());
            c.card(292,b.y(),758,52,20);
            if(on||v.hover().equals("row"+i)||v.hover().equals("add"+i)) c.round(292,b.y(),758,52,20,new Color(247,215,229,on?155:90));
            cover(c,t,304,b.y()+8,36,11,covers);
            c.text(t.title(),360,b.y()+21,14,true,on?DEEP:INK,360);
            c.text(t.artist(),360,b.y()+40,11,false,MUTED,360);
            c.text(t.album(),748,b.y()+31,12,false,MUTED,150);
            c.right(t.durationText(),972,b.y()+31,12,MUTED);
            button(c,v,l,"add"+i,v.page()==MusicView.Page.QUEUE?"remove":"plus",false);
        }
    }

    private static void settings(FrostCanvas c,MusicView v,MusicLayout l) {
        setting(c,v,l,"quality","播放音质",v.quality());
        setting(c,v,l,"lyric","灵动岛歌词",v.lyricMode());
        setting(c,v,l,"island","顶部灵动岛",v.islandEnabled()?"已开启":"已关闭");
        c.card(292,336,758,216,28);
        c.text("CloudXmusic",316,372,23,true,DEEP,700);
        c.text("云朵音乐盒 · "+v.version(),316,399,14,false,INK,690);
        c.text("开发者",316,438,12,false,MUTED,690);
        c.text("Lzasn,xWtree,cloudfl4re,xiaota888",316,465,17,true,INK,690);
        c.text("Minecraft 1.21.11 · Fabric · Inter + Noto Sans SC",316,503,12,false,MUTED,585);
        c.text("短信、密码、邮箱登录与歌单导入：使用 /cxm 客户端命令",316,527,11,false,MUTED,585);
        labelButton(c,v,l,"stop","停止播放");
    }

    private static void setting(FrostCanvas c,MusicView v,MusicLayout l,String key,String title,String value) {
        Box b=l.box(key);
        c.text(title,b.x(),b.y()+20,13,true,INK,205);
        c.right(value,b.x()+b.w(),b.y()+20,13,v.hover().equals(key)?DEEP:MUTED);
        c.g.setColor(new Color(135,90,115,30));
        c.g.drawLine(b.x(),b.y()+35,b.x()+b.w(),b.y()+35);
    }

    private static void footer(FrostCanvas c,MusicView v,MusicLayout l,Function<String,BufferedImage> covers) {
        c.card(292,575,758,86,27);
        cover(c,v.current(),308,588,40,12,covers);
        c.text(v.current().title(),362,605,14,true,INK,145);
        c.text(v.current().artist(),362,624,11,false,MUTED,145);
        Box favorite=l.box("favorite");
        c.round(favorite.x(),favorite.y(),favorite.w(),favorite.h(),14,
                v.favorite()?new Color(249,218,231,220):new Color(255,255,255,115));
        UiIcons.draw(c,v.favorite()?"heartFill":"heart",favorite.cx(),favorite.cy(),15,v.favorite()?DEEP:MUTED);
        for(int i=0;i<PlaybackMode.values().length;i++) modeButton(c,v,l,i);
        controls(c,v,l);
        progress(c,v,l.box("seek"),false);
        c.text(Track.format((int)v.position()),310,649,10,false,MUTED,65);
        volume(c,v,l);
    }

    private static void controls(FrostCanvas c,MusicView v,MusicLayout l) {
        button(c,v,l,"prev","prev",false);button(c,v,l,"next","next",false);
        button(c,v,l,"play",v.playing()?"pause":"play",true);
    }

    private static void modeButton(FrostCanvas c,MusicView v,MusicLayout l,int index) {
        Box b=l.box("mode"+index);
        PlaybackMode mode=PlaybackMode.values()[index];
        boolean active=v.playbackMode()==mode;
        if(active) {
            c.shadow(b.x(),b.y(),b.w(),b.h(),14,7,3,new Color(150,65,99,17));
            c.round(b.x(),b.y(),b.w(),b.h(),14,new Color(248,218,231,220));
        }
        UiIcons.draw(c,mode.icon,b.cx(),b.cy(),16,active?DEEP:MUTED);
    }

    private static void volume(FrostCanvas c,MusicView v,MusicLayout l) {
        Box b=l.box("volume");
        UiIcons.draw(c,"volume",b.x()-15,b.cy(),17,MUTED);
        c.round(b.x(),b.cy()-2,b.w(),4,2,new Color(227,207,218));
        c.round(b.x(),b.cy()-2,b.w()*Math.max(0,Math.min(1,v.volume())),4,2,PINK);
        c.round(b.x()+b.w()*Math.max(0,Math.min(1,v.volume()))-5,b.cy()-5,10,10,5,Color.WHITE);
        c.g.setColor(PINK);c.g.setStroke(new BasicStroke(1.5f));
        c.g.draw(shape(b.x()+b.w()*Math.max(0,Math.min(1,v.volume()))-5,b.cy()-5,10,10,5));
    }

    private static void progress(FrostCanvas c,MusicView v,Box b,boolean times) {
        c.round(b.x(),b.cy()-2,b.w(),4,2,new Color(227,207,218));
        c.round(b.x(),b.cy()-2,b.w()*v.progress(),4,2,PINK);
        c.round(b.x()+b.w()*v.progress()-6,b.cy()-6,12,12,6,Color.WHITE);
        c.g.setColor(PINK);c.g.setStroke(new BasicStroke(2));
        c.g.draw(shape(b.x()+b.w()*v.progress()-6,b.cy()-6,12,12,6));
        if(times) {
            c.text(Track.format((int)v.position()),b.x(),b.cy()+24,12,false,MUTED,80);
            c.right(v.current().durationText(),b.x()+b.w(),b.cy()+24,12,MUTED);
        }
    }

    private static void button(FrostCanvas c,MusicView v,MusicLayout l,String key,String icon,boolean accent) {
        Box b=l.box(key);boolean hover=v.hover().equals(key);
        c.shadow(b.x(),b.y(),b.w(),b.h(),b.h()/2,accent?10:6,accent?5:3,new Color(150,65,99,accent?38:18));
        c.round(b.x(),b.y(),b.w(),b.h(),b.h()/2,accent?new GradientPaint(b.x(),b.y(),new Color(245,162,189),b.x()+b.w(),b.y()+b.h(),hover?DEEP:PINK):new Color(255,255,255,hover?240:185));
        UiIcons.draw(c,icon,b.cx(),b.cy(),accent?26:19,accent?Color.WHITE:hover?DEEP:MUTED);
    }

    private static void labelButton(FrostCanvas c,MusicView v,MusicLayout l,String key,String label) {
        Box b=l.box(key);c.shadow(b.x(),b.y(),b.w(),b.h(),b.h()/2,8,3,new Color(160,70,109,20));
        c.round(b.x(),b.y(),b.w(),b.h(),b.h()/2,v.hover().equals(key)?new Color(244,203,221):new Color(249,225,235));
        c.center(label,b.cx(),b.cy()+5,13,true,DEEP,b.w()-16);
    }

    public static void cover(FrostCanvas c,Track track,int x,int y,int size,int radius,Function<String,BufferedImage> covers) {
        if(size>100) c.shadow(x,y,size,size,radius,14,7,new Color(89,45,65,28));
        Shape previous=c.g.getClip();c.g.clip(shape(x,y,size,size,radius));
        BufferedImage image=covers.apply(track.coverUrl());
        if(image!=null) {
            double zoom=(double)size/Math.min(image.getWidth(),image.getHeight());
            int w=(int)Math.ceil(image.getWidth()*zoom),h=(int)Math.ceil(image.getHeight()*zoom);
            c.g.drawImage(image,x+(size-w)/2,y+(size-h)/2,w,h,null);
        } else {
            c.round(x,y,size,size,radius,new GradientPaint(x,y,new Color(178,216,207),x+size,y+size,new Color(226,137,168)));
            c.round(x+size*.17,y+size*.17,size*.66,size*.66,size*.33,new Color(255,236,244,100));
            c.round(x+size*.27,y+size*.27,size*.46,size*.46,size*.23,new Color(229,147,174,120));
            c.round(x+size*.42,y+size*.42,size*.16,size*.16,size*.08,new Color(255,248,244,220));
        }
        c.g.setClip(previous);
    }
}
