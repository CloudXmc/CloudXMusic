package dev.lzasn.cloudxmusic.client.kugou;

/** 协议 v5 无酷狗枚举，界面据此禁用创建/加入，禁止映射为 QQ 或网易云。 */
public final class KugouCapabilities {
    private KugouCapabilities(){}
    public static boolean supportsRooms(){return false;}
}
