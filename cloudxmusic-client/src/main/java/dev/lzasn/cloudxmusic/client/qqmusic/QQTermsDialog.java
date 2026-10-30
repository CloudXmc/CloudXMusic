package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.gui.design.*;
import dev.lzasn.cloudxmusic.client.gui.design.MusicLayout.Box;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import static dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas.*;
import static dev.lzasn.cloudxmusic.client.gui.design.MusicLayout.*;

/** QQ 登录前的模态阅读会话；坐标沿用主画布，滚轮和点击不会穿透。 */
final class QQTermsDialog {
    static final Box TEXT=new Box(180,138,740,380),AGREE=new Box(718,565,202,42),REJECT=new Box(526,565,172,42);
    private static final int LINE_HEIGHT=24,CONTENT_HEIGHT=348;
    private static final String CONTENT="""
            一、非官方性质与使用范围
            云朵音乐盒（CloudXMusic）是独立开发的非官方第三方模组，与 QQ 音乐、腾讯及其关联公司不存在隶属、授权或背书关系。本模组通过非官方接口提供音乐功能，仅供学习、研究和交流，请勿将其用于商业用途或违反服务条款的活动。

            二、账号风控与使用风险
            使用第三方接口、非官方登录或扫码方式，可能触发平台的安全策略，包括但不限于异常登录提醒、二次验证、访问限流、账号风控、临时冻结或其他账号限制。请充分了解上述风险并自行决定是否继续；由此产生的账号使用风险及相关后果由用户自行承担。

            三、登录与凭证保存
            扫码前请核对登录页面及设备提示，不要向任何人提供验证码、登录 Cookie、Token 或本地 account.json。登录凭证保存在本机 config/cloudxmusic/qqmusic/ 目录，用于向 QQ 音乐验证身份；本模组不会将其提交到项目自建服务器。请妥善保护本机文件，分享日志或源码时不要附带凭证。

            四、会员权限与音乐版权
            歌曲版权、会员权限、数字专辑购买状态及音源可用性均由平台决定。本模组不授予额外的会员或版权许可，也不保证可以播放所有歌曲。请遵守适用法律、平台服务条款及权利人的要求，不得利用本模组绕过付费限制或传播受版权保护的作品。

            五、网络请求与服务变更
            搜索、扫码、推荐、歌词、封面及音频请求会直接发送至对应平台和 CDN，相关服务可能处理网络地址、登录身份及请求数据。非官方接口可能随时失效、变更、限流或停止服务；网络、设备和接口异常可能造成播放中断或数据不准确。

            六、责任边界与用户选择
            开发者会尽合理努力维护功能与安全，但不承诺第三方服务永久可用或完全无误，也不对平台处置、网络异常或用户不当操作提供保证。法律规定不得排除或限制的责任，不因本说明而免除。请勿使用重要账号进行不必要的测试。

            七、同意、拒绝与退出
            请完整阅读以上内容。只有滚动到本说明底部后，才能点击“同意并继续”开始 QQ 音乐登录；点击“拒绝并退出”或按 Esc 将取消本次登录，不会请求新的二维码，也不会删除你已有的网易云账号或 QQ 本地凭证。你可随时在 QQ 账号界面退出登录并清除凭证。

            阅读完毕。若你已理解并愿意承担上述使用风险，请选择“同意并继续”；如不同意，请选择“拒绝并退出”。
            """;
    private final QQTermsConsent consent;
    private final List<String> lines;
    private final double maxScroll;
    private double scroll;
    private boolean open;
    private long revision;
    private Runnable continuation=()->{};
    QQTermsDialog(QQTermsConsent consent){
        this.consent=consent;
        try(var canvas=new FrostCanvas(new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB),1)){lines=wrap(canvas,CONTENT,692);}
        maxScroll=Math.max(0,lines.size()*LINE_HEIGHT-CONTENT_HEIGHT);
    }
    static List<String> wrap(FrostCanvas c,String text,float width){
        var lines=new ArrayList<String>();
        for(String paragraph:text.split("\n",-1)){
            StringBuilder row=new StringBuilder();float used=0;
            for(int i=0;i<paragraph.length();){
                int cp=paragraph.codePointAt(i);String glyph=new String(Character.toChars(cp));float size=c.width(glyph,13,false);
                if(used+size>width&&!row.isEmpty()){lines.add(row.toString());row.setLength(0);used=0;}
                row.append(glyph);used+=size;i+=Character.charCount(cp);
            }
            lines.add(row.toString());
        }
        return List.copyOf(lines);
    }
    boolean isOpen(){return open;}
    long revision(){return revision;}
    double scrollOffset(){return scroll;}
    double maxScroll(){return maxScroll;}
    boolean canAgree(){var state=consent.view();return open&&state.loaded()&&!state.saving()&&scroll>=maxScroll-1;}
    void open(Runnable next){open=true;scroll=0;continuation=next;revision++;}
    void dismiss(){open=false;continuation=()->{};revision++;}
    void update(){if(open&&consent.view().agreed())proceed();}
    private void proceed(){if(!open)return;Runnable next=continuation;dismiss();next.run();}
    boolean scroll(double x,double y,double vertical){
        if(!open)return false;
        if(TEXT.contains(x,y)&&Double.isFinite(vertical)){double next=Math.clamp(scroll-vertical*36,0,maxScroll);if(next!=scroll){scroll=next;revision++;}}
        return true;
    }
    boolean click(double x,double y){
        if(!open)return false;
        if(REJECT.contains(x,y))dismiss();
        else if(AGREE.contains(x,y)&&canAgree()){
            long token=revision;consent.agree(()->{if(open&&revision==token)proceed();});
        }
        return true;
    }
    BufferedImage decorate(BufferedImage base,double scale){
        if(!open)return base;
        BufferedImage image=new BufferedImage(base.getWidth(),base.getHeight(),BufferedImage.TYPE_INT_ARGB);
        try(var c=new FrostCanvas(image,scale)){
            AffineTransform transform=c.g.getTransform();c.g.setTransform(new AffineTransform());c.g.drawImage(base,0,0,null);c.g.setTransform(transform);c.g.translate(PAD,PAD);
            c.round(0,0,WIDTH,HEIGHT,38,new Color(0,0,0,135));c.shadow(150,42,800,590,24,12,0,4,new Color(0,0,0,60));
            c.round(150,42,800,590,24,dark?new Color(38,46,51):new Color(255,248,252));
            c.text("用户使用协议与免责声明",180,90,21,true,INK,740);
            c.text("QQ 音乐登录前，请完整阅读并使用鼠标滚轮滑动至底部",180,118,12,false,MUTED,740);
            c.round(TEXT.x(),TEXT.y(),TEXT.w(),TEXT.h(),14,SOFT);
            Shape clip=c.g.getClip();c.g.clip(new Rectangle(TEXT.x()+16,TEXT.y()+16,692,CONTENT_HEIGHT));
            for(int i=0;i<lines.size();i++){
                double baseline=TEXT.y()+30+i*LINE_HEIGHT-scroll;
                if(baseline>=TEXT.y()+4&&baseline<TEXT.y()+TEXT.h())c.text(lines.get(i),TEXT.x()+16,(float)baseline,13,false,INK,692);
            }
            c.g.setClip(clip);
            double thumb=Math.max(30,CONTENT_HEIGHT*CONTENT_HEIGHT/(double)(lines.size()*LINE_HEIGHT));
            c.round(906,TEXT.y()+16,4,CONTENT_HEIGHT,2,TRACK);
            c.round(906,TEXT.y()+16+(maxScroll==0?0:scroll/maxScroll*(CONTENT_HEIGHT-thumb)),4,thumb,2,PINK);
            var state=consent.view();String message=!state.error().isEmpty()?state.error():state.saving()?"正在保存同意状态…":!state.loaded()?"正在读取 QQ 本地协议状态…":canAgree()?"已阅读至底部，可同意并继续":"请向下滚动，读完全文后方可同意";
            c.text(message,180,543,11,false,state.error().isBlank()?MUTED:QQSearchPainter.errorColor(),740);
            c.round(REJECT.x(),REJECT.y(),REJECT.w(),REJECT.h(),14,SOFT);c.center("拒绝并退出",REJECT.cx(),REJECT.cy()+4,12,true,DEEP,REJECT.w()-16);
            c.round(AGREE.x(),AGREE.y(),AGREE.w(),AGREE.h(),14,canAgree()?PINK:TRACK);
            c.center(state.saving()?"保存中…":"同意并继续",AGREE.cx(),AGREE.cy()+4,12,true,canAgree()?Color.WHITE:MUTED,AGREE.w()-16);
        }
        return image;
    }
}
