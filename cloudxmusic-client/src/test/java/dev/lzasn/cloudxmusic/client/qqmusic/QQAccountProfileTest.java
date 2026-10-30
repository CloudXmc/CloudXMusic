package dev.lzasn.cloudxmusic.client.qqmusic;
import org.junit.jupiter.api.Test;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import static dev.lzasn.cloudxmusic.client.qqmusic.QQSessionVerifierTest.*;
import static org.junit.jupiter.api.Assertions.*;
class QQAccountProfileTest {
    @Test void profileRefreshGetsNestedUinWhenStoredAvatarIsMissing()throws Exception{
        var account=new QQCredentials("123456","test-key",2,"昵称",0);var calls=new java.util.ArrayList<String>();
        var profile=QQAccountProfileApi.load(account,()->true,(request,cookies,timeout)->{
            String method=request.getAsJsonObject("req_0").get("method").getAsString();calls.add(method);
            return method.equals("GetLoginUserInfo")?reply(0,"{\"info\":{\"base\":{\"uin\":12345678}}}"):reply(0,"{\"svip\":1,\"identity\":{\"level\":6,\"vip\":1}}");
        });
        assertEquals(java.util.List.of("GetLoginUserInfo","vip_login_base"),calls);
        assertEquals("https://q1.qlogo.cn/g?b=qq&nk=12345678&s=100",profile.avatarUrl());assertEquals("SVIP6",profile.badge());
    }
    @Test void missingAvatarUsesExplicitNestedQqUinAndAcceptsQ1Cdn(){
        var profile=QQAccountProfile.login(json("{\"info\":{\"base\":{\"uin\":12345678}}}"));
        assertEquals("https://q1.qlogo.cn/g?b=qq&nk=12345678&s=100",profile.avatarUrl());
        assertTrue(QQArtworkCache.allowed(java.net.URI.create(profile.avatarUrl())));
        assertEquals("",QQAccountProfile.login(json("{\"musicid\":12345678}")).avatarUrl());
        assertEquals("https://q1.qlogo.cn/real",QQAccountProfile.login(json("{\"info\":{\"headpic\":\"https://q1.qlogo.cn/real\",\"uin\":12345678}}")).avatarUrl());
    }
    @Test void avatarAliasesAreNormalizedAndUntrustedTargetsRejected(){
        var profile=QQAccountProfile.login(json("{\"info\":{\"base\":{\"headurl\":\"http://thirdqq.qlogo.cn/g?b=qq&k=public\"}}}"));
        assertTrue(profile.avatarUrl().startsWith("https://thirdqq.qlogo.cn/"));
        for(String bad:new String[]{"file:///secret","https://localhost/a","https://q.qlogo.cn.evil.test/a","https://u:p@q.qlogo.cn/a","https://q.qlogo.cn:444/a"})assertEquals("",QQAccountProfile.safeAvatar(bad));
    }
    @Test void badgesRequireActiveMembershipAndUseOnlyIdentityLevel(){
        var profile=QQAccountProfile.EMPTY;
        assertEquals("SVIP6",profile.withMembership(json("{\"svip\":1,\"identity\":{\"vip\":1,\"level\":6}}" )).badge());
        assertEquals("VIP4",profile.withMembership(json("{\"svip\":0,\"identity\":{\"HugeVip\":1,\"level\":4}}" )).badge());
        assertEquals("",profile.withMembership(json("{\"identity\":{\"vip\":0,\"level\":8},\"userinfo\":{\"music_level\":90}}" )).badge());
    }
    @Test void authenticatedReadOnlyVipRequestPreservesCredentialsAndAvatar()throws Exception{
        var account=new QQCredentials("123456","test-key",2,"昵称",0,new QQAccountProfile("https://q.qlogo.cn/a",QQAccountProfile.Membership.NONE,0));
        var profile=QQAccountProfileApi.load(account,()->true,(request,cookies,timeout)->{
            assertEquals("VipLogin.VipLoginInter",request.getAsJsonObject("req_0").get("module").getAsString());
            assertEquals("vip_login_base",request.getAsJsonObject("req_0").get("method").getAsString());
            assertEquals(3,cookies.getCookieStore().getCookies().size());
            return reply(0,"{\"svip\":1,\"identity\":{\"level\":6,\"vip\":1}}");
        });
        assertEquals("SVIP6",profile.badge());assertEquals(account.profile().avatarUrl(),profile.avatarUrl());assertEquals("test-key",account.musicKey());
    }
    @Test void sidebarRendersRealAvatarAndGoldGreenBadgesInBothThemes()throws Exception{
        Path out=Path.of("../outputs/qq-upgrade-2.12.0/previews");Files.createDirectories(out);
        BufferedImage avatar=new BufferedImage(40,40,BufferedImage.TYPE_INT_ARGB);var g=avatar.createGraphics();g.setColor(new Color(0x4387C1));g.fillRect(0,0,40,40);g.dispose();
        for(boolean dark:new boolean[]{false,true})for(var membership:QQAccountProfile.Membership.values()){
            dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas.setDark(dark);
            BufferedImage image=new BufferedImage(260,520,BufferedImage.TYPE_INT_ARGB);
            var account=new QQAccountManager.View(true,"小獭.","已登录",false,new QQAccountProfile("",membership,6));
            try(var c=new dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas(image,1)){QQLoginPainter.sidebar(c,account,false,avatar);}
            assertEquals(avatar.getRGB(20,20),image.getRGB(52,479));
            ImageIO.write(image,"png",out.resolve((dark?"dark":"light")+"-account-"+membership+".png").toFile());
        }
        dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas.setDark(false);
    }
}
