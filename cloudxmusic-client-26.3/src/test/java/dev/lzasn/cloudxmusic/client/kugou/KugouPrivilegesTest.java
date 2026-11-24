package dev.lzasn.cloudxmusic.client.kugou;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KugouPrivilegesTest {
    @org.junit.jupiter.api.Test void unknownPrivilegeIsNotVipAndBooleanRequirementsAreRespected(){
        var values=KugouPrivileges.parse(com.google.gson.JsonParser.parseString("{\"data\":[{\"relate_goods\":[{\"quality\":\"high\",\"need_svip\":true},{\"quality\":\"128\",\"privilege\":-1},{\"quality\":\"320\",\"need_vip\":true}]}]}").getAsJsonObject());
        org.junit.jupiter.api.Assertions.assertEquals("SVIP",values.badge(KugouQuality.HIGH));org.junit.jupiter.api.Assertions.assertEquals("VIP",values.badge(KugouQuality.HQ));org.junit.jupiter.api.Assertions.assertEquals("",values.badge(KugouQuality.STANDARD));
    }
    @Test void requirementsComeFromEachResourceAndNeverFromTheQualityName() {
        var value=KugouPrivileges.parse(JsonParser.parseString("""
                {"data":[{"relate_goods":[
                  {"quality":"high","privilege":0},
                  {"quality":"flac","privilege":10,"require_vip_type":"suvip"},
                  {"quality":"320","privilege":0},
                  {"quality":"128","privilege":10,"require_vip_type":"vip"}]}]}
                """).getAsJsonObject());
        assertEquals("",value.badge(KugouQuality.HIGH));
        assertEquals("SVIP",value.badge(KugouQuality.FLAC));
        assertEquals("",value.badge(KugouQuality.HQ));
        assertEquals("VIP",value.badge(KugouQuality.STANDARD));
        assertEquals("",KugouPrivileges.empty().badge(KugouQuality.HIGH));
    }
    @Test void genericPrivilegeCannotInventSuperVipAndExplicitFreeClearsOldBadge() {
        var first=KugouPrivileges.observation(KugouLoginServiceTest.json("{\"privilege\":10}"),KugouQuality.HIGH);
        assertEquals("VIP",first.badge(KugouQuality.HIGH));
        var next=first.merge(KugouPrivileges.observation(KugouLoginServiceTest.json("{\"privilege\":0}"),KugouQuality.HIGH));
        assertEquals("",next.badge(KugouQuality.HIGH));
    }
}
