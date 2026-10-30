package dev.lzasn.cloudxmusic.client.qqmusic;

import java.io.*;
import javax.swing.text.*;
import javax.swing.text.html.*;
import javax.swing.text.html.parser.ParserDelegator;

/** QQ musicmac 的登录态“今日私享”入口。只接受明确标记的每日资源，绝不截取普通推荐冒充。 */
final class QQDailyDiscovery {
    private QQDailyDiscovery() {}
    static QQCatalogApi.Playlist parse(String html)throws IOException{
        if(html.length()>2*1024*1024)throw new IOException("QQ 每日推荐响应过大");
        class Parser extends HTMLEditorKit.ParserCallback {
            boolean inItem,daily;String id="",cover="";QQCatalogApi.Playlist result;
            String attr(MutableAttributeSet a,String key){var names=a.getAttributeNames();while(names.hasMoreElements()){Object name=names.nextElement();if(name.toString().equals(key))return String.valueOf(a.getAttribute(name));}return "";}
            @Override public void handleStartTag(HTML.Tag t,MutableAttributeSet a,int pos){
                if(t==HTML.Tag.LI&&attr(a,"class").contains("playlist__item")){inItem=true;daily=false;id="";cover="";}
                if(inItem&&t==HTML.Tag.A){String rid=attr(a,"data-rid");if(rid.matches("[1-9][0-9]{0,18}"))id=rid;}
            }
            @Override public void handleText(char[] data,int pos){if(inItem){String text=new String(data).replaceAll("\\s","");if(text.equals("今日私享")||text.equals("每日30首")||text.equals("每日推荐"))daily=true;}}
            @Override public void handleSimpleTag(HTML.Tag t,MutableAttributeSet a,int pos){if(inItem&&t==HTML.Tag.IMG)cover=QQAccountProfile.safeAvatar(attr(a,"src"));}
            @Override public void handleEndTag(HTML.Tag t,int pos){if(t==HTML.Tag.LI&&inItem){if(daily&&!id.isEmpty()&&result==null)result=new QQCatalogApi.Playlist(id,0,"","每日30首 · 今日私享",30,cover);inItem=false;}}
        }
        Parser parser=new Parser();new ParserDelegator().parse(new StringReader(html),parser,true);
        if(parser.result==null)throw new IOException("QQ 暂未提供今日私享资源，请确认登录后刷新每日推荐");return parser.result;
    }
}
