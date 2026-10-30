package dev.lzasn.cloudxmusic.client.omni;

public enum MusicSource {
    NETEASE("netease","网易云"), QQ("qq","QQ音乐");
    private final String id,label;
    MusicSource(String id,String label){this.id=id;this.label=label;}
    public String id(){return id;}
    public String label(){return label;}
}
