package dev.lzasn.cloudxmusic.client.kugou;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KugouProfileTest {
    @Test void genericVipTypeSixDoesNotInventSuperVip() {
        var p=KugouProfile.parse(KugouLoginServiceTest.json("{\"data\":{\"nickname\":\"真实昵称\",\"pic\":\"https://imge.kugou.com/{size}/a.jpg\",\"vip_type\":6,\"is_vip\":1}}"));
        assertEquals("真实昵称",p.nickname());assertTrue(p.avatarUrl().contains("300"));assertEquals("VIP",p.badge());
        assertEquals("VIP",KugouProfile.parse(KugouLoginServiceTest.json("{\"data\":{\"vip_type\":1,\"is_vip\":1}}" )).badge());
    }
    @Test void onlyExplicitSuperVipFieldsShowSuperVip() {
        assertEquals("SVIP",KugouProfile.parse(KugouLoginServiceTest.json("{\"data\":{\"is_vip\":1,\"is_svip\":1}}" )).badge());
        assertEquals("SVIP",KugouProfile.parse(KugouLoginServiceTest.json("{\"data\":{\"is_vip\":1,\"busi_vip\":[{\"product_type\":\"svip\",\"is_vip\":1}]}}" )).badge());
    }
    @Test void expiredOrExplicitlyInactiveMembershipHasNoBadge() {
        assertEquals("",KugouProfile.parse(KugouLoginServiceTest.json("{\"vip_type\":6,\"is_vip\":0}")).badge());
        assertEquals("",KugouProfile.parse(KugouLoginServiceTest.json("{\"vip_type\":6,\"expire_time\":1}")).badge());
        assertEquals("",KugouProfile.empty().badge());
    }
}
