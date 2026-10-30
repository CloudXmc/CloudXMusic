package dev.lzasn.cloudxmusic.client.gui.design;

/** 内容身份含歌曲和行时间戳；相同歌词在后一段再次出现，也重新从行首滚动。 */
public final class MarqueeClock {
    private String key;
    private long started;
    public double elapsed(String contentKey,long now){
        if(!contentKey.equals(key)){key=contentKey;started=now;}
        return Math.max(0,(now-started)/1_000_000_000.0);
    }
    public void reset(){key=null;started=0;}
}
