package dev.lzasn.cloudxmusic.client.gui.terms;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class UserAgreementTest {
    @Test void bundledDocumentCoversRealPlatformsCredentialsAndQualifiedLiability() throws Exception {
        String text=UserAgreement.text();
        for(String required:new String[]{"网易云音乐","QQ 音乐","酷狗音乐","非官方","Cookie","封禁","明文",
                "netease/cookies.json","qqmusic/account.json","kugou/account.json","refresh_token","CDN",
                "标准音质","音乐房间","GPL-3.0","商业使用","法律规定不得免除","拒绝并退出"})
            assertTrue(text.contains(required),required);
        assertFalse(text.contains("开发者在任何情况下均不承担责任"));
        assertFalse(text.contains("�"));
        try(var input=UserAgreement.class.getResourceAsStream(UserAgreement.RESOURCE)){
            assertNotNull(input);assertEquals(text,new String(input.readAllBytes(),StandardCharsets.UTF_8));
        }
    }
}
