package dev.lzasn.cloudxmusic.client.kugou;

import java.util.*;

/** 保留真实音频分类及旧配置兼容；不代表客户端能够主动请求或切换所有档位。 */
public enum KugouQuality {
    HIGH("high","Hi-Res音质","豪华VIP"), FLAC("flac","无损音质","VIP"),
    HQ("320","高品音质","部分需会员"), STANDARD("128","标准音质","");
    private final String apiValue,menuLabel,entitlement;
    KugouQuality(String apiValue,String menuLabel,String entitlement){this.apiValue=apiValue;this.menuLabel=menuLabel;this.entitlement=entitlement;}
    public String apiValue(){return apiValue;}
    public String menuLabel(){return menuLabel;}
    public String entitlement(){return entitlement;}
    public boolean flac(){return this==HIGH||this==FLAC;}
    public List<KugouQuality> fallbacks(){return List.of(values()).subList(ordinal(),values().length);}
}
